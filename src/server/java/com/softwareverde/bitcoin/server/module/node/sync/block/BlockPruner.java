package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class BlockPruner extends SleepyService {
    protected static final String LAST_PRUNED_BLOCK_HEIGHT_KEY = "last_pruned_block_height";

    public interface RequiredBlockChecker {
        Boolean isBlockRequired(Long blockHeight, Sha256Hash blockHash);
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockStore _blockStore;
    protected final RequiredBlockChecker _requiredBlockChecker;

    protected Long _lastPrunedBlockHeight = 0L;

    protected Long _getLastPrunedBlockHeight(final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT value FROM properties WHERE `key` = ?")
                .setParameter(LAST_PRUNED_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return row.getLong("value");
    }

    protected void _setLastPrunedBlockHeight(final Long blockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                .setParameter(LAST_PRUNED_BLOCK_HEIGHT_KEY)
                .setParameter(blockHeight)
        );
    }

    public BlockPruner(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockStore blockStore, final RequiredBlockChecker requiredBlockChecker) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockStore = blockStore;
        _requiredBlockChecker = requiredBlockChecker;

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _lastPrunedBlockHeight = _getLastPrunedBlockHeight(databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            // TODO: delete all blocks at the old height, regardless of blockchain segment.
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final Long utxoCommittedBlockHeight = unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final Long headBlockHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockId), 0L);
            final long maxPruneBlockHeight = Math.min(utxoCommittedBlockHeight, headBlockHeight);

            for (long blockHeight = _lastPrunedBlockHeight; blockHeight < maxPruneBlockHeight; ++blockHeight) {
                final BlockId prunedBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
                final Sha256Hash prunedBlockHash = blockHeaderDatabaseManager.getBlockHash(prunedBlockId);

                final Boolean blockIsStillRequired = _requiredBlockChecker.isBlockRequired(blockHeight, prunedBlockHash);
                if (blockIsStillRequired) { break; }

                _blockStore.removeBlock(prunedBlockHash, blockHeight);

                TransactionUtil.startTransaction(databaseConnection);
                databaseConnection.executeSql(
                    new Query("DELETE block_transactions, transactions FROM block_transactions INNER JOIN transactions ON transactions.id = block_transactions.transaction_id WHERE block_id = ?")
                        .setParameter(prunedBlockId)
                );
                _setLastPrunedBlockHeight(blockHeight, databaseManager);
                TransactionUtil.commitTransaction(databaseConnection);

                Logger.info("Pruned Block: " + prunedBlockHash);

                _lastPrunedBlockHeight = blockHeight;
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }
}

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
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UndoLogDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;

public class BlockPruner extends SleepyService {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockStore _blockStore;

    protected Long _lastPrunedBlockHeight = null;

    public BlockPruner(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockStore blockStore) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockStore = blockStore;

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            if (headBlockId != null) {
                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
                _lastPrunedBlockHeight = Math.max(0L, (headBlockHeight - UndoLogDatabaseManager.MAX_REORG_DEPTH));
            }
            else {
                _lastPrunedBlockHeight = 0L;
            }
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
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final Long utxoCommittedBlockHeight = unspentTransactionOutputDatabaseManager.getCommittedUnspentTransactionOutputBlockHeight();
            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            for (long blockHeight = _lastPrunedBlockHeight; blockHeight < utxoCommittedBlockHeight; ++blockHeight) {
                final BlockId prunedBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
                final Sha256Hash prunedBlockHash = blockHeaderDatabaseManager.getBlockHash(prunedBlockId);

                _blockStore.removeBlock(prunedBlockHash, blockHeight);

                TransactionUtil.startTransaction(databaseConnection);
                databaseConnection.executeSql(
                    new Query("DELETE block_transactions, transactions FROM block_transactions INNER JOIN transactions ON transactions.id = block_transactions.transaction_id WHERE block_id = ?")
                        .setParameter(prunedBlockId)
                );
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

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
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public class BlockPruner extends SleepyService {
    protected static final String PRUNED_BLOCK_HEIGHT_KEY = "pruned_block_height";

    public interface RequiredBlockChecker {
        Boolean isBlockRequired(Long blockHeight, Sha256Hash blockHash);
    }

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockStore _blockStore;
    protected final RequiredBlockChecker _requiredBlockChecker;
    protected final Boolean _shouldKeepTransactionHashes;

    protected Long _lastPrunedBlockHeight = null;

    protected Long _getLastPrunedBlockHeight(final DatabaseManager databaseManager) throws DatabaseException {
        final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
        return Util.coalesce(propertiesStore.get(PRUNED_BLOCK_HEIGHT_KEY));
    }

    protected void _setLastPrunedBlockHeight(final Long blockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        final PropertiesStore propertiesStore = databaseManager.getPropertiesStore();
        propertiesStore.set(PRUNED_BLOCK_HEIGHT_KEY, blockHeight);
    }

    public BlockPruner(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BlockStore blockStore, final Boolean shouldKeepTransactionHashes, final RequiredBlockChecker requiredBlockChecker) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockStore = blockStore;
        _requiredBlockChecker = requiredBlockChecker;
        _shouldKeepTransactionHashes = shouldKeepTransactionHashes;
    }

    public void setLastPrunedBlockHeight(final Long blockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        _setLastPrunedBlockHeight(blockHeight, databaseManager);
        _lastPrunedBlockHeight = blockHeight;
    }

    @Override
    protected void _onStart() {
        if (_lastPrunedBlockHeight == null) {
            try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                _lastPrunedBlockHeight = _getLastPrunedBlockHeight(databaseManager);
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }
        }
    }

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

            for (long blockHeight = Util.coalesce(_lastPrunedBlockHeight); blockHeight < maxPruneBlockHeight; ++blockHeight) {
                final BlockId prunedBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
                final Sha256Hash prunedBlockHash = blockHeaderDatabaseManager.getBlockHash(prunedBlockId);

                final Boolean blockIsStillRequired = _requiredBlockChecker.isBlockRequired(blockHeight, prunedBlockHash);
                if (blockIsStillRequired) { break; }

                _blockStore.removeBlock(prunedBlockHash, blockHeight);

                TransactionUtil.startTransaction(databaseConnection);
                // Drop rows from: indexed_transaction_outputs, indexed_transaction_inputs, validated_slp_transactions, double_spend_proofs
                databaseConnection.executeSql(
                    new Query("DELETE indexed_transaction_outputs FROM block_transactions INNER JOIN indexed_transaction_outputs ON indexed_transaction_outputs.id = block_transactions.transaction_id WHERE block_transactions.block_id = ?")
                        .setParameter(prunedBlockId)
                );
                databaseConnection.executeSql(
                    new Query("DELETE indexed_transaction_inputs FROM block_transactions INNER JOIN indexed_transaction_inputs ON indexed_transaction_inputs.id = block_transactions.transaction_id WHERE block_transactions.block_id = ?")
                        .setParameter(prunedBlockId)
                );
                databaseConnection.executeSql(
                    new Query("DELETE validated_slp_transactions FROM block_transactions INNER JOIN validated_slp_transactions ON validated_slp_transactions.id = block_transactions.transaction_id WHERE block_transactions.block_id = ?")
                        .setParameter(prunedBlockId)
                );
                databaseConnection.executeSql(
                    new Query("DELETE double_spend_proofs FROM block_transactions INNER JOIN double_spend_proofs ON double_spend_proofs.id = block_transactions.transaction_id WHERE block_transactions.block_id = ?")
                        .setParameter(prunedBlockId)
                );
                // Drop associated rows from: transactions, block_transactions
                if (_shouldKeepTransactionHashes) {
                    databaseConnection.executeSql(
                        new Query("DELETE block_transactions FROM block_transactions WHERE block_id = ?")
                            .setParameter(prunedBlockId)
                    );
                }
                else {
                    databaseConnection.executeSql(
                        new Query("DELETE block_transactions, transactions FROM block_transactions INNER JOIN transactions ON transactions.id = block_transactions.transaction_id WHERE block_transactions.block_id = ?")
                            .setParameter(prunedBlockId)
                    );
                }
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

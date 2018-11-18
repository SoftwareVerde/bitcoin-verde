package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.server.database.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class TransactionInventoryMessageHandler implements BitcoinNode.TransactionInventoryMessageCallback {
    public static final BitcoinNode.TransactionInventoryMessageCallback IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionInventoryMessageCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final Runnable _newInventoryCallback;

    public TransactionInventoryMessageHandler(final BitcoinNode bitcoinNode, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final Runnable newInventoryCallback) {
        _bitcoinNode = bitcoinNode;
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _newInventoryCallback = newInventoryCallback;
    }

    @Override
    public void onResult(final List<Sha256Hash> transactionHashes) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);

            final List<Sha256Hash> unseenTransactionHashes;
            {
                final ImmutableListBuilder<Sha256Hash> unseenTransactionHashesBuilder = new ImmutableListBuilder<Sha256Hash>(transactionHashes.getSize());
                for (final Sha256Hash transactionHash : transactionHashes) {
                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                    if (transactionId == null) {
                        unseenTransactionHashesBuilder.add(transactionHash);
                    }
                }
                unseenTransactionHashes = unseenTransactionHashesBuilder.build();
            }

            if (! unseenTransactionHashes.isEmpty()) {
                final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = new PendingTransactionDatabaseManager(databaseConnection);
                final List<PendingTransactionId> pendingTransactionIds = pendingTransactionDatabaseManager.storeTransactionHashes(unseenTransactionHashes);

                final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
                nodeDatabaseManager.updateTransactionInventory(_bitcoinNode, pendingTransactionIds);

                final Runnable newInventoryCallback = _newInventoryCallback;
                if (newInventoryCallback != null) {
                    newInventoryCallback.run();
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

//        _bitcoinNode.requestTransactions(unseenTransactionHashes, new BitcoinNode.DownloadTransactionCallback() {
//            @Override
//            public void onResult(final Transaction transaction) {
//                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
//                    final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseManagerCache, _networkTime, _medianBlockTime);
//                    transactionValidator.setLoggingEnabled(true);
//
//                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
//                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
//                    final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseManagerCache);
//
//                    final BlockId blockId = blockDatabaseManager.getHeadBlockId();
//                    final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
//                    final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
//
//                    final TransactionProcessor transactionProcessor = new TransactionProcessor() {
//                        @Override
//                        public void processTransaction(final Transaction transaction) throws DatabaseException {
//                            TransactionUtil.startTransaction(databaseConnection);
//                            final TransactionId transactionId = _insertTransactionOrNull(transaction, transactionDatabaseManager);
//                            final Boolean transactionIsValid = _validateTransaction(blockchainSegmentId, blockHeight, transactionId, transaction, transactionValidator);
//
//                            if (transactionIsValid) {
//                                transactionDatabaseManager.addTransactionToMemoryPool(transactionId);
//                                Logger.log("Added Transaction To Memory Pool: " + transaction.getHash());
//                                TransactionUtil.commitTransaction(databaseConnection);
//
//                                final Set<Transaction> possiblyValidTransactions = _orphanedTransactionsCache.onTransactionAdded(transaction);
//
//                                for (final Transaction possiblyValidTransaction : possiblyValidTransactions) {
//                                    final Boolean canBeInserted = transactionDatabaseManager.previousOutputsExist(transaction);
//                                    if (! canBeInserted) {
//                                        _orphanedTransactionsCache.add(transaction, databaseConnection);
//                                        continue;
//                                    }
//
//                                    this.processTransaction(possiblyValidTransaction);
//                                }
//                            }
//                            else {
//                                Logger.log("Received invalid Transaction from Node: " + _bitcoinNode.getConnectionString() + " " + transaction.getHash());
//                                TransactionUtil.rollbackTransaction(databaseConnection);
//                            }
//                        }
//                    };
//
//                    synchronized (MUTEX) {
//                        final Boolean canBeInserted = transactionDatabaseManager.previousOutputsExist(transaction);
//                        if (! canBeInserted) {
//                            _orphanedTransactionsCache.add(transaction, databaseConnection);
//                            return;
//                        }
//
//                        transactionProcessor.processTransaction(transaction);
//                    }
//
//                }
//                catch (final DatabaseException exception) {
//                    Logger.log(exception);
//                }
//            }
//        });
    }
}
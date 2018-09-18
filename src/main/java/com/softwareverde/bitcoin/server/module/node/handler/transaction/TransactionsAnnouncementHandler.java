package com.softwareverde.bitcoin.server.module.node.handler.transaction;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;

public class TransactionsAnnouncementHandler implements BitcoinNode.TransactionsAnnouncementCallback {
    public static final Object MUTEX = new Object();

    public static final BitcoinNode.TransactionsAnnouncementCallback IGNORE_NEW_TRANSACTIONS_HANDLER = new BitcoinNode.TransactionsAnnouncementCallback() {
        @Override
        public void onResult(final List<Sha256Hash> result) { }
    };

    protected final BitcoinNode _bitcoinNode;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    public TransactionsAnnouncementHandler(final BitcoinNode bitcoinNode, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _bitcoinNode = bitcoinNode;
        _databaseConnectionFactory = databaseConnectionFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
    }

    @Override
    public void onResult(final List<Sha256Hash> transactionHashes) {
        final MutableList<Sha256Hash> unseenTransactionHashes = new MutableList<Sha256Hash>(transactionHashes.getSize());
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);

            for (final Sha256Hash transactionHash : transactionHashes) {
                final TransactionId transactionId = transactionDatabaseManager.getTransactionIdFromHash(transactionHash);
                if (transactionId == null) {
                    unseenTransactionHashes.add(transactionHash);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return;
        }

        _bitcoinNode.requestTransactions(unseenTransactionHashes, new BitcoinNode.DownloadTransactionCallback() {
            @Override
            public void onResult(final Transaction transaction) {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _networkTime, _medianBlockTime);
                    transactionValidator.setLoggingEnabled(false);

                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                    final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection);

                    final BlockId blockId = blockDatabaseManager.getHeadBlockId();
                    final Long blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);
                    final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

                    // TODO: Ensure transactionOutput is unspent...
                    // TODO: Add transaction to memory, but not to the database, if the utxo it's spending is has not been seen yet...

                    synchronized (MUTEX) {
                        TransactionUtil.startTransaction(databaseConnection);
                        final Boolean transactionIsValid = transactionValidator.validateTransactionInputsAreUnlocked(blockChainSegmentId, blockHeight, transaction);
                        if (transactionIsValid) {
                            final TransactionId transactionId = transactionDatabaseManager.insertTransaction(transaction);
                            TransactionUtil.commitTransaction(databaseConnection);
                            // Logger.log("Stored Transaction: " + transactionHash + " with Id: " + transactionId);
                        }
                        else {
                            TransactionUtil.rollbackTransaction(databaseConnection);
                            // Logger.log("Invalid Transaction: " + transactionHash);
                        }
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        });
    }
}
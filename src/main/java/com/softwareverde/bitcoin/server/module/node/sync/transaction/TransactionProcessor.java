package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;

public class TransactionProcessor extends SleepyService {
    protected static final Long MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE = 5000L;

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseCache;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    protected final SystemTime _systemTime;
    protected Long _lastOrphanPurgeTime;

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = new PendingTransactionDatabaseManager(databaseConnection);

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            if ((now - _lastOrphanPurgeTime) > MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE) {
                final MilliTimer purgeOrphanedTransactionsTimer = new MilliTimer();
                purgeOrphanedTransactionsTimer.start();
                pendingTransactionDatabaseManager.purgeExpiredOrphanedTransactions();
                purgeOrphanedTransactionsTimer.stop();
                Logger.log("Purge Orphaned Transactions: " + purgeOrphanedTransactionsTimer.getMillisecondsElapsed() + "ms");
                _lastOrphanPurgeTime = _systemTime.getCurrentTimeInMilliSeconds();
            }

            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, _databaseCache);

            while (! thread.isInterrupted()) {
                final List<PendingTransactionId> pendingTransactionIds = pendingTransactionDatabaseManager.selectCandidatePendingTransactionIds();
                if (pendingTransactionIds.isEmpty()) { return false; }

                final HashMap<Sha256Hash, PendingTransactionId> pendingTransactionIdMap = new HashMap<Sha256Hash, PendingTransactionId>(pendingTransactionIds.getSize());
                final MutableList<Transaction> transactionsToStore = new MutableList<Transaction>(pendingTransactionIds.getSize());
                for (final PendingTransactionId pendingTransactionId : pendingTransactionIds) {
                    if (thread.isInterrupted()) { return false; }

                    final Transaction transaction = pendingTransactionDatabaseManager.getPendingTransaction(pendingTransactionId);
                    if (transaction == null) { continue; }

                    final Boolean transactionCanBeStored = transactionDatabaseManager.previousOutputsExist(transaction);
                    if (! transactionCanBeStored) {
                        pendingTransactionDatabaseManager.updateTransactionDependencies(transaction);
                        continue;
                    }

                    pendingTransactionIdMap.put(transaction.getHash(), pendingTransactionId);
                    transactionsToStore.add(transaction);
                }

                final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, _databaseCache, _networkTime, _medianBlockTime);
                transactionValidator.setLoggingEnabled(true);

                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseCache);

                final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                int invalidTransactionCount = 0;
                final MilliTimer storeTransactionsTimer = new MilliTimer();
                storeTransactionsTimer.start();
                for (final Transaction transaction : transactionsToStore) {
                    if (thread.isInterrupted()) { break; }

                    final Sha256Hash transactionHash = transaction.getHash();
                    final PendingTransactionId pendingTransactionId = pendingTransactionIdMap.get(transactionHash);
                    if (pendingTransactionId == null) { continue; }

                    TransactionUtil.startTransaction(databaseConnection);

                    final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
                    final Boolean transactionIsValid = transactionValidator.validateTransaction(blockchainSegmentId, blockHeight, transaction, true);

                    if (transactionIsValid) {
                        transactionDatabaseManager.addTransactionToMemoryPool(transactionId);
                        TransactionUtil.commitTransaction(databaseConnection);
                    }
                    else {
                        TransactionUtil.rollbackTransaction(databaseConnection);

                        invalidTransactionCount += 1;
                        Logger.log("Invalid MemoryPool Transaction: " + transactionHash);
                    }

                    pendingTransactionDatabaseManager.deletePendingTransaction(pendingTransactionId);
                }
                storeTransactionsTimer.stop();

                Logger.log("Committed " + (transactionsToStore.getSize() - invalidTransactionCount) + " transactions to the MemoryPool in " + storeTransactionsTimer.getMillisecondsElapsed() + "ms. (" + String.format("%.2f", (transactionsToStore.getSize() / storeTransactionsTimer.getMillisecondsElapsed().floatValue() * 1000F)) + "tps) (" + invalidTransactionCount + " invalid)");
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public TransactionProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseCache, final NetworkTime networkTime, final MedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseCache = databaseCache;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;

        _systemTime = new SystemTime();
        _lastOrphanPurgeTime = 0L;
    }
}

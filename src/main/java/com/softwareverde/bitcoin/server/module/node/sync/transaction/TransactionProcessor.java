package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.lazy.LazyUnconfirmedTransactionUtxoSet;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;

public class TransactionProcessor extends SleepyService {
    public interface Callback {
        void onNewTransactions(List<Transaction> transactions);
    }

    protected static final Long MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE = 5000L;

    protected final SystemTime _systemTime;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    protected Long _lastOrphanPurgeTime;
    protected Callback _newTransactionProcessedCallback;

    protected void _deletePendingTransaction(final FullNodeDatabaseManager databaseManager, final PendingTransactionId pendingTransactionId) {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();

        try {
            TransactionUtil.startTransaction(databaseConnection);
            pendingTransactionDatabaseManager.deletePendingTransaction(pendingTransactionId);
            TransactionUtil.commitTransaction(databaseConnection);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();

            final UnspentTransactionOutputContext unconfirmedTransactionUtxoSet = new LazyUnconfirmedTransactionUtxoSet(databaseManager);
            final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(_networkTime, _medianBlockTime, unconfirmedTransactionUtxoSet);
            final TransactionValidator transactionValidator = new TransactionValidatorCore(transactionValidatorContext);

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            if ((now - _lastOrphanPurgeTime) > MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE) {
                final MilliTimer purgeOrphanedTransactionsTimer = new MilliTimer();
                purgeOrphanedTransactionsTimer.start();
                pendingTransactionDatabaseManager.purgeExpiredOrphanedTransactions();
                purgeOrphanedTransactionsTimer.stop();
                Logger.info("Purge Orphaned Transactions: " + purgeOrphanedTransactionsTimer.getMillisecondsElapsed() + "ms");
                _lastOrphanPurgeTime = _systemTime.getCurrentTimeInMilliSeconds();
            }


            while (! thread.isInterrupted()) {
                final List<PendingTransactionId> pendingTransactionIds = pendingTransactionDatabaseManager.selectCandidatePendingTransactionIds();
                if (pendingTransactionIds.isEmpty()) { return false; }

                final HashMap<Sha256Hash, PendingTransactionId> pendingTransactionIdMap = new HashMap<Sha256Hash, PendingTransactionId>(pendingTransactionIds.getCount());
                final List<Transaction> transactionsToStore;
                {
                    final ImmutableListBuilder<Transaction> listBuilder = new ImmutableListBuilder<Transaction>(pendingTransactionIds.getCount());
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
                        listBuilder.add(transaction);
                    }
                    transactionsToStore = listBuilder.build();
                }

                transactionValidator.setLoggingEnabled(true);

                final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                final MutableList<Transaction> validTransactions = new MutableList<Transaction>(transactionsToStore.getCount());
                final MutableList<TransactionId> validTransactionIds = new MutableList<TransactionId>(transactionsToStore.getCount());

                int invalidTransactionCount = 0;
                final MilliTimer storeTransactionsTimer = new MilliTimer();
                storeTransactionsTimer.start();
                for (final Transaction transaction : transactionsToStore) {
                    if (thread.isInterrupted()) { break; }

                    final Sha256Hash transactionHash = transaction.getHash();
                    final PendingTransactionId pendingTransactionId = pendingTransactionIdMap.get(transactionHash);
                    if (pendingTransactionId == null) { continue; }

                    TransactionUtil.startTransaction(databaseConnection);

                    final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
                    final Boolean transactionIsValid = transactionValidator.validateTransaction(blockHeight, transaction, true);

                    if (! transactionIsValid) {
                        TransactionUtil.rollbackTransaction(databaseConnection);

                        _deletePendingTransaction(databaseManager, pendingTransactionId);

                        invalidTransactionCount += 1;
                        Logger.info("Invalid MemoryPool Transaction: " + transactionHash);
                        continue;
                    }

                    final boolean isUnconfirmedTransaction = (transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId) == null);
                    if (isUnconfirmedTransaction) {
                        transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
                    }
                    TransactionUtil.commitTransaction(databaseConnection);

                    _deletePendingTransaction(databaseManager, pendingTransactionId);

                    validTransactions.add(transaction);
                    validTransactionIds.add(transactionId);
                }
                storeTransactionsTimer.stop();

                transactionOutputDatabaseManager.queueTransactionsForProcessing(validTransactionIds);

                Logger.info("Committed " + (transactionsToStore.getCount() - invalidTransactionCount) + " transactions to the MemoryPool in " + storeTransactionsTimer.getMillisecondsElapsed() + "ms. (" + String.format("%.2f", (transactionsToStore.getCount() / storeTransactionsTimer.getMillisecondsElapsed().floatValue() * 1000F)) + "tps) (" + invalidTransactionCount + " invalid)");

                final Callback newTransactionProcessedCallback = _newTransactionProcessedCallback;
                if (newTransactionProcessedCallback != null) {
                    newTransactionProcessedCallback.onNewTransactions(validTransactions);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public TransactionProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory, final MedianBlockTime medianBlockTime, final NetworkTime networkTime) {
        _systemTime = new SystemTime();

        _databaseManagerFactory = databaseManagerFactory;
        _medianBlockTime = medianBlockTime;
        _networkTime = networkTime;

        _lastOrphanPurgeTime = 0L;
    }

    public void setNewTransactionProcessedCallback(final Callback newTransactionProcessedCallback) {
        _newTransactionProcessedCallback = newTransactionProcessedCallback;
    }
}

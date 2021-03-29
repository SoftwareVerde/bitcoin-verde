package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NetworkTimeContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.context.lazy.DoubleSpendProofUtxoSet;
import com.softwareverde.bitcoin.context.lazy.LazyMedianBlockTimeContext;
import com.softwareverde.bitcoin.context.lazy.LazyUnconfirmedTransactionUtxoSet;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofWithTransactions;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;

public class TransactionProcessor extends SleepyService {
    public interface Context extends TransactionInflaters, MultiConnectionFullDatabaseContext, TransactionValidatorFactory, NetworkTimeContext, SystemTimeContext, UpgradeScheduleContext, ThreadPoolContext { }

    public interface Callback {
        void onNewTransactions(List<Transaction> transactions);
    }

    public interface DoubleSpendProofCallback {
        void onNewDoubleSpendProof(DoubleSpendProofWithTransactions doubleSpendProof);
    }

    protected static final Long MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE = 5000L;

    protected final Context _context;

    protected Long _lastOrphanPurgeTime;
    protected Callback _newTransactionProcessedCallback;
    protected DoubleSpendProofCallback _doubleSpendProofCallback;

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
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        final VolatileNetworkTime networkTime = _context.getNetworkTime();
        final SystemTime systemTime = _context.getSystemTime();

        final Thread thread = Thread.currentThread();

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();

            final TransactionInflaters transactionInflaters = _context;
            final UnspentTransactionOutputContext unconfirmedTransactionUtxoSet = new LazyUnconfirmedTransactionUtxoSet(databaseManager, true);
            final MedianBlockTimeContext medianBlockTimeContext = new LazyMedianBlockTimeContext(databaseManager);
            final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
            final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(transactionInflaters, networkTime, medianBlockTimeContext, unconfirmedTransactionUtxoSet, upgradeSchedule);
            final TransactionValidator transactionValidator = _context.getUnconfirmedTransactionValidator(transactionValidatorContext);

            final Long now = systemTime.getCurrentTimeInMilliSeconds();
            if ((now - _lastOrphanPurgeTime) > MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE) {
                final MilliTimer purgeOrphanedTransactionsTimer = new MilliTimer();
                purgeOrphanedTransactionsTimer.start();
                pendingTransactionDatabaseManager.purgeExpiredOrphanedTransactions();
                purgeOrphanedTransactionsTimer.stop();
                Logger.info("Purge Orphaned Transactions: " + purgeOrphanedTransactionsTimer.getMillisecondsElapsed() + "ms");
                _lastOrphanPurgeTime = systemTime.getCurrentTimeInMilliSeconds();
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

                final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

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

                    // NOTE: The transaction cannot be stored before it is validated, otherwise the LazyUtxoSet will believe the output has already been spent (by itself).
                    final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction((headBlockHeight + 1L), transaction);

                    if (! transactionValidationResult.isValid) {
                        _deletePendingTransaction(databaseManager, pendingTransactionId);

                        invalidTransactionCount += 1;
                        Logger.info("Invalid MemoryPool Transaction: " + transactionHash);
                        Logger.info(transactionValidationResult.errorMessage);

                        final DoubleSpendProofCallback doubleSpendProofCallback = _doubleSpendProofCallback;
                        if (doubleSpendProofCallback != null) {
                            final TransactionOutputIdentifier transactionOutputIdentifierBeingDoubleSpent;
                            final TransactionId firstSeenTransactionId;
                            { // Check if the Transaction was invalid due to an attempted double-spend against an unconfirmed transaction...
                                TransactionId transactionId = null;
                                TransactionOutputIdentifier transactionOutputIdentifier = null;
                                for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                                    final UnconfirmedTransactionInputDatabaseManager unconfirmedTransactionInputDatabaseManager = databaseManager.getUnconfirmedTransactionInputDatabaseManager();

                                    transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                                    final UnconfirmedTransactionInputId transactionInputId = unconfirmedTransactionInputDatabaseManager.getUnconfirmedTransactionInputIdSpendingTransactionOutput(transactionOutputIdentifier);
                                    if (transactionInputId != null) {
                                        transactionId = unconfirmedTransactionInputDatabaseManager.getTransactionId(transactionInputId);
                                        break;
                                    }
                                }
                                firstSeenTransactionId = transactionId;
                                transactionOutputIdentifierBeingDoubleSpent = (transactionId != null ? transactionOutputIdentifier : null);
                            }

                            final boolean isAttemptedDoubleSpend = (firstSeenTransactionId != null);
                            if (isAttemptedDoubleSpend) {
                                final DoubleSpendProofUtxoSet modifiedUnconfirmedTransactionUtxoSet = new DoubleSpendProofUtxoSet(databaseManager, true);

                                boolean shouldAbort = false;
                                TransactionOutput transactionOutputBeingDoubleSpent = null;
                                final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
                                for (final TransactionInput transactionInput : transactionInputs) {
                                    // TODO: Obtain previousTransactionOutput without relying on non-pruned mode...
                                    final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);

                                    final Sha256Hash transactionHashBeingSpent = transactionOutputIdentifier.getTransactionHash();
                                    final Integer transactionOutputIndexBeingSpent = transactionOutputIdentifier.getOutputIndex();

                                    final TransactionId transactionIdBeingSpent = transactionDatabaseManager.getTransactionId(transactionHashBeingSpent);
                                    if (transactionIdBeingSpent == null) {
                                        shouldAbort = true;
                                        break;
                                    }

                                    final Transaction transactionBeingSpent = transactionDatabaseManager.getTransaction(transactionIdBeingSpent);
                                    if (transactionBeingSpent == null) {
                                        shouldAbort = true;
                                        break;
                                    }

                                    final List<TransactionOutput> transactionOutputs = transactionBeingSpent.getTransactionOutputs();
                                    final TransactionOutput transactionOutput = transactionOutputs.get(transactionOutputIndexBeingSpent);

                                    modifiedUnconfirmedTransactionUtxoSet.addTransactionOutput(transactionOutputIdentifier, transactionOutput);

                                    if (Util.areEqual(transactionOutputIdentifierBeingDoubleSpent, transactionOutputIdentifier)) {
                                        transactionOutputBeingDoubleSpent = transactionOutput;
                                    }
                                }

                                if ( (! shouldAbort) && (transactionOutputBeingDoubleSpent != null) ) {

                                    final TransactionValidatorContext modifiedTransactionValidatorContext = new TransactionValidatorContext(transactionInflaters, networkTime, medianBlockTimeContext, modifiedUnconfirmedTransactionUtxoSet, upgradeSchedule);
                                    final TransactionValidator modifiedTransactionValidator = _context.getUnconfirmedTransactionValidator(modifiedTransactionValidatorContext);

                                    // Ensure the DoubleSpend Transaction would have otherwise been valid had the UTXO not been spent already...
                                    final TransactionValidationResult doubleSpendTransactionValidationResult = modifiedTransactionValidator.validateTransaction((headBlockHeight + 1L), transaction);
                                    if (doubleSpendTransactionValidationResult.isValid) {
                                        final LockingScript lockingScript = transactionOutputBeingDoubleSpent.getLockingScript();

                                        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
                                        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

                                        final Transaction firstSeenTransaction = transactionDatabaseManager.getTransaction(firstSeenTransactionId);
                                        final DoubleSpendProofWithTransactions doubleSpendProof = DoubleSpendProof.createDoubleSpendProof(transactionOutputIdentifierBeingDoubleSpent, scriptType, firstSeenTransaction, transaction);
                                        if (doubleSpendProof != null) {
                                            Logger.debug("DSProof created: " + doubleSpendProof.getHash());

                                            final ThreadPool threadPool = _context.getThreadPool();
                                            threadPool.execute(new Runnable() {
                                                @Override
                                                public void run() {
                                                    doubleSpendProofCallback.onNewDoubleSpendProof(doubleSpendProof);
                                                }
                                            });
                                        }
                                        else {
                                            Logger.debug("Unable to create DSProof for Tx: " + transactionHash);
                                        }
                                    }
                                }
                            }
                        }

                        continue;
                    }

                    TransactionUtil.startTransaction(databaseConnection);
                    final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
                    final boolean isUnconfirmedTransaction = (transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId) == null); // TODO: This check is likely redundant...
                    if (isUnconfirmedTransaction) {
                        transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
                    }
                    TransactionUtil.commitTransaction(databaseConnection);

                    _deletePendingTransaction(databaseManager, pendingTransactionId);

                    validTransactions.add(transaction);
                    validTransactionIds.add(transactionId);
                }
                storeTransactionsTimer.stop();

                blockchainIndexerDatabaseManager.queueTransactionsForProcessing(validTransactionIds);

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

    public TransactionProcessor(final Context context) {
        _context = context;
        _lastOrphanPurgeTime = 0L;
    }

    public void setNewTransactionProcessedCallback(final Callback newTransactionProcessedCallback) {
        _newTransactionProcessedCallback = newTransactionProcessedCallback;
    }

    public void setNewDoubleSpendProofCallback(final DoubleSpendProofCallback doubleSpendProofCallback) {
        _doubleSpendProofCallback = doubleSpendProofCallback;
    }
}

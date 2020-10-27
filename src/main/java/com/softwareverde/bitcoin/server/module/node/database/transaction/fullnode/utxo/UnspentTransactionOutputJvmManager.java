package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.BlockingQueueBatchRunner;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.JvmSpentState;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UnspentTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoKey;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoValue;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.Util;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UnspentTransactionOutputJvmManager implements UnspentTransactionOutputDatabaseManager {
    protected static final Container<Long> UNCOMMITTED_UTXO_BLOCK_HEIGHT = new Container<Long>(null); // null indicates uninitialized; -1 represents an invalidated set, and must first be cleared (via _clearUncommittedUtxoSet) before any other operations are performed.
    protected static final String COMMITTED_UTXO_BLOCK_HEIGHT_KEY = "committed_utxo_block_height";

    public static final ReentrantReadWriteLock.ReadLock READ_MUTEX;
    public static final ReentrantReadWriteLock.WriteLock WRITE_MUTEX;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(false);

        READ_MUTEX = readWriteLock.readLock();
        WRITE_MUTEX = readWriteLock.writeLock();
    }

    protected static final long UNKNOWN_BLOCK_HEIGHT = -1L;
    protected static final TreeMap<UtxoKey, UtxoValue> UTXO_SET = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);

    protected final Long _maxUtxoCount;
    protected final MasterInflater _masterInflater;
    protected final FullNodeDatabaseManager _databaseManager;
    protected final Double _purgePercent;
    protected final BlockStore _blockStore;

    protected long _minBlockHeight = Long.MAX_VALUE;
    protected long _maxBlockHeight = 0L;

    protected void _clearUncommittedUtxoSet() {
        UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = 0L;

        UTXO_SET.clear();
    }

    protected static Long _getCommittedUnspentTransactionOutputBlockHeight(final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT value FROM properties WHERE `key` = ?")
                .setParameter(UnspentTransactionOutputJvmManager.COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("value"), 0L);
    }

    protected void _clearUncommittedUtxoSetAndRethrow(final Exception exception) throws DatabaseException {
        try {
            _clearUncommittedUtxoSet();
        }
        catch (final Exception suppressedException) {
            Logger.debug(suppressedException);
            exception.addSuppressed(suppressedException);
        }

        if (exception instanceof DatabaseException) {
            throw (DatabaseException) exception;
        }
        throw new DatabaseException(exception);
    }

    protected void _markTransactionOutputsAsSpent(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) {
        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : spentTransactionOutputIdentifiers) {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.remove(utxoKey);
            if (utxoValue != null) { // The UTXO existed within the cache.
                final JvmSpentState spentState = utxoValue.getSpentState();

                if (spentState.isFlushedToDisk() || spentState.isFlushMandatory()) { // If the UTXO was committed then it must later be written to disk.
                    final JvmSpentState newSpentState = new JvmSpentState();
                    newSpentState.setIsSpent(true);
                    newSpentState.setIsFlushedToDisk(false);
                    newSpentState.setIsFlushMandatory(true);

                    final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoValue.blockHeight);
                    queuedUpdates.put(utxoKey, newUtxoValue);
                }
                else { } // The UTXO was never written to disk, therefore it can be removed.
            }
            else { // The UTXO was (very) likely flushed to disk, and therefore must be removed from disk later.
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(true);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true);

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, UNKNOWN_BLOCK_HEIGHT);
                queuedUpdates.put(utxoKey, newUtxoValue);
            }
        }
        UTXO_SET.putAll(queuedUpdates);
    }

    protected void _insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) {
        final UtxoValue utxoValue;
        { // Share the same value reference for batched UTXOs when applicable to conserve memory.
            final JvmSpentState spentState = new JvmSpentState();
            spentState.setIsSpent(false);
            spentState.setIsFlushedToDisk(false);
            spentState.setIsFlushMandatory(false);
            utxoValue = new UtxoValue(spentState, blockHeight);
        }

        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : unspentTransactionOutputIdentifiers) {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue existingUtxoValue = UTXO_SET.get(utxoKey);

            UtxoValue customUtxoValue = null;
            if (existingUtxoValue != null) {
                final JvmSpentState existingSpentState = existingUtxoValue.getSpentState();
                if (existingSpentState.isFlushMandatory()) { // Preserve the mandatoryFlush flag in case of a reorg...
                    final JvmSpentState newJvmSpentState = new JvmSpentState();
                    newJvmSpentState.setIsSpent(false);
                    newJvmSpentState.setIsFlushedToDisk(false);
                    newJvmSpentState.setIsFlushMandatory(true);

                    customUtxoValue = new UtxoValue(newJvmSpentState, blockHeight);
                }
            }

            queuedUpdates.put(utxoKey, (customUtxoValue != null ? customUtxoValue : utxoValue));
        }
        UTXO_SET.putAll(queuedUpdates);

        if (blockHeight != UNKNOWN_BLOCK_HEIGHT) {
            _maxBlockHeight = Math.max(blockHeight, _maxBlockHeight);
            _minBlockHeight = Math.min(blockHeight, _minBlockHeight);
        }
    }

    protected void _undoCreationOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.remove(utxoKey); // Remove the UTXO from the set.
            final JvmSpentState spentState = utxoValue.getSpentState();

            if (spentState.isFlushedToDisk() || spentState.isFlushMandatory()) { // If the UTXO has already been committed to disk as unspent then it must be flushed as spent (aka removed) from disk.
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(true);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true);

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoValue.blockHeight);
                queuedUpdates.put(utxoKey, newUtxoValue);
            }
        }
        UTXO_SET.putAll(queuedUpdates);
    }

    protected void _undoSpendingOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.remove(utxoKey);
            if (utxoValue != null) { // Utxos are removed if they are new and unsynchronized to disk, therefore if the Utxo exists then it was synchronized to disk.
                final JvmSpentState spentState = utxoValue.getSpentState();
                if (spentState.isFlushedToDisk() || spentState.isFlushMandatory()) {
                    final JvmSpentState newSpentState = new JvmSpentState();
                    newSpentState.setIsSpent(false);
                    newSpentState.setIsFlushedToDisk(false);
                    newSpentState.setIsFlushMandatory(true);

                    final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoValue.blockHeight);
                    queuedUpdates.put(utxoKey, newUtxoValue);
                }
                else { } // The UTXO was freshly created and not synchronized, so removing alone is sufficient.
            }
            else {
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(false);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true); // It is unknown if the UTXO was flushed to disk.

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, UNKNOWN_BLOCK_HEIGHT);
                queuedUpdates.put(utxoKey, newUtxoValue);
            }
        }
        UTXO_SET.putAll(queuedUpdates);
    }

    protected void _commitUnspentTransactionOutputs() throws Exception {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final int maxKeepCount = (int) (_maxUtxoCount * (1.0D - _purgePercent));
        final int maxUtxoPerBatch = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final int maxItemCount = (maxUtxoPerBatch * 32); // The maximum number of items in queue at any point in time...

        final AtomicLong onDiskDeleteExecutionTime = new AtomicLong(0L);
        final AtomicInteger onDiskDeleteItemCount = new AtomicInteger(0);
        final AtomicInteger onDiskDeleteBatchCount = new AtomicInteger(0);
        final BlockingQueueBatchRunner<UtxoKey> onDiskUtxoDeleteBatchRunner = BlockingQueueBatchRunner.newSortedInstance(maxUtxoPerBatch, maxItemCount, UtxoKey.COMPARATOR, false, new BatchRunner.Batch<UtxoKey>() {
            @Override
            public void run(final List<UtxoKey> unspentTransactionOutputs) throws Exception {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                onDiskDeleteBatchCount.incrementAndGet();

                { // Commit the output as spent...
                    final Query query = new Query("UPDATE committed_unspent_transaction_outputs SET is_spent = 1 WHERE (transaction_hash, `index`) IN (?)");
                    query.setExpandedInClauseParameters(unspentTransactionOutputs, new ValueExtractor<UtxoKey>() {
                        @Override
                        public InClauseParameter extractValues(final UtxoKey value) {
                            onDiskDeleteItemCount.incrementAndGet();

                            final TypedParameter transactionHash = new TypedParameter(value.transactionHash);
                            final TypedParameter outputIndex = new TypedParameter(value.outputIndex);
                            return new InClauseParameter(transactionHash, outputIndex);
                        }
                    });

                    synchronized (databaseConnection) {
                        databaseConnection.executeSql(query);
                    }
                }

                nanoTimer.stop();
                onDiskDeleteExecutionTime.addAndGet(nanoTimer.getMillisecondsElapsed().longValue());
            }
        });

        final AtomicLong onDiskInsertExecutionTime = new AtomicLong(0L);
        final AtomicInteger onDiskInsertItemCount = new AtomicInteger(0);
        final AtomicInteger onDiskInsertBatchCount = new AtomicInteger(0);
        final BlockingQueueBatchRunner<UnspentTransactionOutput> onDiskUtxoInsertBatchRunner = BlockingQueueBatchRunner.newSortedInstance(maxUtxoPerBatch, maxItemCount, UnspentTransactionOutput.COMPARATOR, false, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                onDiskInsertBatchCount.incrementAndGet();

                // NOTE: updating is_spent to zero on a duplicate key is required in order to undo a block that has been committed to disk.
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO committed_unspent_transaction_outputs (transaction_hash, `index`, block_height) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE is_spent = 0");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    onDiskInsertItemCount.incrementAndGet();

                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getBlockHeight());
                }

                synchronized (databaseConnection) {
                    databaseConnection.executeSql(batchedInsertQuery);
                }

                nanoTimer.stop();
                onDiskInsertExecutionTime.addAndGet(nanoTimer.getMillisecondsElapsed().longValue());
            }
        });

        onDiskUtxoDeleteBatchRunner.start();
        onDiskUtxoInsertBatchRunner.start();

        final long oldMinBlockHeight = _minBlockHeight;
        final long oldMaxBlockHeight = _maxBlockHeight;
        final long oldItemCount = UTXO_SET.size();
        final long oldTotalBlockDistance = (oldMaxBlockHeight - oldMinBlockHeight);

        int remainingPurgeCount = Math.max(0, (UTXO_SET.size() - maxKeepCount));
        long maxBlockHeight = 0L;
        long minBlockHeight = Long.MAX_VALUE;

        final int flushedUnspentStateCode;
        {
            final JvmSpentState spentState = new JvmSpentState();
            spentState.setIsSpent(false);
            spentState.setIsFlushedToDisk(true);
            spentState.setIsFlushMandatory(false);
            flushedUnspentStateCode = spentState.intValue();
        }

        long totalTimeWaited = 0L;
        final MilliTimer iterationTimer = new MilliTimer();
        iterationTimer.start();
        // TODO: Initialize UtxoValues to the same object.
        int i = 0;
        final JvmSpentState transientSpentState = new JvmSpentState(); // Re-initialize the same instance instead of creating many objects.
        final Iterator<Map.Entry<UtxoKey, UtxoValue>> iterator = UTXO_SET.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<UtxoKey, UtxoValue> entry = iterator.next();
            final UtxoKey utxoKey = entry.getKey();
            final UtxoValue utxoValue = entry.getValue();

            transientSpentState.initialize(utxoValue.spentStateCode);
            // Flush the UTXO to disk if the item should be flushed...
            if ( (! transientSpentState.isFlushedToDisk()) || transientSpentState.isFlushMandatory()) {
                if (transientSpentState.isSpent()) {
                    // Delete the spent UTXO from disk.
                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();
                    onDiskUtxoDeleteBatchRunner.waitForQueueCapacity(maxItemCount);
                    nanoTimer.stop();
                    totalTimeWaited += nanoTimer.getMillisecondsElapsed();
                    onDiskUtxoDeleteBatchRunner.addItem(utxoKey);
                }
                else {
                    // Insert the unspent UTXO to disk.
                    final UnspentTransactionOutput unspentTransactionOutput = new UnspentTransactionOutput(utxoKey, utxoValue);
                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();
                    onDiskUtxoInsertBatchRunner.waitForQueueCapacity(maxItemCount);
                    nanoTimer.stop();
                    totalTimeWaited += nanoTimer.getMillisecondsElapsed();

                    onDiskUtxoInsertBatchRunner.addItem(unspentTransactionOutput);
                }
            }

            // Remove the UTXO from the cache if it is spent.
            if (transientSpentState.isSpent()) {
                iterator.remove();
                remainingPurgeCount -= 1;
            }
            else {
                // Mark the UTXO as flushed and clear the mandatory-flush flag.
                entry.setValue(new UtxoValue(flushedUnspentStateCode, utxoValue.blockHeight));

                boolean wasPurged = false;
                if (remainingPurgeCount > 0) {
                    // Progressively purge UTXOs as the iterator creeps towards the end, prioritizing purging older UTXOs.

                    // Examples:
                    //  #1
                    //      i=500, remainingPurgeCount=100, iterationsRemaining=500, maxKeepCount=500, oldItemCount=1000
                    //      purgeAggressiveness=(remainingPurgeCount/iterationsRemaining)=20%
                    //      oldMaxBlockHeight=10000, oldMinBlockHeight=6000, utxoValue.blockHeight=9000
                    //  #2
                    //      i=900, remainingPurgeCount=1, iterationsRemaining=100, maxKeepCount=500, oldItemCount=1000
                    //      purgeAggressiveness=(remainingPurgeCount/iterationsRemaining)=1%
                    //      oldMaxBlockHeight=10000, oldMinBlockHeight=6000, utxoValue.blockHeight=9000
                    //  #3
                    //      i=950, remainingPurgeCount=1, iterationsRemaining=50, maxKeepCount=500, oldItemCount=1000
                    //      purgeAggressiveness=(remainingPurgeCount/iterationsRemaining)=2%
                    //      oldMaxBlockHeight=10000, oldMinBlockHeight=6000, utxoValue.blockHeight=6500
                    final long iterationsRemaining = (oldItemCount - i - 1L); // 500 // 100 // 50
                    final double purgeAggressiveness = Math.min(1D, (( (double) remainingPurgeCount ) / iterationsRemaining)); // 0=purgeNothing, 1=purgeEverything // 0.2D // 0.01D // 0.2D
                    final long purgeDistanceThreshold = (oldMaxBlockHeight - (long) ((oldTotalBlockDistance * purgeAggressiveness) + 0.5D)); // Round up. // 9200 // 9960 // 9920
                    if (utxoValue.blockHeight < purgeDistanceThreshold) { // 9000 < 9200 // 9000 < 9960 // 6500 < 9920
                        iterator.remove();
                        remainingPurgeCount -= 1;
                        wasPurged = true;
                    }
                }

                if ( (! wasPurged) && (utxoValue.blockHeight != UNKNOWN_BLOCK_HEIGHT) ) {
                    maxBlockHeight = Math.max(utxoValue.blockHeight, maxBlockHeight);
                    minBlockHeight = Math.min(utxoValue.blockHeight, minBlockHeight);
                }
            }

            i += 1;
        }
        iterationTimer.stop();

        Logger.trace(
            "iterationTimer=" + iterationTimer.getMillisecondsElapsed() +
            ", onDiskDeleteExecutionTime=" + onDiskDeleteExecutionTime +
            ", onDiskDeleteItemCount=" + onDiskDeleteItemCount +
            ", onDiskDeleteBatchCount=" + onDiskDeleteBatchCount +
            ", onDiskInsertExecutionTime=" + onDiskInsertExecutionTime +
            ", onDiskInsertItemCount=" + onDiskInsertItemCount +
            ", onDiskInsertBatchCount=" + onDiskInsertBatchCount +
            ", totalTimeWaited=" + totalTimeWaited
        );

        onDiskUtxoDeleteBatchRunner.finish();
        onDiskUtxoInsertBatchRunner.finish();

        onDiskUtxoDeleteBatchRunner.waitUntilFinished();
        onDiskUtxoInsertBatchRunner.waitUntilFinished();

        _minBlockHeight = minBlockHeight;
        _maxBlockHeight = maxBlockHeight;
    }

    public UnspentTransactionOutputJvmManager(final Long maxUtxoCount, final Float purgePercent, final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final MasterInflater masterInflater) {
        _maxUtxoCount = maxUtxoCount;
        _masterInflater = masterInflater;
        _databaseManager = databaseManager;
        _purgePercent = purgePercent.doubleValue();
        _blockStore = blockStore;
    }

    @Override
    public void markTransactionOutputsAsSpent(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (spentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _markTransactionOutputsAsSpent(spentTransactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (unspentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _insertUnspentTransactionOutputs(unspentTransactionOutputIdentifiers, blockHeight);
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void undoCreationOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _undoCreationOfTransactionOutputs(transactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void undoSpendingOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _undoSpendingOfTransactionOutputs(transactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        UTXO_READ_MUTEX.lock();
        try {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.get(utxoKey);
            if (utxoValue != null) {
                final JvmSpentState spentState = utxoValue.getSpentState();
                if (spentState.isSpent()) { return null; }
            }
            else { // Possible cache miss; check the committed set for the UTXO.
                final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT is_spent FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ? LIMIT 1")
                        .setParameter(transactionHash)
                        .setParameter(outputIndex)
                );

                if (rows.isEmpty()) { return null; }

                final Row row = rows.get(0);
                final Integer isSpent = row.getInteger("is_spent");
                if (isSpent > 0) { return null; }
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }

        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        if (outputIndex >= transactionOutputs.getCount()) { return null; }

        return transactionOutputs.get(outputIndex);
    }

    @Override
    public List<TransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return new MutableList<TransactionOutput>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final int transactionOutputIdentifierCount = transactionOutputIdentifiers.getCount();

        final MutableList<TransactionOutputIdentifier> cacheMissIdentifiers = new MutableList<TransactionOutputIdentifier>(transactionOutputIdentifierCount);
        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);

        UTXO_READ_MUTEX.lock();
        try {
            { // Only return outputs that are in the UTXO set...
                for (TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                    final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
                    final UtxoValue utxoValue = UTXO_SET.get(utxoKey);

                    if (utxoValue != null) {
                        final JvmSpentState spentState = utxoValue.getSpentState();
                        if (! spentState.isSpent()) {
                            unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                        }
                    }
                    else {
                        cacheMissIdentifiers.add(transactionOutputIdentifier);
                    }
                }
            }
            { // Load UTXOs that weren't in the memory-cache but are in the greater UTXO set on disk...
                final int cacheMissCount = cacheMissIdentifiers.getCount();
                if (cacheMissCount > 0) {
                    final Integer batchSize = Math.min(512, _databaseManager.getMaxQueryBatchSize());
                    final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(batchSize, false);

                    final java.util.List<Row> rows = new ArrayList<Row>(0);
                    batchRunner.run(cacheMissIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                        @Override
                        public void run(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws Exception {
                            rows.addAll(databaseConnection.query(
                                new Query("SELECT transaction_hash, `index` FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?) AND is_spent = 0")
                                    .setExpandedInClauseParameters(transactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
                            ));
                        }
                    });
                    for (final Row row : rows) {
                        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                        final Integer outputIndex = row.getInteger("index");

                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                        unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                    }
                }
            }
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }

        // TODO: remove non-deterministic group-by clause.
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blocks.hash AS block_hash, blocks.block_height, block_transactions.disk_offset, transactions.byte_count FROM transactions INNER JOIN block_transactions ON transactions.id = block_transactions.transaction_id INNER JOIN blocks ON blocks.id = block_transactions.block_id WHERE transactions.hash IN (?) GROUP BY transactions.hash")
                .setInClauseParameters(unspentTransactionOutputIdentifiers, new ValueExtractor<TransactionOutputIdentifier>() {
                        @Override
                        public InClauseParameter extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
                            return ValueExtractor.SHA256_HASH.extractValues(transactionOutputIdentifier.getTransactionHash());
                        }
                    }
                )
        );

        final HashMap<Sha256Hash, Transaction> transactions = new HashMap<Sha256Hash, Transaction>(rows.size());
        for (final Row row : rows) {
            final Sha256Hash blockHash = Sha256Hash.copyOf(row.getBytes("block_hash"));
            final Long blockHeight = row.getLong("block_height");
            final Long diskOffset = row.getLong("disk_offset");
            final Integer byteCount = row.getInteger("byte_count");

            final ByteArray transactionData = _blockStore.readFromBlock(blockHash, blockHeight, diskOffset, byteCount);
            if (transactionData == null) { return null; }

            final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
            final Transaction transaction = ConstUtil.asConstOrNull(transactionInflater.fromBytes(transactionData)); // To ensure Transaction::getHash is constant-time...
            if (transaction == null) { return null; }

            transactions.put(transaction.getHash(), transaction);
        }

        final ImmutableListBuilder<TransactionOutput> transactionOutputsBuilder = new ImmutableListBuilder<TransactionOutput>(transactionOutputIdentifierCount);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            if (! unspentTransactionOutputIdentifiers.contains(transactionOutputIdentifier)) {
                transactionOutputsBuilder.add(null);
                continue;
            }

            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

            final Transaction transaction = transactions.get(transactionOutputIdentifier.getTransactionHash());
            if (transaction == null) {
                transactionOutputsBuilder.add(null);
                continue;
            }

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            if (outputIndex >= transactionOutputs.getCount()) { return null; }

            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            transactionOutputsBuilder.add(transactionOutput);
        }

        return transactionOutputsBuilder.build();
    }

    @Override
    public void commitUnspentTransactionOutputs() throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, -1L) < 0L) {
            // Prevent committing a UTXO set that has been invalidated...
            return;
        }

        UTXO_WRITE_MUTEX.lock();
        try {
            _commitUnspentTransactionOutputs();

            { // Save the committed set's block height...
                final Long newCommittedBlockHeight = UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value;
                final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                databaseConnection.executeSql(
                    new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                        .setParameter(UnspentTransactionOutputJvmManager.COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
                        .setParameter(newCommittedBlockHeight)
                );
            }
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException {
        return this.getUncommittedUnspentTransactionOutputCount(false);
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputCount(final Boolean noLock) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        if (! noLock) { UTXO_READ_MUTEX.lock(); }
        try {
            return (long) UTXO_SET.size();
        }
        finally {
            if (! noLock) { UTXO_READ_MUTEX.unlock(); }
        }
    }

    @Override
    public Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        return this.getCommittedUnspentTransactionOutputBlockHeight(false);
    }

    @Override
    public Long getCommittedUnspentTransactionOutputBlockHeight(final Boolean noLock) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        if (! noLock) { UTXO_READ_MUTEX.lock(); }
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            return _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);
        }
        finally {
            if (! noLock) { UTXO_READ_MUTEX.unlock(); }
        }
    }

    @Override
    public void setUncommittedUnspentTransactionOutputBlockHeight(final Long blockHeight) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        UTXO_WRITE_MUTEX.lock();
        try {
            UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = blockHeight;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public Long getUnspentTransactionOutputBlockHeight() throws DatabaseException {
        return this.getUnspentTransactionOutputBlockHeight(false);
    }

    @Override
    public Long getUnspentTransactionOutputBlockHeight(final Boolean noLock) throws DatabaseException {
        if (Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value) < 0L) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        if (! noLock) { UTXO_READ_MUTEX.lock(); }
        try {

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            final Long committedUtxoBlockHeight = _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);
            return Math.max(Util.coalesce(UnspentTransactionOutputJvmManager.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value), committedUtxoBlockHeight);
        }
        finally {
            if (! noLock) { UTXO_READ_MUTEX.unlock(); }
        }
    }

    @Override
    public void clearCommittedUtxoSet() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            databaseConnection.executeSql(
                new Query("DELETE FROM committed_unspent_transaction_outputs")
            );
            databaseConnection.executeSql(
                new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                    .setParameter(UnspentTransactionOutputJvmManager.COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
                    .setParameter(0L)
            );
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void clearUncommittedUtxoSet() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            _clearUncommittedUtxoSet();
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public Long getMaxUtxoCount() {
        return _maxUtxoCount;
    }
}

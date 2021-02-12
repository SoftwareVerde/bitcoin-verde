package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.JvmSpentState;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UnspentTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoKey;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoValue;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.util.ArrayList;
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

    protected static Long getUtxoBlockHeight() {
        return Util.coalesce(UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, 0L);
    }

    protected static Boolean isUtxoCacheDefunct() {
        return (Util.coalesce(UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, 0L) <= -1L);
    }

    protected static Boolean isUtxoCacheUninitialized() {
        return (UNCOMMITTED_UTXO_BLOCK_HEIGHT.value == null);
    }

    protected static Boolean isUtxoCacheReady() {
        final Long uncommittedUtxoBlockHeight = UNCOMMITTED_UTXO_BLOCK_HEIGHT.value;
        return ( (uncommittedUtxoBlockHeight != null) && (uncommittedUtxoBlockHeight >= 0) );
    }

    public static final ReentrantReadWriteLock.ReadLock READ_MUTEX;
    public static final ReentrantReadWriteLock.WriteLock WRITE_MUTEX;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(false);

        READ_MUTEX = readWriteLock.readLock();
        WRITE_MUTEX = readWriteLock.writeLock();
    }

    /*
            Double buffering:
                The UTXO buffer is cut in half(-ish)* and the second half is the double-buffer.
                (* Might not need to be cut in half since items from the first buffer are moved into the second, not copied.)
                When a commit is required, the entire** contents of the first buffer is copied to the double buffer,
                the first buffer is then purged of all required writes and of old-ish outputs (i.e. the regular purge process).
                While committing the double buffer, the UTXOs are not removed from the buffer until the commit is complete,
                this is necessary because the first buffer needs to be able to check the double buffer for a state change since any
                read would be considered dirty from the on-disk table due to the sql-transaction isolation level.
                (** The entire contents don't need to be copied--only the items requiring flushing.)
                If a commit of the first buffer is required and the double buffer commit hasn't finished, then the first-buffer
                commitment blocks until the double-buffer commit is complete.
            When checking for a UTXO state:
                Check primary cache
                Check secondary cache
                Check database (even during a commit this will be the expected pre-commit state)

            Double-buffer UTXO Cache:
                Add all UTXO state changes to a secondary UTXO cache contained within the main one
                Once we reach a commit point, start a thread to commit the secondary UTXO cache and add any new changes to the primary UTXO set
                Purge primary UTXO cache to clear memory (knowing all changing needing written have already been copied to the secondary cache)
                When we reach another commit point: 1) Make sure we are done with the last commit, 2) move any new changes from the primary UTXO set to the secondary one, 3) re-start the commit thread
            Commit thread:
                Iterate through all the UTXOs and commit them in a transaction.
                Hang onto them until committed so that they can still be found in this cache, even while the commit thread is running
            When checking for a UTXO state:
                Check primary cache
                Check secondary cache
                Check database (even during a commit this will be the expected pre-commit state)
         */


    protected static final long UNKNOWN_BLOCK_HEIGHT = -1L;
    protected static final TreeMap<UtxoKey, UtxoValue> UTXO_SET = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
    protected static final TreeMap<UtxoKey, UtxoValue> DOUBLE_BUFFER = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
    protected static Thread DOUBLE_BUFFER_THREAD = null;

    protected final Long _maxUtxoCount;
    protected final MasterInflater _masterInflater;
    protected final FullNodeDatabaseManager _databaseManager;
    protected final Double _purgePercent;
    protected final BlockStore _blockStore;

    protected long _minBlockHeight = Long.MAX_VALUE;
    protected long _maxBlockHeight = 0L;

    protected void _clearUncommittedUtxoSet() {
        UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = null;
        UTXO_SET.clear();
    }

    protected void _invalidateUncommittedUtxoSet() {
        UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;
        UTXO_SET.clear();
    }

    protected void _invalidateUncommittedUtxoSetAndRethrow(final Exception exception) throws DatabaseException {
        try {
            _invalidateUncommittedUtxoSet();
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

    protected static Long _getCommittedUnspentTransactionOutputBlockHeight(final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT value FROM properties WHERE `key` = ?")
                .setParameter(COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("value"), 0L);
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

            // The utxoValue could be null either due to the UTXO being a cache miss (i.e. it was already committed before the reorg) or because it never existed at all.
            //  In the latter case, the double-buffer will be technically inserting the non-existing UTXO into the database as spent; this is generally not a problem other than causing an unnecessary disk write.
            //  This could be mitigated by only doing an update to spent if the row exists, but the performance hit doesn't warrant the extra ~48 bytes.
            final JvmSpentState spentState = (utxoValue != null ? utxoValue.getSpentState() : null);

            if ( (spentState == null) || (spentState.isFlushedToDisk() || spentState.isFlushMandatory()) ) { // If the UTXO has already been committed to disk as unspent then it must be flushed as spent (aka removed) from disk.
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(true);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true);

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, (utxoValue != null ? utxoValue.blockHeight : UNKNOWN_BLOCK_HEIGHT));
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

    protected static void commitDoubleBufferedUnspentTransactionOutputs(final Long newCommittedBlockHeight, final DatabaseManager databaseManager) throws Exception {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final int maxUtxoPerBatch = Math.min(1024, databaseManager.getMaxQueryBatchSize());

        final AtomicLong onDiskDeleteExecutionTime = new AtomicLong(0L);
        final AtomicInteger onDiskDeleteItemCount = new AtomicInteger(0);
        final AtomicInteger onDiskDeleteBatchCount = new AtomicInteger(0);
        final BatchRunner.Batch<UtxoKey> onDiskUtxoDeleteBatch = new BatchRunner.Batch<UtxoKey>() {
            @Override
            public void run(final List<UtxoKey> unspentTransactionOutputs) throws Exception {
                onDiskDeleteBatchCount.incrementAndGet();

                final Query query = new Query("UPDATE committed_unspent_transaction_outputs SET is_spent = 1 WHERE (transaction_hash, `index`) IN (?)");
                query.setInClauseParameters(unspentTransactionOutputs, new ValueExtractor<UtxoKey>() {
                    @Override
                    public InClauseParameter extractValues(final UtxoKey value) {
                        onDiskDeleteItemCount.incrementAndGet();

                        final TypedParameter transactionHash = new TypedParameter(value.transactionHash);
                        final TypedParameter outputIndex = new TypedParameter(value.outputIndex);
                        return new InClauseParameter(transactionHash, outputIndex);
                    }
                });

                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                databaseConnection.executeSql(query);
                nanoTimer.stop();
                onDiskDeleteExecutionTime.addAndGet(nanoTimer.getMillisecondsElapsed().longValue());
            }
        };

        final AtomicLong onDiskInsertExecutionTime = new AtomicLong(0L);
        final AtomicInteger onDiskInsertItemCount = new AtomicInteger(0);
        final AtomicInteger onDiskInsertBatchCount = new AtomicInteger(0);
        final BatchRunner.Batch<UnspentTransactionOutput> onDiskUtxoInsertBatch = new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                onDiskInsertBatchCount.incrementAndGet();

                // NOTE: block_height is currently unused, however the field could become useful during re-loading UTXOs
                //  into the cache based on recency, to facilitate UTXO commitments, and to facilitate more intelligent reorgs.

                // NOTE: updating is_spent to zero on a duplicate key is required in order to undo a block that has been committed to disk.
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO committed_unspent_transaction_outputs (transaction_hash, `index`, block_height) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE is_spent = 0");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    onDiskInsertItemCount.incrementAndGet();

                    final long blockHeight = transactionOutputIdentifier.getBlockHeight();
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                    batchedInsertQuery.setParameter(Math.max(blockHeight, 0L)); // block_height is an UNSIGNED INT; in the case of a reorg UTXO, the UTXO height can be set to -1, so 0 is used as a compatible placeholder.
                }
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                databaseConnection.executeSql(batchedInsertQuery);
                nanoTimer.stop();
                onDiskInsertExecutionTime.addAndGet(nanoTimer.getMillisecondsElapsed().longValue());
            }
        };

        long totalTimeWaited = 0L;
        final MilliTimer iterationTimer = new MilliTimer();
        iterationTimer.start();

        final int startSize = DOUBLE_BUFFER.size();
        int i = 0;
        final JvmSpentState transientSpentState = new JvmSpentState(); // Re-initialize the same instance instead of creating many objects.
        final Iterator<Map.Entry<UtxoKey, UtxoValue>> iterator = DOUBLE_BUFFER.entrySet().iterator();

        final MutableList<UtxoKey> nextDeleteBatch = new MutableList<UtxoKey>(maxUtxoPerBatch);
        final MutableList<UnspentTransactionOutput> nextInsertBatch = new MutableList<UnspentTransactionOutput>(maxUtxoPerBatch);

        while (iterator.hasNext()) {
            final Map.Entry<UtxoKey, UtxoValue> entry = iterator.next();
            final UtxoKey utxoKey = entry.getKey();
            final UtxoValue utxoValue = entry.getValue();

            transientSpentState.initialize(utxoValue.spentStateCode);

            // All items in the double-buffer are scheduled for flushing, either delete or insert...
            if (transientSpentState.isSpent()) {
                nextDeleteBatch.add(utxoKey);
            }
            else {
                // Insert the unspent UTXO to disk.
                final UnspentTransactionOutput unspentTransactionOutput = new UnspentTransactionOutput(utxoKey, utxoValue);
                nextInsertBatch.add(unspentTransactionOutput);
            }

            if (nextDeleteBatch.getCount() >= maxUtxoPerBatch) {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                onDiskUtxoDeleteBatch.run(nextDeleteBatch);
                nextDeleteBatch.clear();

                nanoTimer.stop();
                totalTimeWaited += nanoTimer.getMillisecondsElapsed();
            }

            if (nextInsertBatch.getCount() >= maxUtxoPerBatch) {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                onDiskUtxoInsertBatch.run(nextInsertBatch);
                nextInsertBatch.clear();

                nanoTimer.stop();
                totalTimeWaited += nanoTimer.getMillisecondsElapsed();
            }

            i += 1;

            if (startSize >= 10) {
                if ((i % (startSize / 10)) == 0) {
                    Logger.trace("Flushing thread " + ((10 * i) / (startSize / 10)) + "% done. " + (startSize - i) + " remaining.");
                }
            }
        }

        if (! nextDeleteBatch.isEmpty()) {
            onDiskUtxoDeleteBatch.run(nextDeleteBatch);
            nextDeleteBatch.clear();
        }

        if (! nextInsertBatch.isEmpty()) {
            onDiskUtxoInsertBatch.run(nextInsertBatch);
            nextInsertBatch.clear();
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

        { // Save the committed set's block height...
            databaseConnection.executeSql(
                new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                    .setParameter(COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
                    .setParameter(newCommittedBlockHeight)
            );
        }

        synchronized (DOUBLE_BUFFER) {
            DOUBLE_BUFFER.clear();
        }

        // This request for garbage collection is not strictly necessary but considering the drastic change in memory
        // usage, it is useful to ensure a collection after the double-buffer is cleared, particularly in cases when
        // periodic calls to gc aren't schedule (i.e. initial node boot).
        System.gc();
    }

    protected Boolean _commitUnspentTransactionOutputs(final DatabaseManagerFactory databaseManagerFactory, final CommitAsyncMode commitAsyncMode) throws DatabaseException {
        if (! UnspentTransactionOutputJvmManager.isUtxoCacheReady()) {
            // Prevent committing a UTXO set that has been invalidated or empty...
            Logger.warn("Not committing UTXO set due to invalidated or empty cache.");
            return false;
        }

        if (commitAsyncMode == CommitAsyncMode.SKIP_IF_BUSY) {
            final boolean lockAcquired = UTXO_WRITE_MUTEX.tryLock();
            if (! lockAcquired) { return false; }
        }
        else {
            UTXO_WRITE_MUTEX.lock();
        }

        try {
            synchronized (DOUBLE_BUFFER) {
                while (DOUBLE_BUFFER_THREAD != null) { // Protect against spontaneous wake-ups..
                    if (commitAsyncMode == CommitAsyncMode.SKIP_IF_BUSY) { return false; } // NOTE: UTXO_WRITE_MUTEX is unlocked by finally block...
                    DOUBLE_BUFFER.wait();
                }

                final Long newCommittedBlockHeight = UnspentTransactionOutputJvmManager.getUtxoBlockHeight();
                if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) {
                    throw new DatabaseException("UTXO set invalidated.");
                }

                _commitUnspentTransactionOutputsToDoubleBuffer();

                DOUBLE_BUFFER_THREAD = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Logger.debug("UTXO double buffer flusher thread started.");
                        final MilliTimer milliTimer = new MilliTimer();
                        milliTimer.start();

                        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                            TransactionUtil.startTransaction(databaseConnection);
                            UnspentTransactionOutputJvmManager.commitDoubleBufferedUnspentTransactionOutputs(newCommittedBlockHeight, databaseManager);
                            TransactionUtil.commitTransaction(databaseConnection);
                        }
                        catch (final Exception exception) {
                            _invalidateUncommittedUtxoSet();
                            DOUBLE_BUFFER.clear();
                            Logger.warn(exception);
                        }
                        finally {
                            DOUBLE_BUFFER_THREAD = null;

                            synchronized (DOUBLE_BUFFER) {
                                DOUBLE_BUFFER.notifyAll();
                            }
                        }

                        milliTimer.stop();
                        Logger.debug("UTXO double buffer flusher thread finished after " + milliTimer.getMillisecondsElapsed() + "ms.");
                    }
                });
                DOUBLE_BUFFER_THREAD.setName("UTXO Double Buffer Flusher");
                DOUBLE_BUFFER_THREAD.start();
            }

            // Must be done outside of synchronization block...
            if (commitAsyncMode == CommitAsyncMode.BLOCK_UNTIL_COMPLETE) {
                final Thread thread = DOUBLE_BUFFER_THREAD;
                if (thread != null) {
                    thread.join();
                }
            }

            return true;
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
            return false;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    protected void _commitUnspentTransactionOutputsToDoubleBuffer() {
        final int maxKeepCount = (int) (_maxUtxoCount * (1.0D - _purgePercent));

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

        // TODO: Initialize UtxoValues to the same object to reduce memory footprint.
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
                DOUBLE_BUFFER.put(utxoKey, utxoValue);
            }

            // Remove the UTXO from the cache if it is spent.
            if (transientSpentState.isSpent()) {
                iterator.remove();
                remainingPurgeCount -= 1;
            }
            else {
                // Mark the UTXO as flushed and clear the mandatory-flush flag.
                entry.setValue(new UtxoValue(flushedUnspentStateCode, utxoValue.blockHeight)); // TODO: cache/reuse UTXO values with the same blockHeight to reduce memory footprint.

                boolean wasPurged = false;
                if (remainingPurgeCount > 0) {
                    // Progressively purge UTXOs as the iterator creeps towards the end, prioritizing purging older UTXOs.
                    final long iterationsRemaining = (oldItemCount - i - 1L);
                    final double purgeAggressiveness = Math.min(1D, (( (double) remainingPurgeCount ) / iterationsRemaining)); // 0=purgeNothing, 1=purgeEverything
                    final long purgeDistanceThreshold = ( oldMinBlockHeight + ((long) (oldTotalBlockDistance * purgeAggressiveness)) );
                    if (utxoValue.blockHeight <= purgeDistanceThreshold) {
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

        Logger.debug("remainingPurgeCount=" + remainingPurgeCount);

        _minBlockHeight = minBlockHeight;
        _maxBlockHeight = maxBlockHeight;

        // This request for garbage collection is not strictly necessary but considering the drastic change in memory
        // usage, it is useful to ensure a collection after the double-buffer is cleared, particularly in cases when
        // periodic calls to gc aren't schedule (i.e. initial node boot).
        System.gc();
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
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (spentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _markTransactionOutputsAsSpent(spentTransactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException {
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (unspentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _insertUnspentTransactionOutputs(unspentTransactionOutputIdentifiers, blockHeight);
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void undoCreationOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _undoCreationOfTransactionOutputs(transactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void undoSpendingOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _undoSpendingOfTransactionOutputs(transactionOutputIdentifiers);
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

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
            else { // Possible cache miss
                // Check the double-buffer before checking disk...
                final UtxoValue doubleBufferedUtxoValue;
                synchronized (DOUBLE_BUFFER) {
                    doubleBufferedUtxoValue = DOUBLE_BUFFER.get(utxoKey);
                }
                if (doubleBufferedUtxoValue != null) {
                    final JvmSpentState spentState = doubleBufferedUtxoValue.getSpentState();
                    if (spentState.isSpent()) { return null; }
                }
                else {
                    // check the committed set for the UTXO.
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
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return new MutableList<TransactionOutput>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
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
                    else { // Possible cache miss...
                        // Check the double buffer first before queuing for disk-lookup...
                        final UtxoValue doubleBufferedUtxoValue;
                        synchronized (DOUBLE_BUFFER) {
                            doubleBufferedUtxoValue = DOUBLE_BUFFER.get(utxoKey);
                        }
                        if (doubleBufferedUtxoValue != null) {
                            final JvmSpentState spentState = doubleBufferedUtxoValue.getSpentState();
                            if (! spentState.isSpent()) {
                                unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                            }
                        }
                        else { // Queue for disk lookup.
                            cacheMissIdentifiers.add(transactionOutputIdentifier);
                        }
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

        final Map<Sha256Hash, Transaction> transactions;
        {
            final MutableList<Sha256Hash> transactionHashes;
            {
                final HashSet<Sha256Hash> transactionHashSet = new HashSet<>(unspentTransactionOutputIdentifiers.size());
                for (final TransactionOutputIdentifier transactionOutputIdentifier : unspentTransactionOutputIdentifiers) {
                    transactionHashSet.add(transactionOutputIdentifier.getTransactionHash());
                }
                transactionHashes = new MutableList<>(transactionHashSet);
                transactionHashes.sort(Sha256Hash.COMPARATOR);
            }

            transactions = transactionDatabaseManager.getTransactions(transactionHashes);
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
    public Boolean commitUnspentTransactionOutputs(final DatabaseManagerFactory databaseManagerFactory, final CommitAsyncMode commitAsyncMode) throws DatabaseException {
        if (! UnspentTransactionOutputJvmManager.isUtxoCacheReady()) {
            // Prevent committing a UTXO set that has been invalidated or empty...
            Logger.warn("Not committing UTXO set due to invalidated or empty cache.");
            return false;
        }

        UTXO_WRITE_MUTEX.lock();
        try {
            return _commitUnspentTransactionOutputs(databaseManagerFactory, commitAsyncMode);
        }
        catch (final Exception exception) {
            _invalidateUncommittedUtxoSetAndRethrow(exception);
            return false;
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
        if (! UnspentTransactionOutputJvmManager.isUtxoCacheReady()) { return 0L; }

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
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        UTXO_WRITE_MUTEX.lock();
        try {
            UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = blockHeight;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        return this.getUncommittedUnspentTransactionOutputBlockHeight(false);
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputBlockHeight(final Boolean noLock) throws DatabaseException {
        if (UnspentTransactionOutputJvmManager.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        if (! noLock) { UTXO_READ_MUTEX.lock(); }
        try {
            final Long uncommittedUtxoBlockHeight = UNCOMMITTED_UTXO_BLOCK_HEIGHT.value;
            if (uncommittedUtxoBlockHeight != null) {
                return uncommittedUtxoBlockHeight;
            }

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            return _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);
        }
        finally {
            if (! noLock) { UTXO_READ_MUTEX.unlock(); }
        }
    }

    @Override
    public void clearCommittedUtxoSet() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            synchronized (DOUBLE_BUFFER) {
                if (DOUBLE_BUFFER_THREAD != null) {
                    try {
                        DOUBLE_BUFFER_THREAD.join();
                    }
                    catch (final Exception exception) {
                        throw new DatabaseException(exception);
                    }
                }
                DOUBLE_BUFFER.clear();
            }

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            databaseConnection.executeSql(
                new Query("DELETE FROM committed_unspent_transaction_outputs")
            );
            databaseConnection.executeSql(
                new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                    .setParameter(COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
                    .setParameter(0L)
            );
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public void clearUncommittedUtxoSet() {
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

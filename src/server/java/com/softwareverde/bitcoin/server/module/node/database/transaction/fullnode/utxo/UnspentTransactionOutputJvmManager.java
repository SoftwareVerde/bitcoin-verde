package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.JvmSpentState;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.Utxo;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoKey;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.jvm.UtxoValue;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
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
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class UnspentTransactionOutputJvmManager implements UnspentTransactionOutputDatabaseManager {
    protected static final String COMMITTED_UTXO_BLOCK_HEIGHT_KEY = "committed_utxo_block_height";
    protected static final String UTXO_CACHE_LOAD_FILE_NAME = "utxo-cache.dat";

    protected static class OutputData {
        public final Long blockHeight;
        public final Long amount;
        public final byte[] lockingScript;

        public OutputData(final Long blockHeight, final Long amount, final byte[] lockingScript) {
            this.blockHeight = blockHeight;
            this.amount = amount;
            this.lockingScript = lockingScript;
        }
    }

    protected static UnspentTransactionOutput inflateTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final UtxoValue utxoValue) {
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
        final MutableUnspentTransactionOutput transactionOutput = new MutableUnspentTransactionOutput();
        transactionOutput.setAmount(utxoValue.amount);
        transactionOutput.setLockingScript(ByteArray.wrap(utxoValue.lockingScript));
        transactionOutput.setIndex(outputIndex);
        transactionOutput.setBlockHeight(utxoValue.blockHeight);
        return transactionOutput;
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
        UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = null;
        UTXO_SET.clear();
    }

    protected void _invalidateUncommittedUtxoSet() {
        UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = -1L;
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

                    final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoValue.blockHeight, utxoValue.amount, utxoValue.lockingScript);
                    queuedUpdates.put(utxoKey, newUtxoValue);
                }
                else { } // The UTXO was never written to disk, therefore it can be removed.
            }
            else { // The UTXO was (very) likely flushed to disk, and therefore must be removed from disk later.
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(true);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true);

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, UtxoValue.UNKNOWN_BLOCK_HEIGHT, UtxoValue.SPENT_AMOUNT);
                queuedUpdates.put(utxoKey, newUtxoValue);
            }
        }
        UTXO_SET.putAll(queuedUpdates);
    }

    protected void _insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs, final Long blockHeight) {
        Sha256Hash previousTransactionHash = null;
        byte[] previousTransactionHashBytes = null;

        final int itemCount = transactionOutputIdentifiers.getCount();
        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (int i = 0; i < itemCount; ++i) {
            final TransactionOutputIdentifier transactionOutputIdentifier = transactionOutputIdentifiers.get(i);
            final TransactionOutput transactionOutput = transactionOutputs.get(i);

            final UtxoKey utxoKey;
            {
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                if (previousTransactionHash != transactionHash) { // NOTE: intentional reference comparison to leverage asConst optimization of TransactionOutputIdentifier without 32-byte iteration...
                    previousTransactionHash = transactionHash;
                    previousTransactionHashBytes = transactionHash.getBytes();
                }

                final int outputIndex = transactionOutputIdentifier.getOutputIndex();
                utxoKey = new UtxoKey(previousTransactionHashBytes, outputIndex);
            }

            final UtxoValue utxoValue;
            {
                final JvmSpentState spentState = new JvmSpentState();
                spentState.setIsSpent(false);
                spentState.setIsFlushedToDisk(false);
                spentState.setIsFlushMandatory(false);

                final long amount = transactionOutput.getAmount();

                final LockingScript lockingScript = transactionOutput.getLockingScript();
                final ByteArray lockingScriptBytes = lockingScript.getBytes();

                utxoValue = new UtxoValue(spentState, blockHeight, amount, lockingScriptBytes.getBytes());
            }

            final UtxoValue existingUtxoValue = UTXO_SET.get(utxoKey);

            UtxoValue customUtxoValue = null;
            if (existingUtxoValue != null) {
                final JvmSpentState existingSpentState = existingUtxoValue.getSpentState();
                if (existingSpentState.isFlushMandatory()) { // Preserve the mandatoryFlush flag in case of a reorg...
                    final JvmSpentState newJvmSpentState = new JvmSpentState();
                    newJvmSpentState.setIsSpent(false);
                    newJvmSpentState.setIsFlushedToDisk(false);
                    newJvmSpentState.setIsFlushMandatory(true);

                    final long amount = utxoValue.amount;
                    final byte[] lockingScript = utxoValue.lockingScript;
                    customUtxoValue = new UtxoValue(newJvmSpentState, blockHeight, amount, lockingScript);
                }
            }

            queuedUpdates.put(utxoKey, (customUtxoValue != null ? customUtxoValue : utxoValue));
        }
        UTXO_SET.putAll(queuedUpdates);

        if (blockHeight != UtxoValue.UNKNOWN_BLOCK_HEIGHT) {
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

                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, (utxoValue != null ? utxoValue.blockHeight : UtxoValue.UNKNOWN_BLOCK_HEIGHT), UtxoValue.SPENT_AMOUNT);
                queuedUpdates.put(utxoKey, newUtxoValue);
            }
        }
        UTXO_SET.putAll(queuedUpdates);
    }

    /**
     * Attempts to find the outputKey's corresponding amount and LockingScript.
     *  If the amount/LockingScript cannot be found, then null is returned.
     *  This operation can be fairly expensive.
     *  Lookup is attempted first via the utxo table, then the pruned outputs table (aka the semi- undo log, if enabled),
     *  then the mempool, then the block flat file.
     */
    protected OutputData _findOutputData(final UtxoKey utxoKey, final DatabaseManager databaseManager) throws DatabaseException {
        { // Check for the amount/script via the cached UTXO state...
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT block_height, amount, locking_script FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
                    .setParameter(utxoKey.transactionHash)
                    .setParameter(utxoKey.outputIndex)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Long blockHeight = row.getLong("block_height");
                final Long amount = row.getLong("amount");
                final byte[] lockingScriptBytes = row.getBytes("locking_script");
                return new OutputData(blockHeight, amount, lockingScriptBytes);
            }
        }

        UndoLogDatabaseManager.READ_LOCK.lock();
        try { // Check for the amount/script via the pruned_previous_transaction_outputs table (aka the semi- undo-log)...

            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT block_height, amount, locking_script FROM pruned_previous_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
                    .setParameter(utxoKey.transactionHash)
                    .setParameter(utxoKey.outputIndex)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Long blockHeight = row.getLong("block_height");
                final Long amount = row.getLong("amount");
                final byte[] lockingScriptBytes = row.getBytes("locking_script");
                return new OutputData(blockHeight, amount, lockingScriptBytes);
            }
        }
        finally {
            UndoLogDatabaseManager.READ_LOCK.unlock();
        }

        { // Attempt to find the amount/script from block flat-file, which may not exist for nodes operating in pruned mode.
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final Sha256Hash transactionHash = Sha256Hash.wrap(utxoKey.transactionHash);
            final int outputIndex = utxoKey.outputIndex;

            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
            if (transactionId == null) { return null; }
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            if (transaction == null) { return null; }

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            if (outputIndex >= transactionOutputs.getCount()) { return null; }

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            final Long amount = transactionOutput.getAmount();
            final LockingScript lockingScript = transactionOutput.getLockingScript();
            final ByteArray lockingScriptBytes = lockingScript.getBytes();
            return new OutputData(blockHeight, amount, lockingScriptBytes.getBytes());
        }
    }

    protected void _undoSpendingOfTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final TreeMap<UtxoKey, UtxoValue> queuedUpdates = new TreeMap<UtxoKey, UtxoValue>(UtxoKey.COMPARATOR);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.remove(utxoKey);
            if (utxoValue != null) {
                final JvmSpentState spentState = utxoValue.getSpentState();
                if (spentState.isFlushedToDisk() || spentState.isFlushMandatory()) {
                    final JvmSpentState newSpentState = new JvmSpentState();
                    newSpentState.setIsSpent(false);
                    newSpentState.setIsFlushedToDisk(false);
                    newSpentState.setIsFlushMandatory(true);

                    if (utxoValue.blockHeight != UtxoValue.UNKNOWN_BLOCK_HEIGHT) {
                        final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoValue.blockHeight, utxoValue.amount, utxoValue.lockingScript);
                        queuedUpdates.put(utxoKey, newUtxoValue);
                    }
                    else {
                        // The only way utxoValue.blockHeight would be unknown should be if the UTXO was created, flushed to disk, then removed from the cache and then was later marked
                        //  as spent (i.e. the disk state is unspent and the cached value is a sentinel value to delete the record).  The UTXO could be re-added to the cache
                        //  however it would not have a blockHeight, so instead, the next request for this UTXO will be a cache miss in order to have the blockHeight loaded from disk.
                        if ( (! spentState.isSpent()) || (! spentState.isFlushMandatory()) ) {
                            throw new DatabaseException("Unexpected UTXO State: " + transactionOutputIdentifier + " " + spentState.intValue());
                        }
                    }
                }
                else {
                    // The UTXO was created (and spent) in the block being undone (and therefore would never need synchronizing), so removing alone is sufficient.
                    // Typically spent outputs are removed if they do not require flushing, so this is unlikely to happen.
                }
            }
            else {
                final JvmSpentState newSpentState = new JvmSpentState();
                newSpentState.setIsSpent(false);
                newSpentState.setIsFlushedToDisk(false);
                newSpentState.setIsFlushMandatory(true); // It is unknown if the UTXO was flushed to disk.

                final OutputData utxoData = _findOutputData(utxoKey, _databaseManager);
                if (utxoData == null) {
                    throw new DatabaseException("Unable to restore UTXO: " + HexUtil.toHexString(utxoKey.transactionHash) + ":" + utxoKey.outputIndex);
                }
                final UtxoValue newUtxoValue = new UtxoValue(newSpentState, utxoData.blockHeight, utxoData.amount, utxoData.lockingScript);
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

                // final Query query = new Query("UPDATE committed_unspent_transaction_outputs SET amount = -1 WHERE (transaction_hash, `index`) IN (?)");
                final Query query = new Query("DELETE FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?)");
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
        final BatchRunner.Batch<Utxo> onDiskUtxoInsertBatch = new BatchRunner.Batch<Utxo>() {
            @Override
            public void run(final List<Utxo> batchItems) throws Exception {
                onDiskInsertBatchCount.incrementAndGet();

                // NOTE: block_height is currently unused, however the field could become useful during re-loading UTXOs
                //  into the cache based on recency, to facilitate UTXO commitments, and to facilitate more intelligent reorgs.

                // NOTE: resetting the amount on a duplicate key is required in order to undo a block that has been committed to disk.
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT INTO committed_unspent_transaction_outputs (transaction_hash, `index`, block_height, amount, locking_script) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)");
                for (final Utxo unspentTransactionOutput : batchItems) {
                    onDiskInsertItemCount.incrementAndGet();

                    final long blockHeight = unspentTransactionOutput.getBlockHeight();
                    batchedInsertQuery.setParameter(unspentTransactionOutput.getTransactionHash());
                    batchedInsertQuery.setParameter(unspentTransactionOutput.getOutputIndex());
                    batchedInsertQuery.setParameter(Math.max(blockHeight, 0L)); // block_height is an UNSIGNED INT; in the case of a reorg UTXO, the UTXO height can be set to -1, so 0 is used as a compatible placeholder.
                    batchedInsertQuery.setParameter(unspentTransactionOutput.getAmount());
                    batchedInsertQuery.setParameter(unspentTransactionOutput.getLockingScript());
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
        final MutableList<Utxo> nextInsertBatch = new MutableList<Utxo>(maxUtxoPerBatch);

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
                final Utxo unspentTransactionOutput = new Utxo(utxoKey, utxoValue);
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

    protected void _startClearExpiredPrunedOutputsThread(final Long committedUtxoBlockHeight, final DatabaseManagerFactory databaseManagerFactory) {
        final Thread thread = (new Thread(new Runnable() {
            @Override
            public void run() {
                try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    final UndoLogDatabaseManager undoLogDatabaseManager = new UndoLogDatabaseManager(databaseManager);
                    undoLogDatabaseManager.clearExpiredPrunedOutputs(committedUtxoBlockHeight);
                }
                catch (final Exception exception) {
                    Logger.debug("Unable to clear expired pruned outputs.", exception);
                }
            }
        }));
        thread.setName("PrunedOutputsCleanupThread");
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        thread.start();
    }

    protected Boolean _commitUnspentTransactionOutputs(final DatabaseManagerFactory databaseManagerFactory, final CommitAsyncMode commitAsyncMode) throws DatabaseException {
        if (! UtxoCacheStaticState.isUtxoCacheReady()) {
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

                final Long newCommittedBlockHeight = UtxoCacheStaticState.getUtxoBlockHeight();
                if (UtxoCacheStaticState.isUtxoCacheDefunct()) {
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

                            _startClearExpiredPrunedOutputsThread(newCommittedBlockHeight, databaseManagerFactory);
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
                entry.setValue(new UtxoValue(flushedUnspentStateCode, utxoValue.blockHeight, utxoValue.amount, utxoValue.lockingScript));

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

                if ( (! wasPurged) && (utxoValue.blockHeight != UtxoValue.UNKNOWN_BLOCK_HEIGHT) ) {
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

    protected Tuple<UtxoKey, UtxoValue> _inflateUtxoFromCommittedTransactionOutputRow(final TransactionOutputIdentifier transactionOutputIdentifier, final Row row) {
        final Long amount = row.getLong("amount");
        final ByteArray lockingScript = ByteArray.wrap(row.getBytes("locking_script"));
        final Long blockHeight = row.getLong("block_height");

        final MutableUnspentTransactionOutput transactionOutput = new MutableUnspentTransactionOutput();
        {
            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
            transactionOutput.setIndex(outputIndex);
            transactionOutput.setAmount(amount);
            transactionOutput.setLockingScript(lockingScript);
            transactionOutput.setBlockHeight(blockHeight);
        }

        final JvmSpentState newSpentState = new JvmSpentState();
        newSpentState.setIsSpent(false);
        newSpentState.setIsFlushedToDisk(true);
        newSpentState.setIsFlushMandatory(false);

        final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
        final UtxoValue utxoValue = new UtxoValue(newSpentState, blockHeight, amount, lockingScript.getBytes());

        return new Tuple<>(utxoKey, utxoValue);
    }

    protected void _writeCacheLoadFile() throws DatabaseException, IOException {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final String dataDirectory = _blockStore.getDataDirectory();
        if (dataDirectory == null) { return; }

        final File file = new File(dataDirectory + "/" + UTXO_CACHE_LOAD_FILE_NAME);
        if (file.exists()) {
            file.delete();
        }

        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        final int outputIndexByteCount = 4;
        final int bytesPerIdentifier = (Sha256Hash.BYTE_COUNT + outputIndexByteCount);
        final int pageSize = (int) (16L * ByteUtil.Unit.Binary.MEBIBYTES);
        final MutableByteArray buffer = new MutableByteArray(pageSize);
        int bufferWriteIndex = 0;

        long writtenIdentifierCount = 0L;
        long writtenByteCount = 0L;

        try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            final Long committedBlockHeight = UtxoCacheStaticState.getUtxoBlockHeight();
            {
                final byte[] blockHeightBytes = ByteUtil.longToBytes(committedBlockHeight);
                fileOutputStream.write(blockHeightBytes);
                writtenByteCount += blockHeightBytes.length;
            }

            for (final UtxoKey utxoKey : UTXO_SET.keySet()) {
                if (bufferWriteIndex + bytesPerIdentifier >= buffer.getByteCount()) {
                    fileOutputStream.write(buffer.unwrap(), 0, bufferWriteIndex);
                    writtenByteCount += bufferWriteIndex;
                    bufferWriteIndex = 0;
                }

                final byte[] outputIndexBytes = ByteUtil.integerToBytes(utxoKey.outputIndex);

                buffer.setBytes(bufferWriteIndex, utxoKey.transactionHash);
                bufferWriteIndex += utxoKey.transactionHash.length;

                buffer.setBytes(bufferWriteIndex, outputIndexBytes);
                bufferWriteIndex += outputIndexBytes.length;

                writtenIdentifierCount += 1L;
            }

            if (bufferWriteIndex != 0) {
                fileOutputStream.write(buffer.unwrap(), 0, bufferWriteIndex);
                writtenByteCount += bufferWriteIndex;
            }
        }

        nanoTimer.stop();
        if (Logger.isTraceEnabled()) {
            Logger.trace("Wrote " + writtenIdentifierCount + " identifiers (" + writtenByteCount + " bytes) in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    protected void _populateCacheViaLoadFile() throws DatabaseException, IOException {
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        final int maxKeepCount = (int) (_maxUtxoCount * (1.0D - _purgePercent));
        long maxRemainingCount = maxKeepCount;

        final String dataDirectory = _blockStore.getDataDirectory();
        final File file = new File(dataDirectory + "/" + UTXO_CACHE_LOAD_FILE_NAME);
        if ( (dataDirectory == null) || (! file.exists()) ) {
            Logger.debug("UtxoCache file does not exist.");
            return;
        }

        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            final long blockHeight;
            {
                final byte[] blockHeightBytes = new byte[8];
                final int bytesRead = fileInputStream.read(blockHeightBytes);
                if (! Util.areEqual(bytesRead, blockHeightBytes.length)) {
                    Logger.debug("Unable to read UtxoCache file block height.");
                    return;
                }

                blockHeight = ByteUtil.bytesToLong(blockHeightBytes);
            }

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            final Long committedBlockHeight = _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);

            if (! Util.areEqual(committedBlockHeight, blockHeight)) {
                Logger.info("UtxoCache file does not match committed UTXO block height; aborting UTXO cache loading.");
                return;
            }

            final int outputIndexByteCount = 4;
            final int bytesPerIdentifier = (Sha256Hash.BYTE_COUNT + outputIndexByteCount);
            final int batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
            final MutableByteArray buffer = new MutableByteArray(batchSize * bytesPerIdentifier);

            int bytesRead;
            do {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                bytesRead = fileInputStream.read(buffer.unwrap());
                final int identifierCount = (int) Math.min(maxRemainingCount, (bytesRead / bytesPerIdentifier));
                if (identifierCount < 1) { continue; }

                final MutableList<TransactionOutputIdentifier> transactionOutputIdentifiers = new MutableList<>(identifierCount);
                for (int i = 0; i < identifierCount; ++i) {
                    final int offset = (i * bytesPerIdentifier);
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(buffer.getBytes(offset, Sha256Hash.BYTE_COUNT));
                    final Integer outputIndex = ByteUtil.bytesToInteger(buffer.getBytes(offset + Sha256Hash.BYTE_COUNT, outputIndexByteCount));
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                    transactionOutputIdentifiers.add(transactionOutputIdentifier);
                }
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT transaction_hash, `index`, amount, locking_script, block_height FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?) AND amount >= 0")
                        .setExpandedInClauseParameters(transactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
                );
                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                    final Tuple<UtxoKey, UtxoValue> utxo = _inflateUtxoFromCommittedTransactionOutputRow(transactionOutputIdentifier, row);
                    UTXO_SET.putIfAbsent(utxo.first, utxo.second);
                    maxRemainingCount -= 1;
                }

                nanoTimer.stop();
                Logger.trace("Populated " + rows.size() + " UTXOs in " + nanoTimer.getMillisecondsElapsed() + "ms, " + maxRemainingCount + " remaining.");

                if (maxRemainingCount < 1) { break; }
            } while (bytesRead >= 0);
        }
    }

    protected void _populateCacheWithRecentTransactionUtxos() throws DatabaseException {
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
        final Long committedBlockHeight = _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);

        final int blockBatchCount = 12;

        BlockId nextBlockId = blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, committedBlockHeight);
        final int maxKeepCount = (int) (_maxUtxoCount * (1.0D - _purgePercent));
        long maxRemainingCount = maxKeepCount;

        long loadBlockHeight = committedBlockHeight;
        while (maxRemainingCount > 0) {
            if (nextBlockId == null) { break; }

            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            final MutableList<BlockId> blockIds = new MutableList<>();
            for (int i = 0; i < blockBatchCount; ++i) {
                blockIds.add(nextBlockId);

                nextBlockId = blockHeaderDatabaseManager.getAncestorBlockId(nextBlockId, 1);
                if (nextBlockId == null) { break; }
                loadBlockHeight -= 1L;
            }
            if (blockIds.isEmpty()) { break; }

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT committed_unspent_transaction_outputs.transaction_hash, committed_unspent_transaction_outputs.`index`, committed_unspent_transaction_outputs.amount, committed_unspent_transaction_outputs.locking_script, committed_unspent_transaction_outputs.block_height FROM committed_unspent_transaction_outputs INNER JOIN transactions ON transactions.hash = committed_unspent_transaction_outputs.transaction_hash INNER JOIN block_transactions ON block_transactions.transaction_id = transactions.id WHERE block_transactions.block_id IN (?) AND committed_unspent_transaction_outputs.amount > 0 ORDER BY committed_unspent_transaction_outputs.block_height DESC LIMIT " + maxRemainingCount)
                    .setInClauseParameters(blockIds, ValueExtractor.IDENTIFIER)
            );
            for (final Row row : rows) {
                final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                final Integer outputIndex = row.getInteger("index");
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                final Tuple<UtxoKey, UtxoValue> utxo = _inflateUtxoFromCommittedTransactionOutputRow(transactionOutputIdentifier, row);
                UTXO_SET.putIfAbsent(utxo.first, utxo.second);
                maxRemainingCount -= 1;
            }

            nanoTimer.stop();
            Logger.trace("Populated " + rows.size() + " UTXOs in " + nanoTimer.getMillisecondsElapsed() + "ms, " + maxRemainingCount + " remaining. BlockHeight: [" + (loadBlockHeight - blockIds.getCount()) + ", " + loadBlockHeight + "]");
        }
    }


    protected UnspentTransactionOutput _getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier, final Boolean updateCacheOnMiss) throws DatabaseException {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        try {
            final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
            final UtxoValue utxoValue = UTXO_SET.get(utxoKey);
            if (utxoValue != null) {
                final JvmSpentState spentState = utxoValue.getSpentState();
                if (spentState.isSpent()) { return null; }
                return UnspentTransactionOutputJvmManager.inflateTransactionOutput(transactionOutputIdentifier, utxoValue);
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
                    return UnspentTransactionOutputJvmManager.inflateTransactionOutput(transactionOutputIdentifier, doubleBufferedUtxoValue);
                }
                else {
                    // check the committed set for the UTXO.
                    final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT amount, locking_script, block_height FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ? LIMIT 1")
                            .setParameter(transactionHash)
                            .setParameter(outputIndex)
                    );

                    if (rows.isEmpty()) { return null; }

                    final Row row = rows.get(0);
                    final Long amount = row.getLong("amount");
                    if (amount < 0L) { return null; }

                    final Long blockHeight = row.getLong("block_height");
                    final ByteArray lockingScript = ByteArray.wrap(row.getBytes("locking_script"));

                    final MutableUnspentTransactionOutput transactionOutput = new MutableUnspentTransactionOutput();
                    {
                        transactionOutput.setIndex(outputIndex);
                        transactionOutput.setAmount(amount);
                        transactionOutput.setLockingScript(lockingScript);
                        transactionOutput.setBlockHeight(blockHeight);
                    }

                    if (updateCacheOnMiss) {
                        final Tuple<UtxoKey, UtxoValue> cachedUtxoValue = _inflateUtxoFromCommittedTransactionOutputRow(transactionOutputIdentifier, row);
                        UTXO_SET.putIfAbsent(cachedUtxoValue.first, cachedUtxoValue.second);
                    }

                    return transactionOutput;
                }
            }
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
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
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
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
    public void insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers, final List<TransactionOutput> transactionOutputs, final Long blockHeight) throws DatabaseException {
        final int itemCount = transactionOutputIdentifiers.getCount();
        final int foundItemCount = transactionOutputs.getCount();

        if (! Util.areEqual(itemCount, foundItemCount)) { throw new RuntimeException("List size mismatch. Expected " + itemCount + " found " + foundItemCount); }
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            _insertUnspentTransactionOutputs(transactionOutputIdentifiers, transactionOutputs, blockHeight);
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
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
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
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
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
    public UnspentTransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        UTXO_READ_MUTEX.lock();
        try {
            return _getUnspentTransactionOutput(transactionOutputIdentifier, false);
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }
    }

    @Override
    public UnspentTransactionOutput loadUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        UTXO_WRITE_MUTEX.lock();
        try {
            return _getUnspentTransactionOutput(transactionOutputIdentifier, true);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    @Override
    public List<UnspentTransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }
        if (transactionOutputIdentifiers.isEmpty()) { return new MutableList<>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final int transactionOutputIdentifierCount = transactionOutputIdentifiers.getCount();

        final MutableList<TransactionOutputIdentifier> cacheMissIdentifiers = new MutableList<TransactionOutputIdentifier>(transactionOutputIdentifierCount);
        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);

        final HashMap<TransactionOutputIdentifier, UnspentTransactionOutput> transactionOutputs = new HashMap<>();

        UTXO_READ_MUTEX.lock();
        try {
            final NanoTimer cacheTimer = new NanoTimer();
            cacheTimer.start();

            int cacheHitCount = 0;
            { // Only return outputs that are in the UTXO set...
                for (TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                    final UtxoKey utxoKey = new UtxoKey(transactionOutputIdentifier);
                    final UtxoValue utxoValue = UTXO_SET.get(utxoKey);
                    if (utxoValue != null) {
                        final JvmSpentState spentState = utxoValue.getSpentState();
                        if (! spentState.isSpent()) {
                            unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);

                            final UnspentTransactionOutput transactionOutput = UnspentTransactionOutputJvmManager.inflateTransactionOutput(transactionOutputIdentifier, utxoValue);
                            transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
                            cacheHitCount += 1;
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

                                final UnspentTransactionOutput transactionOutput = UnspentTransactionOutputJvmManager.inflateTransactionOutput(transactionOutputIdentifier, doubleBufferedUtxoValue);
                                transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
                                cacheHitCount += 1;
                            }
                        }
                        else { // Queue for disk lookup.
                            cacheMissIdentifiers.add(transactionOutputIdentifier);
                        }
                    }
                }
            }
            cacheTimer.stop();
            Logger.trace("getUnspentTransactionOutputs " + cacheHitCount + " from cache in " + cacheTimer.getMillisecondsElapsed() + "ms.");

            final NanoTimer diskTimer = new NanoTimer();
            diskTimer.start();

            int diskHitCount = 0;
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
                                new Query("SELECT transaction_hash, `index`, amount, locking_script, block_height FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?) AND amount >= 0")
                                    .setExpandedInClauseParameters(transactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
                            ));
                        }
                    });
                    for (final Row row : rows) {
                        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                        final Integer outputIndex = row.getInteger("index");
                        final Long amount = row.getLong("amount");
                        final ByteArray lockingScript = ByteArray.wrap(row.getBytes("locking_script"));
                        final Long blockHeight = row.getLong("block_height");

                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                        unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);

                        final MutableUnspentTransactionOutput transactionOutput = new MutableUnspentTransactionOutput();
                        {
                            transactionOutput.setIndex(outputIndex);
                            transactionOutput.setAmount(amount);
                            transactionOutput.setLockingScript(lockingScript);
                            transactionOutput.setBlockHeight(blockHeight);
                        }

                        transactionOutputs.put(transactionOutputIdentifier, transactionOutput);
                        diskHitCount += 1;
                    }
                }
            }

            cacheTimer.stop();
            Logger.trace("getUnspentTransactionOutputs " + diskHitCount + " from disk in " + diskTimer.getMillisecondsElapsed() + "ms.");
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final ImmutableListBuilder<UnspentTransactionOutput> transactionOutputsBuilder = new ImmutableListBuilder<>(transactionOutputIdentifierCount);
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            if (! unspentTransactionOutputIdentifiers.contains(transactionOutputIdentifier)) {
                transactionOutputsBuilder.add(null);
                continue;
            }

            final UnspentTransactionOutput transactionOutput = transactionOutputs.get(transactionOutputIdentifier);
            transactionOutputsBuilder.add(transactionOutput);
        }

        try {
            return transactionOutputsBuilder.build();
        }
        finally {
            nanoTimer.stop();
            Logger.trace("getUnspentTransactionOutputs list overhead: " + nanoTimer.getMillisecondsElapsed() + "ms");
        }
    }

    @Override
    public Boolean commitUnspentTransactionOutputs(final DatabaseManagerFactory databaseManagerFactory, final CommitAsyncMode commitAsyncMode) throws DatabaseException {
        if (! UtxoCacheStaticState.isUtxoCacheReady()) {
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
        if (! UtxoCacheStaticState.isUtxoCacheReady()) { return 0L; }

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
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        UTXO_WRITE_MUTEX.lock();
        try {
            UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = blockHeight;
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
        if (UtxoCacheStaticState.isUtxoCacheDefunct()) { throw new DatabaseException("Attempting to access invalidated UTXO set."); }

        if (! noLock) { UTXO_READ_MUTEX.lock(); }
        try {
            final Long uncommittedUtxoBlockHeight = UtxoCacheStaticState.UNCOMMITTED_UTXO_BLOCK_HEIGHT.value;
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

    public void populateCache(final CacheLoadingMethod cacheLoadingMethod) throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            if (cacheLoadingMethod == CacheLoadingMethod.LOAD_VIA_UTXO_LOAD_FILE) {
                _populateCacheViaLoadFile();
                return;
            }

            _populateCacheWithRecentTransactionUtxos();
        }
        catch (final IOException exception) {
            throw new DatabaseException(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public void writeUtxoCacheLoadFile() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            _writeCacheLoadFile();
        }
        catch (final IOException exception) {
            throw new DatabaseException(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public Boolean doesUtxoCacheLoadFileExist() {
        final String dataDirectory = _blockStore.getDataDirectory();
        final File file = new File(dataDirectory + "/" + UTXO_CACHE_LOAD_FILE_NAME);
        return ( (dataDirectory != null) && file.exists() );
    }

    public void deleteUtxoCacheLoadFile() {
        final String dataDirectory = _blockStore.getDataDirectory();
        final File file = new File(dataDirectory + "/" + UTXO_CACHE_LOAD_FILE_NAME);
        if ( (dataDirectory == null) || (! file.exists()) ) { return; }

        file.delete();
    }
}

package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.BlockingQueueBatchRunner;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UnspentTransactionOutputDatabaseManager {

    public static class SpentState {
        public static final Integer SYNCHRONIZED_UNSPENT = null;
        public static final Integer UNSYNCHRONIZED_UNSPENT = 0;
        public static final Integer UNSYNCHRONIZED_SPENT = 1;
        public static final Integer SYNCHRONIZED_SPENT = 2;

        public static Boolean isUnspent(final Integer isSpent) {
            return (Util.areEqual(isSpent, SYNCHRONIZED_UNSPENT) || Util.areEqual(isSpent, UNSYNCHRONIZED_UNSPENT));
        }

        public static Boolean isUncommittedToDisk(final Integer isSpent) {
            return (Util.areEqual(isSpent, UNSYNCHRONIZED_UNSPENT) || Util.areEqual(isSpent, UNSYNCHRONIZED_SPENT));
        }

        public static Boolean wasSpent(final Integer isSpent) {
            return (! SpentState.isUnspent(isSpent)); // NOTE: Subtly allows for values greater than 2 or less than 0...
        }

        public static Boolean wasCommittedToDisk(final Integer isSpent) {
            return (! SpentState.isUncommittedToDisk(isSpent)); // NOTE: Subtly allows for values greater than 2 or less than 0...
        }
    }

    public static void lockUtxoSet() {
        UTXO_WRITE_MUTEX.lock();
    }

    public static void unlockUtxoSet() {
        UTXO_WRITE_MUTEX.unlock();
    }

    protected static final ReentrantReadWriteLock.ReadLock UTXO_READ_MUTEX;
    protected static final ReentrantReadWriteLock.WriteLock UTXO_WRITE_MUTEX;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(false);

        UTXO_READ_MUTEX = readWriteLock.readLock();
        UTXO_WRITE_MUTEX = readWriteLock.writeLock();
    }

    protected static final Container<Long> UNCOMMITTED_UTXO_BLOCK_HEIGHT = new Container<Long>(0L);
    protected static final String COMMITTED_UTXO_BLOCK_HEIGHT_KEY = "committed_utxo_block_height";

    /**
     * MAX_UTXO_CACHE_COUNT determines the maximum number of rows (both clean and dirty) within the unspent_transaction_outputs in-memory table.
     *  This value is a function of the max_heap_table_size, which caps out at 4 gigabytes.
     *  The theoretical value, calculated via (unspent_transaction_outputs::max_data_length / unspent_transaction_outputs::avg_row_length), is not quite accurate.
     *  After some empirical evidence, the actual unspent_transaction_outputs::max_data_length and unspent_transaction_outputs::avg_row_length reported by MySQL aren't sufficient/accurate.
     *  The actual observed max row count is 39216366, which renders exactly 4GB of memory (1882505376 in data, 2412509502 in indexes), which puts row_length at 48 bytes per row, 109.55 including indexes.
     *  The value chosen, 33554432 (2^25), is the closest power-of-two under the 4GB max, which allows for some additional (although unobserved) inaccuracies.
     *
     *  Update: Using a BTREE for the PRIMARY KEY changes the used bytes per row (BTREE is less memory-efficient but more performant).
     *      The actual observed max row count with these settings is 27891486, which results in 1338877344 in data, 2941008296 in indexes). 48 bytes per row, 154 including indexes.
     *      The new value chosen is not near a clean power of two, so 27M was chosen (27889398 being the theoretical max).
     *
     *  Update 2: Removing the index on is_spent changes the data+index size per row to 109 including indexes.
     *      The new value chosen is now 33554432 (2^25) (39403369 being the new theoretical max).
     */
    public static final Long BYTES_PER_UTXO = 128L; // 109L;
    public static final Long DEFAULT_MAX_UTXO_CACHE_COUNT = 33554432L; // 2^25
    public static final Float DEFAULT_PURGE_PERCENT = 0.50F;

    protected final Long _maxUtxoCount;
    protected final Float _purgePercent;
    protected final BlockStore _blockStore;
    protected final FullNodeDatabaseManager _databaseManager;
    protected final MasterInflater _masterInflater;

    protected static Long _getCommittedUnspentTransactionOutputBlockHeight(final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, value FROM properties WHERE `key` = ?")
                .setParameter(COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("value"), 0L);
    }

    protected static void _commitUnspentTransactionOutputs(final Long maxUtxoCount, final Float purgePercent, final FullNodeDatabaseManager transactionalDatabaseManager, final DatabaseConnection nonTransactionalDatabaseConnection, final DatabaseConnectionFactory nonTransactionalDatabaseConnectionFactory) throws DatabaseException {
        final DatabaseConnection transactionalDatabaseConnection = transactionalDatabaseManager.getDatabaseConnection();

        final long maxKeepCount = (long) (maxUtxoCount * (1.0D - purgePercent));
        final int maxUtxoPerBatch = 1024;

        final Long minUtxoBlockHeight;
        final Long maxUtxoBlockHeight;
        {
            final java.util.List<Row> rows = nonTransactionalDatabaseConnection.query(
                new Query("SELECT COALESCE(MIN(block_height), 0) AS min_block_height, COALESCE(MAX(block_height), 0) AS max_block_height FROM unspent_transaction_outputs")
            );
            final Row row = rows.get(0);
            maxUtxoBlockHeight = row.getLong("max_block_height");
            minUtxoBlockHeight = row.getLong("min_block_height");
        }

        Logger.trace("UTXO Commit starting: max_block_height=" + maxUtxoBlockHeight + ", min_block_height=" + minUtxoBlockHeight);

        nonTransactionalDatabaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs_buffer")
        );

        // The commit process has three core routines:
        //  1. Removing UTXOs marked as spent from the memory cache.
        //  2. Removing UTXOs marked as spent from the on-disk UTXO set.
        //  3. Committing unspent UTXOs into the on-disk UTXO set.
        // The three routines are executed asynchronously via different database threads.
        //  Since the routines use different database connections, these operations are not executed in a Database Transaction.
        //  Therefore, an error encountered here results in a corrupted UTXO set and needs to be rebuilt.
        //  It's plausible to execute the changes to the on-disk UTXO set with a single Database Transaction,
        //  which would render errors recoverable by only rebuilding since the last commitment instead of rebuilding the whole UTXO set, but this is not currently done.

        final BlockingQueueBatchRunner<UnspentTransactionOutput> inMemoryUtxoRetainerBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, true, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO unspent_transaction_outputs_buffer (transaction_hash, `index`, is_spent, block_height) VALUES (?, ?, NULL, ?)");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getBlockHeight());
                }

                try (final DatabaseConnection nonTransactionalDatabaseConnection = nonTransactionalDatabaseConnectionFactory.newConnection()) {
                    nonTransactionalDatabaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });
        final BlockingQueueBatchRunner<TransactionOutputIdentifier> onDiskUtxoPostDeleteBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, true, new BatchRunner.Batch<TransactionOutputIdentifier>() {
            @Override
            public void run(final List<TransactionOutputIdentifier> unspentTransactionOutputs) throws Exception {
                // Queue the output for deletion...
                final Query query = new BatchedInsertQuery("INSERT IGNORE INTO stale_committed_unspent_transaction_outputs (transaction_hash, `index`) VALUES (?, ?)");
                for (final TransactionOutputIdentifier unspentTransactionOutput : unspentTransactionOutputs) {
                    query.setParameter(unspentTransactionOutput.getTransactionHash());
                    query.setParameter(unspentTransactionOutput.getOutputIndex());
                }

                // NOTE: Marking the output for deletion can use a non-transactional connection since the actual delete process checks the is_spent value...
                try (final DatabaseConnection nonTransactionalDatabaseConnection = nonTransactionalDatabaseConnectionFactory.newConnection()) {
                    nonTransactionalDatabaseConnection.executeSql(query);
                }
            }
        });
        final BlockingQueueBatchRunner<TransactionOutputIdentifier> onDiskUtxoDeleteBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, false, new BatchRunner.Batch<TransactionOutputIdentifier>() {
            @Override
            public void run(final List<TransactionOutputIdentifier> unspentTransactionOutputs) throws Exception {
                { // Commit the output as spent...
                    final Query query = new Query("UPDATE committed_unspent_transaction_outputs SET is_spent = 1 WHERE (transaction_hash, `index`) IN (?)");
                    query.setExpandedInClauseParameters(unspentTransactionOutputs, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);

                    synchronized (transactionalDatabaseConnection) {
                        transactionalDatabaseConnection.executeSql(query);
                    }
                }

                // Queue the output for deletion...
                for (final TransactionOutputIdentifier unspentTransactionOutput : unspentTransactionOutputs) {
                    onDiskUtxoPostDeleteBatchRunner.addItem(unspentTransactionOutput);
                }
            }
        });
        final BlockingQueueBatchRunner<UnspentTransactionOutput> onDiskUtxoInsertBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, false, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO committed_unspent_transaction_outputs (transaction_hash, `index`, block_height) VALUES (?, ?, ?)");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getBlockHeight());
                }

                synchronized (transactionalDatabaseConnection) {
                    transactionalDatabaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });

        inMemoryUtxoRetainerBatchRunner.start();
        onDiskUtxoDeleteBatchRunner.start();
        onDiskUtxoPostDeleteBatchRunner.start();
        onDiskUtxoInsertBatchRunner.start();

        long keepCount = 0L;
        Long blockHeight = maxUtxoBlockHeight;
        final java.util.ArrayList<Tuple<Long, Integer>> utxoBlockHeights = new java.util.ArrayList<Tuple<Long, Integer>>(1);
        utxoBlockHeights.add(new Tuple<Long, Integer>(null, maxUtxoPerBatch)); // Search for rows without block_heights first (and only once)...
        do {
            // TODO: When committing UTXOs, have them sorted before inserting to disk...
            { // Populate blockHeights...
                final java.util.List<Row> rows = nonTransactionalDatabaseConnection.query(
                    new Query("SELECT block_height, COUNT(*) AS utxo_count FROM unspent_transaction_outputs WHERE block_height <= ? GROUP BY block_height ORDER BY block_height DESC LIMIT 1024")
                        .setParameter(blockHeight)
                );
                utxoBlockHeights.ensureCapacity(utxoBlockHeights.size() + rows.size()); // NOTE: Usually 1024 + 1 (for the initial null row).
                for (final Row row : rows) {
                    final Long utxoBlockHeight = row.getLong("block_height");
                    final Integer utxoCount = row.getInteger("utxo_count");
                    utxoBlockHeights.add(new Tuple<Long, Integer>(utxoBlockHeight, utxoCount));
                }
            }

            if (utxoBlockHeights.isEmpty()) {
                break;
            }

            for (int i = 0; i < utxoBlockHeights.size(); ++i) {
                final boolean blockHeightsContainsNull;
                final List<Long> blockHeights;
                {
                    boolean nullValueExists = false;
                    final ImmutableListBuilder<Long> blockHeightsBuilder = new ImmutableListBuilder<Long>();

                    int totalUtxoCount = 0;
                    int lookAheadCount = 0;
                    while ( (totalUtxoCount < maxUtxoPerBatch) && ((i + lookAheadCount) < utxoBlockHeights.size()) ) {
                        final Tuple<Long, Integer> utxoBlockHeightsTuple = utxoBlockHeights.get(i + lookAheadCount);

                        // NOTE: blockHeight may be null only if the UTXO was not in the in-memory cache when it was marked as spent...
                        final Long nullableBlockHeight = utxoBlockHeightsTuple.first;
                        final Integer utxoCount = utxoBlockHeightsTuple.second;

                        if ( (blockHeightsBuilder.getCount() > 0) && ((totalUtxoCount + utxoCount) >= maxUtxoPerBatch) ) { break; }

                        if (nullableBlockHeight == null) {
                            nullValueExists = true;
                        }

                        blockHeightsBuilder.add(nullableBlockHeight);
                        totalUtxoCount += utxoCount;
                        lookAheadCount += 1;
                    }
                    i += (lookAheadCount > 0 ? (lookAheadCount - 1) : 0);

                    blockHeights = blockHeightsBuilder.build();
                    blockHeightsContainsNull = nullValueExists;
                }

                final Query query;
                { // Create the query, accounting for a possibly-null blockHeight...
                    query = new Query("SELECT transaction_hash, `index`, is_spent, block_height FROM unspent_transaction_outputs WHERE block_height IN (?)" + (blockHeightsContainsNull ? " OR block_height IS NULL" : ""));
                    query.setInClauseParameters(blockHeights, ValueExtractor.LONG);
                }

                final java.util.List<Row> rows = nonTransactionalDatabaseConnection.query(query);

                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");
                    final Integer isSpent = row.getInteger("is_spent"); // NOTE: May be null if isSpent is null.
                    final Long nullableBlockHeight = row.getLong("block_height");

                    // is_spent may have 4 possible values:
                    //  null    - the UTXO has NOT been spent and it HAS been synchronized to disk
                    //  0       - the UTXO has NOT been spent and it has NOT ever been synchronized to disk
                    //  1       - the UTXO HAS been spent and it has NOT ever been synchronized to disk
                    //  2       - the UTXO HAS been spent and its old (unspent) state HAD been synchronized to disk
                    // Therefore, when is_spent is null or 2, a physical write to disk must occur to mark the UTXO as spent

                    final UnspentTransactionOutput spendableTransactionOutput = new UnspentTransactionOutput(transactionHash, outputIndex, nullableBlockHeight);

                    if (SpentState.wasSpent(isSpent)) {
                        final boolean wasCommittedToDisk = SpentState.wasCommittedToDisk(isSpent);
                        if (wasCommittedToDisk) {
                            final TransactionOutputIdentifier unspentTransactionOutput = new TransactionOutputIdentifier(transactionHash, outputIndex);
                            onDiskUtxoDeleteBatchRunner.addItem(unspentTransactionOutput);
                        }
                    }
                    else {
                        // The UTXO has not been spent...
                        if (isSpent != null) {
                            // The UTXO is new and has not been committed...
                            onDiskUtxoInsertBatchRunner.addItem(spendableTransactionOutput);
                        }

                        if (keepCount < maxKeepCount) {
                            // Retain some UTXOs in the cache, based on age...
                            inMemoryUtxoRetainerBatchRunner.addItem(spendableTransactionOutput);
                            keepCount += 1L;
                        }
                    }

                    { // Throttle iteration so prevent BatchRunner from consuming too much memory...
                        final int maxItemCount = (maxUtxoPerBatch * 32);

                        try {
                            inMemoryUtxoRetainerBatchRunner.waitForQueueCapacity(maxItemCount);
                            onDiskUtxoDeleteBatchRunner.waitForQueueCapacity(maxItemCount);
                            onDiskUtxoPostDeleteBatchRunner.waitForQueueCapacity(maxItemCount);
                            onDiskUtxoInsertBatchRunner.waitForQueueCapacity(maxItemCount);
                        }
                        catch (final InterruptedException exception) {
                            throw new DatabaseException(exception);
                        }
                    }
                }

                final Long firstBlockHeight = blockHeights.get(0);
                final Long lastBlockHeight = blockHeights.get(blockHeights.getCount() - 1);

                // Logger.trace(rows.size() + " UTXOs, blockHeight=[" + firstBlockHeight + "," + lastBlockHeight + "], spent=" + spentCount + ", kept=" + keepCount);

                blockHeight = Util.coalesce(lastBlockHeight, Long.MIN_VALUE); // Only the first row should be null.  If no other rows follow, then MIN_VALUE causes the outer loop to exit.
            }

            utxoBlockHeights.clear();

        } while (blockHeight > minUtxoBlockHeight);

        long rotateTablesExecutionTime = 0L;
        final MilliTimer rotateTablesTimer = new MilliTimer();

        rotateTablesTimer.start();
        nonTransactionalDatabaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs")
        );
        rotateTablesTimer.stop();
        rotateTablesExecutionTime += rotateTablesTimer.getMillisecondsElapsed();

        inMemoryUtxoRetainerBatchRunner.finish();
        onDiskUtxoDeleteBatchRunner.finish();
        onDiskUtxoPostDeleteBatchRunner.finish();
        onDiskUtxoInsertBatchRunner.finish();

        try {
            inMemoryUtxoRetainerBatchRunner.waitUntilFinished();
            onDiskUtxoDeleteBatchRunner.waitUntilFinished();
            onDiskUtxoPostDeleteBatchRunner.waitUntilFinished();
            onDiskUtxoInsertBatchRunner.waitUntilFinished();
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }

        rotateTablesTimer.start();
        nonTransactionalDatabaseConnection.executeSql(
            new Query("INSERT INTO unspent_transaction_outputs SELECT * FROM unspent_transaction_outputs_buffer")
        );
        nonTransactionalDatabaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs_buffer")
        );
        rotateTablesTimer.stop();
        rotateTablesExecutionTime += rotateTablesTimer.getMillisecondsElapsed();

        // Save the committed set's block height...
        final Long oldCommittedBlockHeight = _getCommittedUnspentTransactionOutputBlockHeight(transactionalDatabaseConnection);
        final Long newCommittedBlockHeight = Math.max(maxUtxoBlockHeight, oldCommittedBlockHeight);
        transactionalDatabaseConnection.executeSql(
            new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                .setParameter(COMMITTED_UTXO_BLOCK_HEIGHT_KEY)
                .setParameter(newCommittedBlockHeight)
        );

        Logger.info(
            "Commit Utxo Timer:\n" +
            "    Commit inMemoryUtxoRetainerBatchRunner=" + inMemoryUtxoRetainerBatchRunner.getTotalItemCount() + " in " + inMemoryUtxoRetainerBatchRunner.getExecutionTime() + "ms\n" +
            "    Commit onDiskUtxoDeleteBatchRunner=" + onDiskUtxoDeleteBatchRunner.getTotalItemCount() + " in " + onDiskUtxoDeleteBatchRunner.getExecutionTime() + "ms\n" +
            "    Commit onDiskUtxoPostDeleteBatchRunner=" + onDiskUtxoPostDeleteBatchRunner.getTotalItemCount() + " in " + onDiskUtxoPostDeleteBatchRunner.getExecutionTime() + "ms\n" +
            "    Commit onDiskUtxoInsertBatchRunner=" + onDiskUtxoInsertBatchRunner.getTotalItemCount() + " in " + onDiskUtxoInsertBatchRunner.getExecutionTime() + "ms\n" +
            "    Commit rotateTablesTimer=" + rotateTablesExecutionTime + "ms"
        );
    }

    protected void _clearUncommittedUtxoSet() throws DatabaseException {
        UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = 0L;

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs")
        );
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

    public UnspentTransactionOutputDatabaseManager(final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final MasterInflater masterInflater) {
        this(DEFAULT_MAX_UTXO_CACHE_COUNT, DEFAULT_PURGE_PERCENT, databaseManager, blockStore, masterInflater);
    }

    public UnspentTransactionOutputDatabaseManager(final Long maxUtxoCount, final Float purgePercent, final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final MasterInflater masterInflater) {
        _maxUtxoCount = maxUtxoCount;
        _purgePercent = purgePercent;
        _databaseManager = databaseManager;
        _masterInflater = masterInflater;
        _blockStore = blockStore;
    }

    public void markTransactionOutputsAsSpent(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException {
        if (spentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024, false);
            batchRunner.run(spentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    // is_spent is originally:
                    //  non-existent if it was synchronized and purged from the cache
                    //  null if it was unspent and has been synchronized to disk already
                    //  0 if it is new (unspent) and requires synchronization
                    //  1 if it was new and been marked as spent already (but not synchronized to disk) (shouldn't happen - UTXOs shouldn't be marked as spent multiple times)
                    //  2 if it has already been marked as spent and was previously synchronized to disk (shouldn't happen - UTXOs shouldn't be marked as spent multiple times)
                    //
                    // Therefore, during a commit, when:
                    //  is_spent=null, it has already been synchronized and is unspent
                    //  is_spent=1, it has been spent but was never committed to disk
                    //  is_spent=2, it must be physically deleted from disk
                    //
                    // INSERT...VALUES (?, ?, NULL, 2) ON DUPLICATE KEY UPDATE is_spent = (IF(COALESCE(is_spent, 2) >= 2, 2, 1)) renders:
                    //  non-existent -> 2
                    //  null -> 2
                    //  0 -> 1
                    //  1 -> 1
                    //  2 -> 2
                    //  2+ -> 2 (should never happen)

                    final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, NULL, 2) ON DUPLICATE KEY UPDATE is_spent = (IF(COALESCE(is_spent, 2) >= 2, 2, 1))");
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                        query.setParameter(transactionHash);
                        query.setParameter(outputIndex);

                        // Logger.trace("DELETE UTXO: " + transactionOutputIdentifier + " is_spent=?, block_height=NULL");
                    }

                    databaseConnection.executeSql(query);
                }
            });
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public void insertUnspentTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException {
        if (unspentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024, false);
            batchRunner.run(unspentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, ?, 0) ON DUPLICATE KEY UPDATE is_spent = 0"); // INSERT/UPDATE is necessary to account for duplicate transactions E3BF3D07D4B0375638D5F1DB5255FE07BA2C4CB067CD81B84EE974B6585FB468 and D5D27987D2A3DFC724E359870C6644B40E497BDC0589A033220FE15429D88599.
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                        query.setParameter(transactionHash);
                        query.setParameter(outputIndex);
                        query.setParameter(blockHeight);

                        // Logger.trace("NEW UTXO: " + transactionOutputIdentifier + " is_spent=0, block_height=" + blockHeight);
                    }

                    databaseConnection.executeSql(query);
                }
            });
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    /**
     * Marks the provided UTXOs as spent, logically removing them from the UTXO set, and forces the outputs to be synchronized to disk on the next UTXO commit.
     */
    public void undoCreatedTransactionOutputs(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers) throws DatabaseException {
        if (spentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024, false);
            batchRunner.run(spentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, NULL, 2) ON DUPLICATE KEY UPDATE is_spent = 2");
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                        query.setParameter(transactionHash);
                        query.setParameter(outputIndex);

                        Logger.trace("DELETE UTXO: " + transactionOutputIdentifier + " is_spent=2, block_height=NULL");
                    }

                    databaseConnection.executeSql(query);
                }
            });
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    /**
     * Re-inserts the provided UTXOs into the UTXO set and looks up their original associated blockHeight.  These UTXOs will be synchronized to disk during the next UTXO commit.
     */
    public void undoPreviousTransactionOutputs(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers) throws DatabaseException {
        if (unspentTransactionOutputIdentifiers.isEmpty()) { return; }

        UTXO_WRITE_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024, false);
            batchRunner.run(unspentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, ?, 0) ON DUPLICATE KEY UPDATE is_spent = 0");
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                        final Long blockHeight;
                        { // Lookup the UTXO's block height...
                            final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
                            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
                        }
                        if (blockHeight == null) { continue; } // Omit the UTXO since it wasn't added on the new main chain...

                        query.setParameter(transactionHash);
                        query.setParameter(outputIndex);
                        query.setParameter(blockHeight);

                        Logger.trace("NEW UTXO: " + transactionOutputIdentifier + " is_spent=0, block_height=" + blockHeight);
                    }

                    databaseConnection.executeSql(query);
                }
            });
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        UTXO_READ_MUTEX.lock();
        try { // Ensure the output is in the UTXO set...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT is_spent FROM unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ? LIMIT 1")
                    .setParameter(transactionHash)
                    .setParameter(outputIndex)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Integer isSpent = row.getInteger("is_spent");
                if (SpentState.wasSpent(isSpent)) { return null; }
            }
            else {
                rows.addAll(databaseConnection.query(
                    new Query("SELECT is_spent FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ? LIMIT 1")
                        .setParameter(transactionHash)
                        .setParameter(outputIndex)
                ));

                if (rows.isEmpty()) { return null; }

                final Row row = rows.get(0);
                final Integer isSpent = row.getInteger("is_spent");
                if (SpentState.wasSpent(isSpent)) { return null; }
            }
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

    public List<TransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (transactionOutputIdentifiers.isEmpty()) { return new MutableList<TransactionOutput>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final int transactionOutputIdentifierCount = transactionOutputIdentifiers.getCount();

        final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiersNotInCache;
        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers;

        final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(512, false);

        UTXO_READ_MUTEX.lock();
        try {
            { // Only return outputs that are in the UTXO set...
                final HashSet<TransactionOutputIdentifier> cacheMissIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);

                final java.util.List<Row> rows = new ArrayList<Row>(0);
                batchRunner.run(transactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                    @Override
                    public void run(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws Exception {
                        rows.addAll(databaseConnection.query(
                            new Query("SELECT transaction_hash, `index`, is_spent FROM unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?)")
                                .setInClauseParameters(transactionOutputIdentifiers, new ValueExtractor<TransactionOutputIdentifier>() {
                                    @Override
                                    public InClauseParameter extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
                                        cacheMissIdentifiers.add(transactionOutputIdentifier);
                                        return ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER.extractValues(transactionOutputIdentifier);
                                    }
                                })
                        ));
                    }
                });

                unspentTransactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);
                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");
                    final Integer isSpent = row.getInteger("is_spent");

                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                    if (SpentState.isUnspent(isSpent)) {
                        unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                    }

                    cacheMissIdentifiers.remove(transactionOutputIdentifier);
                }

                unspentTransactionOutputIdentifiersNotInCache = new ImmutableList<TransactionOutputIdentifier>(cacheMissIdentifiers);
            }
            { // Load UTXOs that weren't in the memory-cache but are in the greater UTXO set on disk...
                final int cacheMissCount = unspentTransactionOutputIdentifiersNotInCache.getCount();
                if (cacheMissCount > 0) {
                    final java.util.List<Row> rows = new ArrayList<Row>(0);
                    batchRunner.run(unspentTransactionOutputIdentifiersNotInCache, new BatchRunner.Batch<TransactionOutputIdentifier>() {
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
            final Transaction transaction = ConstUtil.asConstOrNull(transactionInflater.fromBytes(transactionData)); // To ensure Transaction::getGash is constant-time...
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
            if (outputIndex >= transactionOutputs.getCount()) {
                transactionOutputsBuilder.add(null);
                return null;
            }

            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            transactionOutputsBuilder.add(transactionOutput);
        }

        return transactionOutputsBuilder.build();
    }

    /**
     * Flushes all queued UTXO set changes to disk.  The UTXO set is locked for the duration of this call.
     */
    public void commitUnspentTransactionOutputs(final DatabaseConnectionFactory databaseConnectionFactory) throws DatabaseException {
        if (UNCOMMITTED_UTXO_BLOCK_HEIGHT.value == 0L) {
            // Prevent committing a UTXO set that has been invalidated...
            return;
        }

        UTXO_WRITE_MUTEX.lock();
        try (final DatabaseConnection nonTransactionalDatabaseConnection = databaseConnectionFactory.newConnection()) {
            _commitUnspentTransactionOutputs(_maxUtxoCount, _purgePercent, _databaseManager, nonTransactionalDatabaseConnection, databaseConnectionFactory);
        }
        catch (final Exception exception) {
            _clearUncommittedUtxoSetAndRethrow(exception);
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public Long getCachedUnspentTransactionOutputCount() throws DatabaseException {
        UTXO_READ_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS row_count FROM unspent_transaction_outputs")
            );
            final Row row = rows.get(0);
            return row.getLong("row_count");
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }
    }

    public Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException {
        UTXO_READ_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS row_count FROM unspent_transaction_outputs WHERE is_spent IS NOT NULL")
            );
            final Row row = rows.get(0);
            return row.getLong("row_count");
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }
    }

    public Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        UTXO_READ_MUTEX.lock();
        try {
            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            return _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }
    }

    public void setUncommittedUnspentTransactionOutputBlockHeight(final Long blockHeight) throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            UNCOMMITTED_UTXO_BLOCK_HEIGHT.value = blockHeight;
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public Long getUncommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        UTXO_READ_MUTEX.lock();
        try {

            final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
            final Long committedUtxoBlockHeight = _getCommittedUnspentTransactionOutputBlockHeight(databaseConnection);
            return Math.max(UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, committedUtxoBlockHeight);
        }
        finally {
            UTXO_READ_MUTEX.unlock();
        }
    }

    public void clearCommittedUtxoSet() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
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

    public void clearUncommittedUtxoSet() throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();
        try {
            _clearUncommittedUtxoSet();
        }
        finally {
            UTXO_WRITE_MUTEX.unlock();
        }
    }

    public Long getMaxUtxoCount() {
        return _maxUtxoCount;
    }
}

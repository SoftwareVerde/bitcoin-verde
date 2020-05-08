package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UnspentTransactionOutputDatabaseManager {
    public static final ReentrantReadWriteLock.ReadLock UTXO_READ_MUTEX;
    public static final ReentrantReadWriteLock.WriteLock UTXO_WRITE_MUTEX;
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
    public static final Long BYTES_PER_UTXO = 109L;
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

    protected static void _commitUnspentTransactionOutputs(final Long maxUtxoCount, final Float purgePercent, final DatabaseConnection transactionalDatabaseConnection, final DatabaseConnection nonTransactionalDatabaseConnection, final DatabaseConnectionFactory nonTransactionalDatabaseConnectionFactory) throws DatabaseException {
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

        final BlockingQueueBatchRunner<UnspentTransactionOutput> inMemoryUtxoRetainerBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO unspent_transaction_outputs_buffer (transaction_hash, `index`, is_spent, block_height) VALUES (?, ?, NULL, ?)");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getBlockHeight());
                }

                synchronized (nonTransactionalDatabaseConnection) {
                    nonTransactionalDatabaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });
        final BlockingQueueBatchRunner<UnspentTransactionOutput> onDiskUtxoDeleteBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> unspentTransactionOutputs) throws Exception {
                final Query query = new Query("DELETE FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?)");
                query.setExpandedInClauseParameters(unspentTransactionOutputs, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);

                synchronized (transactionalDatabaseConnection) {
                    transactionalDatabaseConnection.executeSql(query);
                }
            }
        });
        final BlockingQueueBatchRunner<UnspentTransactionOutput> onDiskUtxoInsertBatchRunner = BlockingQueueBatchRunner.newInstance(maxUtxoPerBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
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
        onDiskUtxoInsertBatchRunner.start();

        long keepCount = 0L;
        Long blockHeight = maxUtxoBlockHeight;
        final java.util.ArrayList<Tuple<Long, Integer>> utxoBlockHeights = new java.util.ArrayList<Tuple<Long, Integer>>(1);
        utxoBlockHeights.add(new Tuple<Long, Integer>(null, maxUtxoPerBatch)); // Search for rows without block_heights first (and only once)...
        do {
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
                    final Boolean nullableIsSpent = row.getBoolean("is_spent");
                    final Long nullableBlockHeight = row.getLong("block_height");

                    final UnspentTransactionOutput spendableTransactionOutput = new UnspentTransactionOutput(transactionHash, outputIndex, nullableBlockHeight);

                    if (Util.coalesce(nullableIsSpent, false)) {
                        // The UTXO was spent since the last commit...
                        // TODO: Is there a way to avoid the delete if we know the UTXO was never committed to disk?
                        onDiskUtxoDeleteBatchRunner.addItem(spendableTransactionOutput);
                    }
                    else {
                        // The UTXO has not been spent...
                        if (nullableIsSpent != null) {
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
        onDiskUtxoInsertBatchRunner.finish();

        try {
            inMemoryUtxoRetainerBatchRunner.waitUntilFinished();
            onDiskUtxoDeleteBatchRunner.waitUntilFinished();
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

        Logger.info("Commit Utxo Timer: inMemoryUtxoRetainerBatchRunner=" + inMemoryUtxoRetainerBatchRunner.getExecutionTime() + "ms, onDiskUtxoDeleteBatchRunner=" + onDiskUtxoDeleteBatchRunner.getExecutionTime() + "ms, onDiskUtxoInsertBatchRunner=" + onDiskUtxoInsertBatchRunner.getExecutionTime() + "ms, rotateTablesTimer=" + rotateTablesExecutionTime + "ms");
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

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024);
            batchRunner.run(spentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
                @Override
                public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                    final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, NULL, 1) ON DUPLICATE KEY UPDATE is_spent = 1");
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : batchItems) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

                        query.setParameter(transactionHash);
                        query.setParameter(outputIndex);
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

            final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024);
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
                final Boolean isSpent = Util.coalesce(row.getBoolean("is_spent"), false); // Both null and false are indicative of being unspent...
                if (isSpent) { return null; }
            }
            else {
                rows.addAll(databaseConnection.query(
                    new Query("SELECT 1 FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ? LIMIT 1")
                        .setParameter(transactionHash)
                        .setParameter(outputIndex)
                ));

                if (rows.isEmpty()) {
                    return null;
                }
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

        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiersNotInCache;
        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers;

        UTXO_READ_MUTEX.lock();
        try {
            { // Only return outputs that are in the UTXO set...
                unspentTransactionOutputIdentifiersNotInCache = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);

                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT transaction_hash, `index`, is_spent FROM unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?)")
                        .setInClauseParameters(transactionOutputIdentifiers, new ValueExtractor<TransactionOutputIdentifier>() {
                            @Override
                            public InClauseParameter extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
                                unspentTransactionOutputIdentifiersNotInCache.add(transactionOutputIdentifier);
                                return ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER.extractValues(transactionOutputIdentifier);
                            }
                        })
                );

                unspentTransactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>(transactionOutputIdentifierCount);
                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");
                    final Boolean isSpent = Util.coalesce(row.getBoolean("is_spent"), false); // Both null and false are indicative of being unspent...

                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                    if (! isSpent) {
                        unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                    }

                    unspentTransactionOutputIdentifiersNotInCache.remove(transactionOutputIdentifier);
                }
            }
            { // Load UTXOs that weren't in the memory-cache but are in the greater UTXO set on disk...
                final int cacheMissCount = unspentTransactionOutputIdentifiersNotInCache.size();
                if (cacheMissCount > 0) {
                    final java.util.List<Row> rows = databaseConnection.query(
                        new Query("SELECT transaction_hash, `index` FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?)")
                            .setExpandedInClauseParameters(unspentTransactionOutputIdentifiersNotInCache, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
                    );
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
                })
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
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
            transactionOutputsBuilder.add(transactionOutput);
        }

        return transactionOutputsBuilder.build();
    }

    public void commitUnspentTransactionOutputs(final DatabaseConnectionFactory databaseConnectionFactory) throws DatabaseException {
        UTXO_WRITE_MUTEX.lock();

        if (UNCOMMITTED_UTXO_BLOCK_HEIGHT.value == 0L) {
            // Prevent committing a UTXO set that has been invalidated...
            return;
        }

        try (final DatabaseConnection nonTransactionalDatabaseConnection = databaseConnectionFactory.newConnection()) {
            final DatabaseConnection transactionalDatabaseConnection = _databaseManager.getDatabaseConnection();
            _commitUnspentTransactionOutputs(_maxUtxoCount, _purgePercent, transactionalDatabaseConnection, nonTransactionalDatabaseConnection, databaseConnectionFactory);
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
}

package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.util.IoUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class TransactionDatabaseManager {
    public static final Object BLOCK_TRANSACTIONS_WRITE_MUTEX = new Object();
    // TODO: Inserting a transaction requires a write lock...

    // The EXISTING_TRANSACTIONS_FILTER is used to greatly improve the performance of TransactionDatabaseManager::storeTransactions by reducing the number of queried hashes to determine if a transaction is new...
    protected static final Integer EXISTING_TRANSACTIONS_FILTER_VERSION = 2;
    protected static MutableBloomFilter EXISTING_TRANSACTIONS_FILTER = null;
    protected static Sha256Hash EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = null;
    protected static final Long FILTER_ITEM_COUNT = 500_000_000L;
    protected static final Long FILTER_NONCE = 0L;
    protected static final Double FILTER_FALSE_POSITIVE_RATE = 0.001D;

    // NOTE: This function intentionally does not use IoUtil::getFileContents in order to reduce the initial memory footprint of the node startup...
    protected static MutableBloomFilter _loadBloomFilterFromFile(final String filename, final Long filterItemCount, final Long filterNonce) {
        final File file = new File(filename);
        if (! file.canRead()) { return null; }

        final Long byteCount = file.length();
        if (byteCount == 0L) { return null; }
        if (byteCount > Integer.MAX_VALUE) { return null; }

        try (final FileInputStream fileInputStream = new FileInputStream(file)) {
            final byte[] bytes = new byte[byteCount.intValue()];

            final int readByteCount = fileInputStream.read(bytes, 0, bytes.length);
            if (readByteCount != bytes.length) { return null; }

            final Integer functionCount = MutableBloomFilter.calculateFunctionCount(bytes.length, filterItemCount);
            return MutableBloomFilter.wrap(MutableByteArray.wrap(bytes), functionCount, filterNonce);
        }
        catch (final Exception exception) { return null; }
    }

    public static void initializeBloomFilter(final String filename, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        try {
            final Json json = Json.parse(StringUtil.bytesToString(Util.coalesce(IoUtil.getFileContents(filename + ".json"), new byte[0])));
            final Integer transactionBloomFilterVersion = json.getInteger("version");

            final Sha256Hash filterLastTransactionHash;
            {
                if (Util.areEqual(EXISTING_TRANSACTIONS_FILTER_VERSION, transactionBloomFilterVersion)) {
                    filterLastTransactionHash = Sha256Hash.fromHexString(json.getString("lastTransactionHash"));
                }
                else {
                    filterLastTransactionHash = null;
                }
            }

            if (filterLastTransactionHash != null) {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id, hash FROM transactions ORDER BY id DESC LIMIT 1")
                );
                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    final Sha256Hash lastTransactionHash = Sha256Hash.fromHexString(row.getString("hash"));
                    if (Util.areEqual(lastTransactionHash, filterLastTransactionHash)) {
                        Logger.log("Restoring ExistingTransactionFilter. Last TransactionHash: " + lastTransactionHash);

                        final MutableBloomFilter loadedBloomFilter = _loadBloomFilterFromFile(filename, FILTER_ITEM_COUNT, FILTER_NONCE);
                        if (loadedBloomFilter != null) {
                            EXISTING_TRANSACTIONS_FILTER = loadedBloomFilter;
                            EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = filterLastTransactionHash;
                        }
                    }
                    else {
                        Logger.log("Rebuilding ExistingTransactionFilter. Filter TransactionHash: " + filterLastTransactionHash + ", Database TransactionHash: " + lastTransactionHash);
                    }
                }
            }
            if (EXISTING_TRANSACTIONS_FILTER != null) { return; }

            final MutableBloomFilter mutableBloomFilter = MutableBloomFilter.newInstance(FILTER_ITEM_COUNT, FILTER_FALSE_POSITIVE_RATE, FILTER_NONCE);

            Logger.log("[Building TransactionBloomFilter]");
            final Long batchSize = 4096L;
            long lastTransactionId = 0L;
            Sha256Hash lastTransactionHash = null;
            while (true) {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id, hash FROM transactions WHERE id > ? ORDER BY id ASC LIMIT " + batchSize)
                        .setParameter(lastTransactionId)
                );
                if (rows.isEmpty()) { break; }

                for (final Row row : rows) {
                    final long transactionId = row.getLong("id");
                    final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));
                    mutableBloomFilter.addItem(transactionHash);
                    if (transactionId > lastTransactionId) {
                        lastTransactionId = transactionId;
                    }

                    lastTransactionHash = transactionHash;
                }
            }

            EXISTING_TRANSACTIONS_FILTER = mutableBloomFilter;
            EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = lastTransactionHash;
        }
        catch (final Exception exception) {
            EXISTING_TRANSACTIONS_FILTER = null;
            EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = null;
            throw exception;
        }
    }

    public static void saveBloomFilter(final String filename) {
        final MutableByteArray filterBytes = (EXISTING_TRANSACTIONS_FILTER != null ? EXISTING_TRANSACTIONS_FILTER.unwrap() : new MutableByteArray(0));
        IoUtil.putFileContents(filename, filterBytes.unwrap());

        final ByteArray filterLastTransactionHash = Util.coalesce(EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH, new MutableByteArray(0));
        final Json json = new Json();
        json.put("version", EXISTING_TRANSACTIONS_FILTER_VERSION);
        json.put("lastTransactionHash", filterLastTransactionHash);
        IoUtil.putFileContents(filename + ".json", StringUtil.stringToBytes(json.toString()));
    }

    protected final DatabaseManagerCache _databaseManagerCache;

    protected static final SystemTime _systemTime = new SystemTime();
    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _insertTransactionInputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertTransactionInput(transactionId, transactionInput);
        }
    }

    protected void _insertTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Sha256Hash transactionHash = transaction.getHash();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionHash, transactionOutput);
        }
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     */
    protected TransactionId _getTransactionIdFromHash(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId cachedTransactionId = _databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) { return cachedTransactionId; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected void _updateTransaction(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionIdCache();
        _databaseManagerCache.invalidateTransactionCache();

        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, version = ?, lock_time = ? WHERE id = ?")
                .setParameter(transaction.getHash())
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(transactionId)
        );
    }

    protected Integer _getTransactionCount(final BlockId blockId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM block_transactions WHERE block_id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    protected List<BlockId> _getBlockIds(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, block_id FROM block_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );

        final MutableList<BlockId> blockIds = new MutableList<BlockId>(rows.size());
        for (final Row row : rows) {
            final Long blockId = row.getLong("block_id");
            blockIds.add(BlockId.wrap(blockId));
        }
        return blockIds;
    }

    protected void _insertIntoUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final Long now = _systemTime.getCurrentTimeInSeconds();

        _databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO unconfirmed_transactions (transaction_id, timestamp) VALUES (?, ?)")
                .setParameter(transactionId)
                .setParameter(now)
        );
    }

    protected void _insertIntoUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        if (transactionIds.isEmpty()) { return; }
        final Long now = _systemTime.getCurrentTimeInSeconds();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO unconfirmed_transactions (transaction_id, timestamp) VALUES (?, ?)");
        for (final TransactionId transactionId : transactionIds) {
            batchedInsertQuery.setParameter(transactionId);
            batchedInsertQuery.setParameter(now);
        }

        _databaseConnection.executeSql(batchedInsertQuery);
    }

    protected void _deleteFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
    }

    protected void _deleteFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        if (transactionIds.isEmpty()) { return; }

        _databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transactions WHERE transaction_id IN (" + DatabaseUtil.createInClause(transactionIds) + ")")
        );
    }

    protected TransactionId _insertTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final LockTime lockTime = transaction.getLockTime();
        final Long transactionIdLong = _databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)")
                .setParameter(transactionHash)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
        );

        final TransactionId transactionId = TransactionId.wrap(transactionIdLong);

        _databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
        _databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

        if (EXISTING_TRANSACTIONS_FILTER != null) {
            EXISTING_TRANSACTIONS_FILTER.addItem(transactionHash);
            EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = transactionHash;
        }

        return transactionId;
    }

    /**
     * Returns a map of newly inserted Transactions and their Ids.  If a transaction already existed, its Hash/Id pair are not returned within the map.
     */
    protected Map<Sha256Hash, TransactionId> _storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        if (transactions.isEmpty()) { return new HashMap<Sha256Hash, TransactionId>(0); }

        final Integer transactionCount = transactions.getSize();

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(transactionCount);
        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final LockTime lockTime = transaction.getLockTime();

            batchedInsertQuery.setParameter(transactionHash);
            batchedInsertQuery.setParameter(transaction.getVersion());
            batchedInsertQuery.setParameter(lockTime.getValue());

            transactionHashes.add(transactionHash);
        }

        final Long firstTransactionId = _databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionId == null) {
            Logger.log("NOTICE: Error storing transactions.");
            return null;
        }

        final Integer affectedRowCount = _databaseConnection.getRowsAffectedCount();

        final List<Long> transactionIdRange;
        {
            final ImmutableListBuilder<Long> rowIds = new ImmutableListBuilder<Long>(affectedRowCount);
            for (int i = 0; i < affectedRowCount; ++i) {
                rowIds.add(firstTransactionId + i);
            }
            transactionIdRange = rowIds.build();
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id IN (" + DatabaseUtil.createInClause(transactionIdRange) + ")")
        );
        if (! Util.areEqual(rows.size(), affectedRowCount)) {
            Logger.log("NOTICE: Error storing transactions. Insert mismatch: Got " + rows.size() + ", expected " + affectedRowCount);
            return null;
        }

        final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(affectedRowCount);
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));
            transactionHashMap.put(transactionHash, transactionId);

            _databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
            // _databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

            if (EXISTING_TRANSACTIONS_FILTER != null) {
                EXISTING_TRANSACTIONS_FILTER.addItem(transactionHash);
                EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = transactionHash;
            }
        }

        return transactionHashMap;
    }

    protected Transaction _inflateTransaction(final TransactionId transactionId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        final Transaction cachedTransaction = _databaseManagerCache.getCachedTransaction(transactionId);
        if (cachedTransaction != null) { return cachedTransaction; }

        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long version = row.getLong("version");
        final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));

        final List<TransactionInputId> transactionInputIds;
        final List<TransactionOutputId> transactionOutputIds;

        final ImmutableSha256Hash transactionHash;
        final ImmutableTransaction transaction;
        {
            final MutableTransaction mutableTransaction = new MutableTransaction();

            mutableTransaction.setVersion(version);
            mutableTransaction.setLockTime(lockTime);

            transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);
                mutableTransaction.addTransactionInput(transactionInput);
            }

            transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
            for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
                final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
                mutableTransaction.addTransactionOutput(transactionOutput);
            }

            transaction = mutableTransaction.asConst();
            transactionHash = transaction.getHash();

            { // Validate inflated transaction hash...
                final Sha256Hash expectedTransactionHash = Sha256Hash.fromHexString(row.getString("hash"));
                if (! Util.areEqual(expectedTransactionHash, transactionHash)) {
                    Logger.log("ERROR: Error inflating transaction: " + expectedTransactionHash);
                    return null;
                }
            }
        }

        if (shouldUpdateUnspentOutputCache) {
            for (int i = 0; i < transactionOutputIds.getSize(); ++i) {
                final Integer transactionOutputIndex = i;
                final TransactionOutputId transactionOutputId = transactionOutputIds.get(i);

                _databaseManagerCache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
            }

            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
                _databaseManagerCache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
            }
        }

        _databaseManagerCache.cacheTransactionId(transactionHash, transactionId);
        _databaseManagerCache.cacheTransaction(transactionId, transaction);

        return transaction;
    }

    public TransactionDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final TransactionId cachedTransactionId = _databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) {
            _databaseManagerCache.cacheTransaction(cachedTransactionId, transaction.asConst());
            return cachedTransactionId;
        }

        final TransactionId existingTransactionId = _getTransactionIdFromHash(transactionHash);
        if (existingTransactionId != null) {
            return existingTransactionId;
        }

        final TransactionId transactionId = _insertTransaction(transaction);
        _insertTransactionOutputs(transactionId, transaction);
        _insertTransactionInputs(transactionId, transaction);

        _databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
        _databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

        return transactionId;
    }

    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final Integer transactionCount = transactions.getSize();

        final MilliTimer selectTransactionHashesTimer = new MilliTimer();
        final MilliTimer txHashMapTimer = new MilliTimer();
        final MilliTimer storeTransactionRecordsTimer = new MilliTimer();
        final MilliTimer insertTransactionOutputsTimer = new MilliTimer();
        final MilliTimer insertTransactionInputsTimer = new MilliTimer();

        final List<Sha256Hash> transactionHashes;
        final HashMap<Sha256Hash, Transaction> unseenTransactionMap = new HashMap<Sha256Hash, Transaction>(transactionCount);
        final HashMap<Sha256Hash, TransactionId> existingTransactions = new HashMap<Sha256Hash, TransactionId>(transactionCount);
        {
            txHashMapTimer.start();
            final MutableList<Sha256Hash> possiblySeenTransactionHashes = new MutableList<Sha256Hash>();
            final ImmutableListBuilder<Sha256Hash> transactionHashesBuilder = new ImmutableListBuilder<Sha256Hash>(transactionCount);
            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();
                transactionHashesBuilder.add(transactionHash);

                { // Items matching the bloom filter may be false positives, so mark the matches as "possibly seen", but still assume they're unseen until proven later...
                    if ( (EXISTING_TRANSACTIONS_FILTER == null) || (EXISTING_TRANSACTIONS_FILTER.containsItem(transactionHash)) ) { // If the bloom filter hasn't been loaded, assume all items are new...
                        possiblySeenTransactionHashes.add(transactionHash);
                    }

                    unseenTransactionMap.put(transactionHash, transaction);
                }
            }
            transactionHashes = transactionHashesBuilder.build();

            final Integer positivesCount = possiblySeenTransactionHashes.getSize();
            final Integer falsePositiveCount;
            { // Of the "possibly seen" transactions, prove they've actually been seen...
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id, hash FROM transactions WHERE hash IN (" + DatabaseUtil.createInClause(possiblySeenTransactionHashes) + ")")
                );
                for (final Row row : rows) {
                    final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
                    final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));

                    // The existence of the transaction is confirmed, so definitively mark the transaction as seen...
                    existingTransactions.put(transactionHash, transactionId);
                    unseenTransactionMap.remove(transactionHash);
                }

                falsePositiveCount = (positivesCount - rows.size());
            }
            txHashMapTimer.stop();

            final Float falsePositiveRate = (falsePositiveCount.floatValue() / transactionCount);
            if ( (EXISTING_TRANSACTIONS_FILTER != null) && (falsePositiveRate > FILTER_FALSE_POSITIVE_RATE) ) {
                Logger.log("INFO: TransactionBloomFilter exceeded false positive rate: " + positivesCount + " positives, " + falsePositiveCount + " false positives, " + transactionCount + " transactions, " + falsePositiveRate + " false positive rate.");
            }
        }

        storeTransactionRecordsTimer.start();
        final List<Transaction> unseenTransactions = new MutableList<Transaction>(unseenTransactionMap.values()); // Transactions that are believed to be new...
        final Map<Sha256Hash, TransactionId> newTransactionIds = _storeTransactions(unseenTransactions);
        if (newTransactionIds == null) { return null; }

        // final List<Transaction> newTransactions;
        // if (newTransactionIds.size() < unseenTransactions.getSize()) {
        //     // Some of the Transactions that were attempted to be inserted were already seen.
        //     // Attempting to store their Inputs/Outputs would fail due to duplicates, so they are ignored.
        //     // BloomFilters only give false positives, not false negatives, so this should never happen...
        //     //  Encountering this scenario isn't catastrophic, but does represent a bug in the BloomFilter,
        //     //  or a bug in the code resulting in a Transaction being inserted without being added to the filter.
        //     final ImmutableListBuilder<Transaction> newTransactionsBuilder = new ImmutableListBuilder<Transaction>(newTransactionIds.size());
        //     for (final Transaction transaction : unseenTransactions) {
        //         final Sha256Hash transactionHash = transaction.getHash();
        //         if (newTransactionIds.containsKey(transactionHash)) {
        //             newTransactionsBuilder.add(transaction);
        //         }
        //         else {
        //             Logger.log("NOTICE: TxBloomFilter rendered false negative for: " + transactionHash + ". Recovering.");
        //         }
        //     }
        //     newTransactions = newTransactionsBuilder.build();
        // }
        // else {
        //     newTransactions = unseenTransactions;
        // }

        storeTransactionRecordsTimer.stop();

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);

        insertTransactionOutputsTimer.start();
        // final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.insertTransactionOutputs(newTransactionIds, newTransactions);
        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.insertTransactionOutputs(newTransactionIds, unseenTransactions);
        if (transactionOutputIds == null) { return null; }
        insertTransactionOutputsTimer.stop();

        insertTransactionInputsTimer.start();
        // final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.insertTransactionInputs(newTransactionIds, newTransactions);
        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.insertTransactionInputs(newTransactionIds, unseenTransactions);
        if (transactionInputIds == null) { return  null; }
        insertTransactionInputsTimer.stop();

        for (final Sha256Hash transactionHash : newTransactionIds.keySet()) {
            final TransactionId transactionId = newTransactionIds.get(transactionHash);
            existingTransactions.put(transactionHash, transactionId);
        }

        final MutableList<TransactionId> allTransactionIds = new MutableList<TransactionId>(transactionCount);
        for (final Sha256Hash transactionHash : transactionHashes) {
            final TransactionId transactionId = existingTransactions.get(transactionHash);
            if (transactionId == null) { // Should only happen (rarely) when another thread is attempting to insert the same Transaction at the same time as this thread...
                final TransactionId missingTransactionId = _getTransactionIdFromHash(transactionHash);
                if (missingTransactionId == null) {
                    Logger.log("NOTICE: Error storing Transactions. Missing Transaction: " + transactionHash);
                    return null;
                }

                allTransactionIds.add(missingTransactionId);
            }
            else {
                allTransactionIds.add(transactionId);
            }
        }

        Logger.log("selectTransactionHashesTimer: " + selectTransactionHashesTimer.getMillisecondsElapsed() + "ms");
        Logger.log("txHashMapTimer: " + txHashMapTimer.getMillisecondsElapsed() + "ms");
        Logger.log("storeTransactionRecordsTimer: " + storeTransactionRecordsTimer.getMillisecondsElapsed() + "ms");
        Logger.log("insertTransactionOutputsTimer: " + insertTransactionOutputsTimer.getMillisecondsElapsed() + "ms");
        Logger.log("InsertTransactionInputsTimer: " + insertTransactionInputsTimer.getMillisecondsElapsed() + "ms");

        return allTransactionIds;
    }

    public void associateTransactionToBlock(final TransactionId transactionId, final BlockId blockId) throws DatabaseException {
        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final Integer currentTransactionCount = _getTransactionCount(blockId);
            _databaseConnection.executeSql(
                new Query("INSERT INTO block_transactions (block_id, transaction_id, sort_order) VALUES (?, ?, ?)")
                    .setParameter(blockId)
                    .setParameter(transactionId)
                    .setParameter(currentTransactionCount)
            );
        }
    }

    public void associateTransactionsToBlock(final List<TransactionId> transactionIds, final BlockId blockId) throws DatabaseException {
        synchronized (BLOCK_TRANSACTIONS_WRITE_MUTEX) {
            final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO block_transactions (block_id, transaction_id, sort_order) VALUES (?, ?, ?)");
            int sortOrder = 0;
            for (final TransactionId transactionId : transactionIds) {
                batchedInsertQuery.setParameter(blockId);
                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(sortOrder);
                sortOrder += 1;
            }

            _databaseConnection.executeSql(batchedInsertQuery);
        }
    }

    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(transactionHash);
    }

    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final Transaction cachedTransaction = _databaseManagerCache.getCachedTransaction(transactionId);
        if (cachedTransaction != null) {
            final Sha256Hash transactionHash = cachedTransaction.getHash();
            _databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
            return cachedTransaction.getHash();
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));

        _databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);

        return transactionHash;
    }

    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _inflateTransaction(transactionId, false);
    }

    public Transaction getTransaction(final TransactionId transactionId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        return _inflateTransaction(transactionId, shouldUpdateUnspentOutputCache);
    }

    public Boolean previousOutputsExist(final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId == null) { return false; }
        }

        return true;
    }

    public void addToUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        _insertIntoUnconfirmedTransactions(transactionId);
    }

    public void addToUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        _insertIntoUnconfirmedTransactions(transactionIds);
    }

    public void removeFromUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionId);
    }

    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionIds);
    }

    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        return (! rows.isEmpty());
    }

    public List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT transactions.id FROM transactions INNER JOIN unconfirmed_transactions ON transactions.id = unconfirmed_transactions.transaction_id")
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    public List<TransactionId> getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            // "Select transactions that are unconfirmed that spent an output produced by any of these transactionIds..."
            new Query("SELECT unconfirmed_transactions.transaction_id FROM transaction_outputs INNER JOIN transaction_inputs ON transaction_outputs.id = transaction_inputs.previous_transaction_output_id INNER JOIN unconfirmed_transactions ON transaction_inputs.transaction_id = unconfirmed_transactions.transaction_id WHERE transaction_outputs.transaction_id IN (" + DatabaseUtil.createInClause(transactionIds) + ") GROUP BY unconfirmed_transactions.transaction_id")
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    public Integer getUnconfirmedTransactionCount() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM unconfirmed_transactions")
        );
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    public Integer getTransactionCount(final BlockId blockId) throws DatabaseException {
        return _getTransactionCount(blockId);
    }

    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final List<BlockId> blockIds = _getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
            if (isConnected) {
                return blockId;
            }
        }

        return null;
    }

    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return _getBlockIds(transactionId);
    }

    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionIdFromHash(transactionHash);
        if (transactionId == null) { return new MutableList<BlockId>(); }

        return _getBlockIds(transactionId);
    }

    public void updateTransaction(final Transaction transaction) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionIdCache();
        _databaseManagerCache.invalidateTransactionCache();

        final TransactionId transactionId = _getTransactionIdFromHash(transaction.getHash());

        _updateTransaction(transactionId, transaction);

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);

        { // Process TransactionOutputs....
            final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

            final HashMap<Integer, TransactionOutput> transactionOutputMap = new HashMap<Integer, TransactionOutput>();
            {
                for (final TransactionOutput transactionOutput : transactionOutputs) {
                    transactionOutputMap.put(transactionOutput.getIndex(), transactionOutput);
                }
            }

            final Set<Integer> processedTransactionOutputIndexes = new TreeSet<Integer>();
            for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
                final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);

                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Boolean transactionOutputExistsInUpdatedTransaction = transactionOutputMap.containsKey(transactionOutputIndex);
                if (transactionOutputExistsInUpdatedTransaction) {
                    transactionOutputDatabaseManager.updateTransactionOutput(transactionOutputId, transactionId, transactionOutput);
                    processedTransactionOutputIndexes.add(transactionOutputIndex);
                }
                else {
                    transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
                }
            }

            final Sha256Hash transactionHash = transaction.getHash();
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Boolean transactionOutputHasBeenProcessed = processedTransactionOutputIndexes.contains(transactionOutputIndex);
                if (! transactionOutputHasBeenProcessed) {
                    transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionHash, transactionOutput);
                }
            }
        }

        { // Process TransactionInputs....
            final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            final HashMap<TransactionOutputIdentifier, TransactionInput> transactionInputMap = new HashMap<TransactionOutputIdentifier, TransactionInput>();
            {
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                    transactionInputMap.put(transactionOutputIdentifier, transactionInput);
                }
            }

            final Set<TransactionOutputIdentifier> processedTransactionInputIndexes = new TreeSet<TransactionOutputIdentifier>();
            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputExistsInUpdatedTransaction = transactionInputMap.containsKey(transactionOutputIdentifier);
                if (transactionInputExistsInUpdatedTransaction) {
                    transactionInputDatabaseManager.updateTransactionInput(transactionInputId, transactionId, transactionInput);
                    processedTransactionInputIndexes.add(transactionOutputIdentifier);
                }
                else {
                    transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
                }
            }

            for (final TransactionInput transactionInput : transactionInputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final Boolean transactionInputHasBeenProcessed = processedTransactionInputIndexes.contains(transactionOutputIdentifier);
                if (! transactionInputHasBeenProcessed) {
                    transactionInputDatabaseManager.insertTransactionInput(transactionId, transactionInput);
                }
            }
        }
    }

    public void deleteTransaction(final TransactionId transactionId) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionIdCache();
        _databaseManagerCache.invalidateTransactionCache();

//        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);
//        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
//        for (final TransactionInputId transactionInputId : transactionInputIds) {
//            transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
//        }
//
//        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection, _databaseManagerCache);
//        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
//        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
//            transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
//        }
//
//        _databaseConnection.executeSql(
//            new Query("DELETE FROM block_transactions WHERE transaction_id = ?")
//                .setParameter(transactionId)
//        );

        _databaseConnection.executeSql(
            new Query("DELETE FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
    }
}

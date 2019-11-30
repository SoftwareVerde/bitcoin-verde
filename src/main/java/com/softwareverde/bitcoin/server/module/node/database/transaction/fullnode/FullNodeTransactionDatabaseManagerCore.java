package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
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
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.json.Json;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public class FullNodeTransactionDatabaseManagerCore implements FullNodeTransactionDatabaseManager {
    // TODO: Inserting a transaction requires a write lock...

    // The EXISTING_TRANSACTIONS_FILTER is used to greatly improve the performance of TransactionDatabaseManager::storeTransactions by reducing the number of queried hashes to determine if a transaction is new...
    protected static final Integer EXISTING_TRANSACTIONS_FILTER_VERSION = 2;
    protected static MutableBloomFilter EXISTING_TRANSACTIONS_FILTER = null;
    protected static TransactionId EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID = null;
    protected static Sha256Hash EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = null;
    protected static final Long FILTER_ITEM_COUNT = 500_000_000L;
    protected static final Long FILTER_NONCE = 0L;
    protected static final Double FILTER_FALSE_POSITIVE_RATE = 0.001D;

    protected static final Integer MIN_INPUT_OUTPUT_COUNT_FOR_BATCHING = 4; // The minimum number (inclusive) of inputs/outputs required to trigger batching input/outputs of the individual transaction...

    public static void initializeBloomFilter(final String filename, final DatabaseConnection databaseConnection) throws DatabaseException {
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
                    final Sha256Hash lastTransactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
                    if (Util.areEqual(lastTransactionHash, filterLastTransactionHash)) {
                        Logger.debug("Restoring ExistingTransactionFilter. Last TransactionHash: " + lastTransactionHash);

                        final MutableBloomFilter loadedBloomFilter = _loadBloomFilterFromFile(filename, FILTER_ITEM_COUNT, FILTER_NONCE);
                        if (loadedBloomFilter != null) {
                            EXISTING_TRANSACTIONS_FILTER = loadedBloomFilter;
                            EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = filterLastTransactionHash;
                        }
                    }
                    else {
                        Logger.debug("Rebuilding ExistingTransactionFilter. Filter TransactionHash: " + filterLastTransactionHash + ", Database TransactionHash: " + lastTransactionHash);
                        Logger.flush();
                    }
                }
            }
            if (EXISTING_TRANSACTIONS_FILTER != null) { return; }

            final MutableBloomFilter mutableBloomFilter = MutableBloomFilter.newInstance(FILTER_ITEM_COUNT, FILTER_FALSE_POSITIVE_RATE, FILTER_NONCE);

            Logger.info("[Building TransactionBloomFilter]");
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
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
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
        IoUtil.putFileContents(filename + ".dat", filterBytes.unwrap());

        final ByteArray filterLastTransactionHash = Util.coalesce(EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH, new MutableByteArray(0));
        final Json json = new Json();
        json.put("version", EXISTING_TRANSACTIONS_FILTER_VERSION);
        json.put("lastTransactionHash", filterLastTransactionHash);
        IoUtil.putFileContents(filename + ".json", StringUtil.stringToBytes(json.toString()));
    }

    // NOTE: This function intentionally does not use IoUtil::getFileContents in order to reduce the initial memory footprint of the node startup...
    protected static MutableBloomFilter _loadBloomFilterFromFile(final String filename, final Long filterItemCount, final Long filterNonce) {
        final File file = new File(filename + ".dat");
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

    protected final SystemTime _systemTime = new SystemTime();
    protected final FullNodeDatabaseManager _databaseManager;

    protected void _insertTransactionInputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();

        final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
        if (transactionInputs.getSize() < MIN_INPUT_OUTPUT_COUNT_FOR_BATCHING) {
            int inputIndex = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                transactionInputDatabaseManager.insertTransactionInput(transactionId, inputIndex, transactionInput);
                inputIndex += 1;
            }
        }
        else {
            final Sha256Hash transactionHash = transaction.getHash();
            final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(1);
            transactionHashMap.put(transactionHash, transactionId);

            final MutableList<Transaction> transactionList = new MutableList<Transaction>(1);
            transactionList.add(transaction);

            transactionInputDatabaseManager.insertTransactionInputs(transactionHashMap, transactionList, null);
        }
    }

    protected void _insertTransactionOutputs(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        final Sha256Hash transactionHash = transaction.getHash();

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        if (transactionOutputs.getSize() < MIN_INPUT_OUTPUT_COUNT_FOR_BATCHING) {
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionHash, transactionOutput);
            }
        }
        else {
            final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(1);
            transactionHashMap.put(transactionHash, transactionId);

            final MutableList<Transaction> transactionList = new MutableList<Transaction>(1);
            transactionList.add(transaction);

            transactionOutputDatabaseManager.insertTransactionOutputs(transactionHashMap, transactionList);
        }
    }

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     */
    protected TransactionId _getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        if (transactionHash == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final TransactionId cachedTransactionId = databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) { return cachedTransactionId; }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    protected void _updateTransaction(final Transaction transaction, final Boolean skipMissingOutputs) throws DatabaseException {
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        databaseManagerCache.invalidateTransactionIdCache();
        databaseManagerCache.invalidateTransactionCache();

        final TransactionId transactionId = _getTransactionId(transaction.getHash());

        _updateTransaction(transactionId, transaction);

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
                final boolean transactionOutputExistsInUpdatedTransaction = transactionOutputMap.containsKey(transactionOutputIndex);
                if (transactionOutputExistsInUpdatedTransaction) {
                    transactionOutputDatabaseManager.updateTransactionOutput(transactionOutputId, transactionOutput);
                    processedTransactionOutputIndexes.add(transactionOutputIndex);
                }
                else {
                    transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
                }
            }

            final Sha256Hash transactionHash = transaction.getHash();
            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final boolean transactionOutputHasBeenProcessed = processedTransactionOutputIndexes.contains(transactionOutputIndex);
                if (! transactionOutputHasBeenProcessed) {
                    transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionHash, transactionOutput);
                }
            }
        }

        { // Process TransactionInputs....
            final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();

            final int inputCount = transactionInputs.getSize();
            final HashMap<TransactionOutputIdentifier, Integer> inputIndexes = new HashMap<TransactionOutputIdentifier, Integer>(inputCount);
            final HashMap<TransactionOutputIdentifier, TransactionInput> transactionInputMap = new HashMap<TransactionOutputIdentifier, TransactionInput>(inputCount);
            {
                int inputIndex = 0;
                for (final TransactionInput transactionInput : transactionInputs) {
                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                    transactionInputMap.put(transactionOutputIdentifier, transactionInput);
                    inputIndexes.put(transactionOutputIdentifier, inputIndex);
                    inputIndex += 1;
                }
            }

            final Set<TransactionOutputIdentifier> processedTransactionInputIndexes = new TreeSet<TransactionOutputIdentifier>();
            for (final TransactionInputId transactionInputId : transactionInputIds) {
                final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final boolean transactionInputExistsInUpdatedTransaction = transactionInputMap.containsKey(transactionOutputIdentifier);
                if (transactionInputExistsInUpdatedTransaction) {
                    final Integer inputIndex = inputIndexes.get(transactionOutputIdentifier);
                    transactionInputDatabaseManager.updateTransactionInput(transactionInputId, transactionId, inputIndex, transactionInput);
                    processedTransactionInputIndexes.add(transactionOutputIdentifier);
                }
                else {
                    transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
                }
            }

            for (final TransactionInput transactionInput : transactionInputs) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionInput.getPreviousOutputTransactionHash(), transactionInput.getPreviousOutputIndex());
                final boolean transactionInputHasBeenProcessed = processedTransactionInputIndexes.contains(transactionOutputIdentifier);
                if (! transactionInputHasBeenProcessed) {
                    final Integer inputIndex = inputIndexes.get(transactionOutputIdentifier);
                    transactionInputDatabaseManager.insertTransactionInput(transactionId, inputIndex, transactionInput, skipMissingOutputs);
                }
            }
        }
    }

    protected void _updateTransaction(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionIdCache();
        databaseManagerCache.invalidateTransactionCache();

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final Integer outputCount = transactionOutputs.getSize();

        final LockTime lockTime = transaction.getLockTime();
        databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, version = ?, lock_time = ?, output_count = ? WHERE id = ?")
                .setParameter(transaction.getHash())
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(outputCount)
                .setParameter(transactionId)
        );
    }

    protected List<BlockId> _getBlockIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
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
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long now = _systemTime.getCurrentTimeInSeconds();

        databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO unconfirmed_transactions (transaction_id, timestamp) VALUES (?, ?)")
                .setParameter(transactionId)
                .setParameter(now)
        );
    }

    protected void _insertIntoUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (transactionIds.isEmpty()) { return; }
        final Long now = _systemTime.getCurrentTimeInSeconds();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO unconfirmed_transactions (transaction_id, timestamp) VALUES (?, ?)");
        for (final TransactionId transactionId : transactionIds) {
            batchedInsertQuery.setParameter(transactionId);
            batchedInsertQuery.setParameter(now);
        }

        databaseConnection.executeSql(batchedInsertQuery);
    }

    protected void _deleteFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
    }

    protected void _deleteFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (transactionIds.isEmpty()) { return; }

        databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transactions WHERE transaction_id IN (?)")
                .setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
        );
    }

    protected TransactionId _insertTransaction(final Transaction transaction) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        final Sha256Hash transactionHash = transaction.getHash();
        final Integer outputCount = transactionOutputs.getSize();

        final LockTime lockTime = transaction.getLockTime();
        final Long transactionIdLong = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, version, lock_time, output_count) VALUES (?, ?, ?, ?)")
                .setParameter(transactionHash)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
                .setParameter(outputCount)
        );

        final TransactionId transactionId = TransactionId.wrap(transactionIdLong);

        databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
        databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

        if (EXISTING_TRANSACTIONS_FILTER != null) {
            EXISTING_TRANSACTIONS_FILTER.addItem(transactionHash);

            synchronized (EXISTING_TRANSACTIONS_FILTER) {
                if ( (EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID == null) || (transactionIdLong > EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID.longValue()) ) {
                    EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID = transactionId;
                    EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = transactionHash;
                }
            }
        }

        return transactionId;
    }

    /**
     * Returns a map of newly inserted Transactions and their Ids.  If a transaction already existed, its Hash/Id pair are not returned within the map.
     */
    protected Map<Sha256Hash, TransactionId> _storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        if (transactions.isEmpty()) { return new HashMap<Sha256Hash, TransactionId>(0); }

        final Integer transactionCount = transactions.getSize();

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(transactionCount);
        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO transactions (hash, version, lock_time, output_count) VALUES (?, ?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final Integer transactionOutputCount = transactionOutputs.getSize();
            final Sha256Hash transactionHash = transaction.getHash();
            final LockTime lockTime = transaction.getLockTime();

            batchedInsertQuery.setParameter(transactionHash);
            batchedInsertQuery.setParameter(transaction.getVersion());
            batchedInsertQuery.setParameter(lockTime.getValue());
            batchedInsertQuery.setParameter(transactionOutputCount);

            transactionHashes.add(transactionHash);
        }

        final Long firstTransactionId = databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionId == null) {
            Logger.warn("Error storing transactions.");
            return null;
        }

        final Integer affectedRowCount = databaseConnection.getRowsAffectedCount();

        final List<Long> transactionIdRange;
        {
            final ImmutableListBuilder<Long> rowIds = new ImmutableListBuilder<Long>(affectedRowCount);
            for (int i = 0; i < affectedRowCount; ++i) {
                rowIds.add(firstTransactionId + i);
            }
            transactionIdRange = rowIds.build();
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id IN (?)")
                .setInClauseParameters(transactionIdRange, ValueExtractor.LONG)
        );
        if (! Util.areEqual(rows.size(), affectedRowCount)) {
            Logger.warn("Error storing transactions. Insert mismatch: Got " + rows.size() + ", expected " + affectedRowCount);
            return null;
        }

        final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(affectedRowCount);
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
            transactionHashMap.put(transactionHash, transactionId);

            databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
            // databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

            if (EXISTING_TRANSACTIONS_FILTER != null) {
                EXISTING_TRANSACTIONS_FILTER.addItem(transactionHash);

                synchronized (EXISTING_TRANSACTIONS_FILTER) {
                    if ( (EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID == null) || (transactionId.longValue() > EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID.longValue()) ) {
                        EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_ID = transactionId;
                        EXISTING_TRANSACTIONS_FILTER_LAST_TRANSACTION_HASH = transactionHash;
                    }
                }
            }
        }

        return transactionHashMap;
    }

    protected Transaction _inflateTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();

        final Transaction cachedTransaction = databaseManagerCache.getCachedTransaction(transactionId);
        if (cachedTransaction != null) { return cachedTransaction; }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long version = row.getLong("version");
        final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));
        final Sha256Hash expectedTransactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

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
                if (transactionInput == null) {
                    Logger.warn("Error inflating transaction: " + expectedTransactionHash);
                    return null;
                }

                mutableTransaction.addTransactionInput(transactionInput);
            }

            transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
            for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
                final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
                if (transactionOutput == null) {
                    Logger.warn("Error inflating transaction: " + expectedTransactionHash);
                    return null;
                }

                mutableTransaction.addTransactionOutput(transactionOutput);
            }

            transaction = mutableTransaction.asConst();
            transactionHash = transaction.getHash();

            { // Validate inflated transaction hash...
                if (! Util.areEqual(expectedTransactionHash, transactionHash)) {
                    Logger.warn("Error inflating transaction: " + expectedTransactionHash);
                    return null;
                }
            }
        }

        databaseManagerCache.cacheTransactionId(transactionHash, transactionId);
        databaseManagerCache.cacheTransaction(transactionId, transaction);

        return transaction;
    }

    protected Map<TransactionOutputIdentifier, TransactionOutputId> _buildOutputIdsMap(final List<TransactionOutputId> transactionOutputIds, final List<Transaction> transactions) {
        final HashMap<TransactionOutputIdentifier, TransactionOutputId> transactionOutputIdMap = new HashMap<TransactionOutputIdentifier, TransactionOutputId>(transactionOutputIds.getSize());

        int transactionOutputIdIndex = 0;
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            final int transactionOutputCount = transactionOutputs.getSize();

            if ((transactionOutputIdIndex + transactionOutputCount) > transactionOutputIds.getSize()) {
                Logger.debug("InsertTransactionOutputs Id count mismatch. Expected at least " + (transactionOutputIdIndex + transactionOutputCount) + " ids, but only have " + transactionOutputIds.getSize() + " available.");
                return null;
            }

            for (int i = 0; i < transactionOutputCount; ++i) {
                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, i);
                final TransactionOutputId transactionOutputId = transactionOutputIds.get(transactionOutputIdIndex);

                transactionOutputIdMap.put(transactionOutputIdentifier, transactionOutputId);

                transactionOutputIdIndex += 1;
            }
        }

        return transactionOutputIdMap;
    }

    public FullNodeTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final Sha256Hash transactionHash = transaction.getHash();

        final TransactionId cachedTransactionId = databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) {
            databaseManagerCache.cacheTransaction(cachedTransactionId, transaction.asConst());
            return cachedTransactionId;
        }

        final TransactionId existingTransactionId = _getTransactionId(transactionHash);
        if (existingTransactionId != null) {
            return existingTransactionId;
        }

        final TransactionId transactionId = _insertTransaction(transaction);
        _insertTransactionOutputs(transactionId, transaction);
        _insertTransactionInputs(transactionId, transaction);

        databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
        databaseManagerCache.cacheTransaction(transactionId, transaction.asConst());

        return transactionId;
    }

    @Override
    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();

        final int transactionCount = transactions.getSize();

        final MilliTimer selectTransactionHashesTimer = new MilliTimer();
        final MilliTimer txHashMapTimer = new MilliTimer();
        final MilliTimer storeTransactionRecordsTimer = new MilliTimer();
        final MilliTimer insertTransactionOutputsTimer = new MilliTimer();
        final MilliTimer insertTransactionInputsTimer = new MilliTimer();

        final List<Sha256Hash> transactionHashes;
        final ConcurrentHashMap<Sha256Hash, Transaction> unseenTransactionMap = new ConcurrentHashMap<Sha256Hash, Transaction>(transactionCount);
        final ConcurrentHashMap<Sha256Hash, TransactionId> existingTransactions = new ConcurrentHashMap<Sha256Hash, TransactionId>(transactionCount);
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

            final int positivesCount = possiblySeenTransactionHashes.getSize();
            final AtomicInteger falsePositiveCount = new AtomicInteger(0);
            final BatchRunner<Sha256Hash> batchRunner = new BatchRunner<Sha256Hash>(512);
            batchRunner.run(possiblySeenTransactionHashes, new BatchRunner.Batch<Sha256Hash>() {
                @Override
                public void run(final List<Sha256Hash> batchItems) throws DatabaseException {
                    // Of the "possibly seen" transactions, prove they've actually been seen...
                    final Query query = new Query("SELECT id, hash FROM transactions WHERE hash IN (?)");
                    query.setInClauseParameters(batchItems, ValueExtractor.SHA256_HASH);

                    final java.util.List<Row> rows;
                    final DatabaseConnectionFactory connectionFactory = _databaseManager.getDatabaseConnectionFactory();
                    if (connectionFactory != null) {
                        try (final DatabaseConnection databaseConnection = connectionFactory.newConnection()) {
                            rows = databaseConnection.query(query);
                        }
                    }
                    else {
                        Logger.debug("DatabaseConnectionFactory not set, falling back to synchronous database connection.");
                        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                        rows = databaseConnection.query(query);
                    }

                    for (final Row row : rows) {
                        final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
                        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

                        // The existence of the transaction is confirmed, so definitively mark the transaction as seen...
                        existingTransactions.put(transactionHash, transactionId);
                        unseenTransactionMap.remove(transactionHash);
                    }

                    falsePositiveCount.addAndGet(positivesCount - rows.size());
                }
            });

            txHashMapTimer.stop();

            final float falsePositiveRate = ( ((float) falsePositiveCount.get()) / transactionCount );
            if ( (EXISTING_TRANSACTIONS_FILTER != null) && (falsePositiveRate > FILTER_FALSE_POSITIVE_RATE) ) {
                Logger.debug("TransactionBloomFilter exceeded false positive rate: " + positivesCount + " positives, " + falsePositiveCount + " false positives, " + transactionCount + " transactions, " + falsePositiveRate + " false positive rate.");
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
        //             Logger.warn("TxBloomFilter rendered false negative for: " + transactionHash + ". Recovering.");
        //         }
        //     }
        //     newTransactions = newTransactionsBuilder.build();
        // }
        // else {
        //     newTransactions = unseenTransactions;
        // }

        storeTransactionRecordsTimer.stop();

        insertTransactionOutputsTimer.start();
        // final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.insertTransactionOutputs(newTransactionIds, newTransactions);
        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.insertTransactionOutputs(newTransactionIds, unseenTransactions);
        if (transactionOutputIds == null) { return null; }
        insertTransactionOutputsTimer.stop();

        final Map<TransactionOutputIdentifier, TransactionOutputId> newOutputsFromThisBlock = _buildOutputIdsMap(transactionOutputIds, unseenTransactions);
        if (newOutputsFromThisBlock == null) { return null; }

        insertTransactionInputsTimer.start();
        // final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.insertTransactionInputs(newTransactionIds, newTransactions);
        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.insertTransactionInputs(newTransactionIds, unseenTransactions, newOutputsFromThisBlock);
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
                final TransactionId missingTransactionId = _getTransactionId(transactionHash);
                if (missingTransactionId == null) {
                    Logger.warn("Error storing Transactions. Missing Transaction: " + transactionHash);
                    return null;
                }

                allTransactionIds.add(missingTransactionId);
            }
            else {
                allTransactionIds.add(transactionId);
            }
        }

        Logger.debug("selectTransactionHashesTimer: " + selectTransactionHashesTimer.getMillisecondsElapsed() + "ms");
        Logger.debug("txHashMapTimer: " + txHashMapTimer.getMillisecondsElapsed() + "ms");
        Logger.debug("storeTransactionRecordsTimer: " + storeTransactionRecordsTimer.getMillisecondsElapsed() + "ms");
        Logger.debug("insertTransactionOutputsTimer: " + insertTransactionOutputsTimer.getMillisecondsElapsed() + "ms");
        Logger.debug("InsertTransactionInputsTimer: " + insertTransactionInputsTimer.getMillisecondsElapsed() + "ms");

        return allTransactionIds;
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionId(transactionHash);
    }

    @Override
    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final Transaction cachedTransaction = databaseManagerCache.getCachedTransaction(transactionId);
        if (cachedTransaction != null) {
            final Sha256Hash transactionHash = cachedTransaction.getHash();
            databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);
            return cachedTransaction.getHash();
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

        databaseManagerCache.cacheTransactionId(transactionHash.asConst(), transactionId);

        return transactionHash;
    }

    @Override
    public Map<Sha256Hash, TransactionId> getTransactionIds(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        final DatabaseConnectionFactory databaseConnectionFactory = _databaseManager.getDatabaseConnectionFactory();

        final ConcurrentLinkedDeque<Row> rows = new ConcurrentLinkedDeque<Row>();
        final BatchRunner<Sha256Hash> batchRunner = new BatchRunner<Sha256Hash>(256);
        batchRunner.run(transactionHashes, new BatchRunner.Batch<Sha256Hash>() {
            @Override
            public void run(final List<Sha256Hash> batchItems) throws Exception {
                final Query query = new Query("SELECT id, hash FROM transactions WHERE hash IN (?)");
                query.setInClauseParameters(batchItems, ValueExtractor.SHA256_HASH);

                if (databaseConnectionFactory != null) {
                    try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                        rows.addAll(databaseConnection.query(query));
                    }
                }
                else {
                    final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                    rows.addAll(databaseConnection.query(query));
                }
            }
        });

        final int transactionCount = transactionHashes.getSize();
        if (rows.size() != transactionCount) {
            Logger.debug("Transaction count mismatch. Received " + rows.size() + ", expected " + transactionCount + ".");
            return null;
        }

        final HashMap<Sha256Hash, TransactionId> transactionHashesMap = new HashMap<Sha256Hash, TransactionId>(transactionCount);
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

            transactionHashesMap.put(transactionHash, transactionId);
        }
        return transactionHashesMap;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _inflateTransaction(transactionId);
    }

    @Override
    public Boolean previousOutputsExist(final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputIdentifier transactionOutputIdentifier = TransactionOutputIdentifier.fromTransactionInput(transactionInput);
            final TransactionOutputId transactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(transactionOutputIdentifier);
            if (transactionOutputId == null) { return false; }
        }

        return true;
    }

    @Override
    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        _insertIntoUnconfirmedTransactions(transactionId);
    }

    @Override
    public void addToUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        _insertIntoUnconfirmedTransactions(transactionIds);
    }

    @Override
    public void removeFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionId);
    }

    @Override
    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionIds);
    }

    @Override
    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        return (! rows.isEmpty());
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transactions.id FROM transactions INNER JOIN unconfirmed_transactions ON transactions.id = unconfirmed_transactions.transaction_id")
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    // "Select transactions that are unconfirmed that spend an output spent by any of these transactionIds..."
    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "transaction_inputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON transaction_inputs.transaction_id = unconfirmed_transactions.transaction_id " +
                "WHERE " +
                    "(transaction_inputs.previous_transaction_id, transaction_inputs.previous_transaction_output_index) IN (" +
                        "SELECT previous_transaction_id, previous_transaction_output_index FROM transaction_inputs WHERE transaction_id IN (?)" +
                    ")" +
                "GROUP BY unconfirmed_transactions.transaction_id"
            )
                .setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    // "Select transactions that are unconfirmed that spent an output produced by any of these transactionIds..."
    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "transaction_inputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON transaction_inputs.transaction_id = unconfirmed_transactions.transaction_id " +
                "WHERE " +
                        "transaction_inputs.previous_transaction_id IN (?) " +
                "GROUP BY unconfirmed_transactions.transaction_id"
            )
                .setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    @Override
    public Integer getUnconfirmedTransactionCount() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM unconfirmed_transactions")
        );
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    @Override
    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final List<BlockId> blockIds = _getBlockIds(transactionId);
        for (final BlockId blockId : blockIds) {
            final Boolean isConnected = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
            if (isConnected) {
                return blockId;
            }
        }

        return null;
    }

    @Override
    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return _getBlockIds(transactionId);
    }

    @Override
    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return new MutableList<BlockId>(); }

        return _getBlockIds(transactionId);
    }

    @Override
    public Long calculateTransactionFee(final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();

        Long totalTransactionInputAmount = 0L;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final TransactionOutputId previousTransactionOutputId;
            {
                final Sha256Hash previousOutputTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                if (previousOutputTransactionHash != null) {
                    final TransactionOutputIdentifier previousTransactionOutputIdentifier = new TransactionOutputIdentifier(previousOutputTransactionHash, transactionInput.getPreviousOutputIndex());
                    previousTransactionOutputId = transactionOutputDatabaseManager.findTransactionOutput(previousTransactionOutputIdentifier);
                }
                else {
                    previousTransactionOutputId = null;
                }
            }

            if (previousTransactionOutputId == null) { return null; }

            final TransactionOutput previousTransactionOutput = transactionOutputDatabaseManager.getTransactionOutput(previousTransactionOutputId);
            final Long previousTransactionOutputAmount = ( previousTransactionOutput != null ? previousTransactionOutput.getAmount() : null );

            if (previousTransactionOutputAmount == null) { return null; }

            totalTransactionInputAmount += previousTransactionOutputAmount;
        }

        Long totalTransactionOutputAmount = 0L;
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            totalTransactionOutputAmount += transactionOutput.getAmount();
        }

        return (totalTransactionInputAmount - totalTransactionOutputAmount);
    }

    @Override
    public void updateTransaction(final Transaction transaction) throws DatabaseException {
        _updateTransaction(transaction, false);
    }

    @Override
    public void updateTransaction(final Transaction transaction, final Boolean skipMissingOutputs) throws DatabaseException {
        _updateTransaction(transaction, skipMissingOutputs);
    }

    @Override
    public void deleteTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionIdCache();
        databaseManagerCache.invalidateTransactionCache();

//        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(databaseConnection, databaseManagerCache);
//        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
//        for (final TransactionInputId transactionInputId : transactionInputIds) {
//            transactionInputDatabaseManager.deleteTransactionInput(transactionInputId);
//        }
//
//        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(databaseConnection, databaseManagerCache);
//        final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
//        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
//            transactionOutputDatabaseManager.deleteTransactionOutput(transactionOutputId);
//        }
//
//        databaseConnection.executeSql(
//            new Query("DELETE FROM block_transactions WHERE transaction_id = ?")
//                .setParameter(transactionId)
//        );

        databaseConnection.executeSql(
            new Query("DELETE FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = _databaseManager.getSlpTransactionDatabaseManager();
        return slpTransactionDatabaseManager.getSlpTokenId(transactionId);
    }
}

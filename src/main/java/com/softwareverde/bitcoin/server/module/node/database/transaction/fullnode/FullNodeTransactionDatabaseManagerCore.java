package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.JavaListWrapper;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FullNodeTransactionDatabaseManagerCore implements FullNodeTransactionDatabaseManager {
    protected final SystemTime _systemTime = new SystemTime();
    protected final FullNodeDatabaseManager _databaseManager;
    protected final MasterInflater _masterInflater;
    protected final BlockStore _blockStore;

    /**
     * Returns the transaction that matches the provided transactionHash, or null if one was not found.
     */
    protected TransactionId _getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        if (transactionHash == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(transactionHash)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
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

    protected TransactionId _storeTransactionHash(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Check if the Transaction already exists...
            final TransactionId transactionId = _getTransactionId(transactionHash);
            if (transactionId != null) {
                return transactionId;
            }
        }

        final Integer transactionByteCount = transaction.getByteCount();
        final Long transactionId = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, byte_count) VALUES (?, ?)")
                .setParameter(transactionHash)
                .setParameter(transactionByteCount)
        );

        return TransactionId.wrap(transactionId);
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

        final Sha256Hash transactionHash = transaction.getHash();

        final LockTime lockTime = transaction.getLockTime();
        final Long transactionIdLong = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, version, lock_time) VALUES (?, ?, ?)")
                .setParameter(transactionHash)
                .setParameter(transaction.getVersion())
                .setParameter(lockTime.getValue())
        );

        return TransactionId.wrap(transactionIdLong);
    }

    /**
     * Returns a map of newly inserted Transactions and their Ids.  If a transaction already existed, its Hash/Id pair are not returned within the map.
     */
    protected Map<Sha256Hash, TransactionId> _storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (transactions.isEmpty()) { return new HashMap<Sha256Hash, TransactionId>(0); }

        final int transactionCount = transactions.getCount();

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

        final Long firstTransactionId = databaseConnection.executeSql(batchedInsertQuery);
        if (firstTransactionId == null) {
            Logger.warn("Error storing transactions.");
            return null;
        }

        final Integer affectedRowCount = databaseConnection.getRowsAffectedCount();

        final List<TransactionId> transactionIdRange;
        {
            final ImmutableListBuilder<TransactionId> rowIds = new ImmutableListBuilder<TransactionId>(affectedRowCount);
            for (int i = 0; i < affectedRowCount; ++i) {
                final long transactionIdLong = (firstTransactionId + i);
                rowIds.add(TransactionId.wrap(transactionIdLong));
            }
            transactionIdRange = rowIds.build();
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id IN (?)")
                .setInClauseParameters(transactionIdRange, ValueExtractor.IDENTIFIER)
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
        }

        return transactionHashMap;
    }

    protected void _storeUnconfirmedTransaction(final TransactionId transactionId, final Transaction transaction) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT 1 FROM unconfirmed_transactions WHERE transaction_id = ? LIMIT 1")
                .setParameter(transactionId)
        );
        if (! rows.isEmpty()) { return; }

        final Long version = transaction.getVersion();
        final LockTime lockTime = transaction.getLockTime();
        final Long timestamp = _systemTime.getCurrentTimeInSeconds();

        final Long unconfirmedTransactionId = databaseConnection.executeSql(
            new Query("INSERT INTO unconfirmed_transactions (transaction_id, version, lock_time, timestamp) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(version)
                .setParameter(lockTime.getValue())
                .setParameter(timestamp)
        );

        final UnconfirmedTransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getUnconfirmedTransactionInputDatabaseManager();
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertUnconfirmedTransactionInput(transactionId, transactionInput);
        }

        final UnconfirmedTransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertUnconfirmedTransactionOutput(transactionId, transactionOutput);
        }
    }

    protected Transaction _getTransaction(final TransactionId transactionId, final Boolean allowFromUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Attempt to load the Transaction from a Block on disk...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blocks.hash AS block_hash, blocks.block_height, block_transactions.disk_offset, transactions.byte_count FROM transactions INNER JOIN block_transactions ON transactions.id = block_transactions.transaction_id INNER JOIN blocks ON blocks.id = block_transactions.block_id WHERE transactions.id = ? LIMIT 1")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Sha256Hash blockHash = Sha256Hash.copyOf(row.getBytes("block_hash"));
                final Long blockHeight = row.getLong("block_height");
                final Long diskOffset = row.getLong("disk_offset");
                final Integer byteCount = row.getInteger("byte_count");

                final ByteArray transactionData = _blockStore.readFromBlock(blockHash, blockHeight, diskOffset, byteCount);
                if (transactionData == null) { return null; }

                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                return transactionInflater.fromBytes(transactionData);
            }
        }

        if (allowFromUnconfirmedTransactions) { // Attempt to load the Transaction from the Unconfirmed Transactions table...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT transactions.hash, unconfirmed_transactions.version, unconfirmed_transactions.lock_time FROM unconfirmed_transactions INNER JOIN transactions ON transactions.id = unconfirmed_transactions.transaction_id WHERE transactions.id = ?")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Long version = row.getLong("version");
                final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));
                final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

                final MutableTransaction transaction = new MutableTransaction();
                transaction.setVersion(version);
                transaction.setLockTime(lockTime);

                final UnconfirmedTransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getUnconfirmedTransactionInputDatabaseManager();
                final List<UnconfirmedTransactionInputId> transactionInputIds = transactionInputDatabaseManager.getUnconfirmedTransactionInputIds(transactionId);
                if (transactionInputIds.isEmpty()) { return null; }

                for (final UnconfirmedTransactionInputId transactionInputId : transactionInputIds) {
                    final TransactionInput transactionInput = transactionInputDatabaseManager.getUnconfirmedTransactionInput(transactionInputId);
                    transaction.addTransactionInput(transactionInput);
                }

                final UnconfirmedTransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
                final List<UnconfirmedTransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getUnconfirmedTransactionOutputIds(transactionId);
                if (transactionOutputIds.isEmpty()) { return null; }

                for (final UnconfirmedTransactionOutputId transactionOutputId : transactionOutputIds) {
                    final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getUnconfirmedTransactionOutput(transactionOutputId);
                    transaction.addTransactionOutput(transactionOutput);
                }

                if (! Util.areEqual(transactionHash, transaction.getHash())) { return null; }

                return transaction;
            }
        }

        return null;
    }

    protected List<TransactionId> _getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "WITH RECURSIVE cte (transaction_id, previous_transaction_hash) AS ( " +
                    "SELECT " +
                        "unconfirmed_transaction_inputs.transaction_id, " +
                        "previous_transactions.hash AS previous_transaction_hash " +
                    "FROM " +
                        "transactions AS previous_transactions " +
                        "INNER JOIN unconfirmed_transaction_inputs " +
                            "ON unconfirmed_transaction_inputs.previous_transaction_hash = previous_transactions.hash " +
                ")" +
                "SELECT transaction_id FROM transactions INNER JOIN cte ON cte.previous_transaction_hash = transactions.hash WHERE transactions.id IN (?) " +
                "GROUP BY transaction_id"
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

    public FullNodeTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager, final BlockStore blockStore, final MasterInflater masterInflater) {
        _databaseManager = databaseManager;
        _masterInflater = masterInflater;
        _blockStore = blockStore;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _getTransaction(transactionId, true);
    }

    @Override
    public Boolean previousOutputsExist(final Transaction transaction) throws DatabaseException {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionId previousTransactionId = _getTransactionId(previousTransactionHash);
            if (previousTransactionId == null) { return false; }

            final Transaction previousTransaction = _getTransaction(previousTransactionId, true);
            if (previousTransaction == null) { return false; }

            final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
            if (previousOutputIndex >= previousTransactionOutputs.getCount()) { return false; }
        }

        return true;
    }

    @Override
    public TransactionId storeUnconfirmedTransaction(final Transaction transaction) throws DatabaseException {
        final TransactionId transactionId = _storeTransactionHash(transaction);

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        try {
            _storeUnconfirmedTransaction(transactionId, transaction);
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
        return transactionId;
    }

    @Override
    public List<TransactionId> storeUnconfirmedTransactions(final List<Transaction> transactions) throws DatabaseException {

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(transactions.getCount());
        try {
            for (final Transaction transaction : transactions) {
                final TransactionId transactionId = _storeTransactionHash(transaction);
                _storeUnconfirmedTransaction(transactionId, transaction);
                transactionIds.add(transactionId);
            }
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
        return transactionIds;
    }

    @Override
    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final Transaction transaction = _getTransaction(transactionId, true);
        if (transaction == null) {
            throw new DatabaseException("Unable to load transaction: " + transactionId);
        }

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        try {
            _storeUnconfirmedTransaction(transactionId, transaction);
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
    }

    @Override
    public void addToUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionIds.getCount());

        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = _getTransaction(transactionId, true);
            if (transaction == null) {
                throw new DatabaseException("Unable to load transaction: " + transactionId);
            }

            transactions.add(transaction);
        }

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        try {
            for (int i = 0; i < transactions.getCount(); ++i) {
                final TransactionId transactionId = transactionIds.get(i);
                final Transaction transaction = transactions.get(i);

                _storeUnconfirmedTransaction(transactionId, transaction);
            }
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
    }

    @Override
    public void removeFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        try {
            _deleteFromUnconfirmedTransactions(transactionId);
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
    }

    @Override
    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
        try {
            _deleteFromUnconfirmedTransactions(transactionIds);
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
        }
    }

    @Override
    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.lock();
        final java.util.List<Row> rows;
        try {
            rows = databaseConnection.query(
                new Query("SELECT 1 FROM unconfirmed_transactions WHERE transaction_id = ? LIMIT 1")
                    .setParameter(transactionId)
            );
            return (!rows.isEmpty());
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.unlock();
        }
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.lock();
        final java.util.List<Row> rows;
        try {
            rows = databaseConnection.query(
                new Query("SELECT transaction_id FROM unconfirmed_transactions")
            );
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.unlock();
        }

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(final List<Transaction> transactions) throws DatabaseException {
        if (transactions.isEmpty()) { return new MutableList<TransactionId>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final HashSet<TransactionOutputIdentifier> transactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>();
        for (final Transaction transaction : transactions) {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                transactionOutputIdentifiers.add(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
            }
        }

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.lock();
        final java.util.List<Row> rows;
        try {
            rows = databaseConnection.query(
                new Query("SELECT transaction_id FROM unconfirmed_transaction_inputs WHERE (previous_transaction_hash, previous_transaction_output_index) IN (?)")
                    .setExpandedInClauseParameters(transactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
            );
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.unlock();
        }

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            transactionIds.add(transactionId);
        }
        transactionIds.addAll(_getUnconfirmedTransactionsDependingOn(transactionIds));
        return transactionIds;
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.lock();
        try {
            return _getUnconfirmedTransactionsDependingOn(transactionIds);
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.unlock();
        }
    }

    @Override
    public Integer getUnconfirmedTransactionCount() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.lock();
        final java.util.List<Row> rows;
        try {
            rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS transaction_count FROM unconfirmed_transactions")
            );
        }
        finally {
            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_READ_LOCK.unlock();
        }
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    @Override
    public Long calculateTransactionFee(final Transaction transaction) throws DatabaseException {
        long totalInputAmount = 0L;
        {
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            final int transactionInputCount = transactionInputs.getCount();
            final HashMap<Sha256Hash, Transaction> cachedTransactions = new HashMap<Sha256Hash, Transaction>(transactionInputCount / 2);
            for (final TransactionInput transactionInput : transactionInputs) {
                final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();

                final Transaction previousTransaction;
                {
                    final Transaction cachedTransaction = cachedTransactions.get(previousTransactionHash);
                    if (cachedTransaction != null) {
                        previousTransaction = cachedTransaction;
                    }
                    else {
                        final TransactionId previousTransactionId = _getTransactionId(previousTransactionHash);
                        if (previousTransactionId == null) { return null; }

                        previousTransaction = _getTransaction(previousTransactionId, true);
                        if (previousTransaction == null) { return null; }
                        cachedTransactions.put(previousTransactionHash, previousTransaction);
                    }
                }

                final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
                final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
                if (previousTransactionOutputIndex >= previousTransactionOutputs.getCount()) { return null; }

                final TransactionOutput previousTransactionOutput = previousTransactionOutputs.get(previousTransactionOutputIndex);
                totalInputAmount += previousTransactionOutput.getAmount();
            }
        }

        final Long totalOutputValue = transaction.getTotalOutputValue();

        return (totalInputAmount - totalOutputValue);
    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = _databaseManager.getBlockchainIndexerDatabaseManager();
        return blockchainIndexerDatabaseManager.getSlpTokenId(transactionId);
    }

    @Override
    public Boolean isCoinbaseTransaction(final Sha256Hash transactionHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM transactions INNER JOIN block_transactions ON transactions.id = block_transactions.transaction_id WHERE transactions.hash = ? AND block_transactions.`index` = 0")
                .setParameter(transactionHash)
        );
        return (! rows.isEmpty());
    }

    @Override
    public TransactionId storeTransactionHash(final Transaction transaction) throws DatabaseException {
        return _storeTransactionHash(transaction);
    }

    @Override
    public List<TransactionId> storeTransactionHashes(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BatchRunner<Transaction> batchRunner = new BatchRunner<Transaction>(1024);
        batchRunner.run(transactions, new BatchRunner.Batch<Transaction>() {
            @Override
            public void run(final List<Transaction> transactionsBatch) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO transactions (hash, byte_count) VALUES (?, ?)");

                for (final Transaction transaction : transactionsBatch) {
                    final Sha256Hash transactionHash = transaction.getHash();
                    final Integer transactionByteCount = transaction.getByteCount();

                    batchedInsertQuery.setParameter(transactionHash);
                    batchedInsertQuery.setParameter(transactionByteCount);
                }

                databaseConnection.executeSql(batchedInsertQuery);            }
        });

        final ConcurrentHashMap<Sha256Hash, TransactionId> transactionHashMap = new ConcurrentHashMap<Sha256Hash, TransactionId>(transactions.getCount());
        final AtomicInteger rowCount = new AtomicInteger(0);
        batchRunner.run(transactions, new BatchRunner.Batch<Transaction>() {
            @Override
            public void run(final List<Transaction> transactionsBatch) throws Exception {
                final Query query = new Query("SELECT id, hash FROM transactions WHERE hash IN (?)");
                query.setInClauseParameters(transactionsBatch, ValueExtractor.HASHABLE);

                final java.util.List<Row> rows = databaseConnection.query(query);

                for (final Row row : rows) {
                    final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

                    transactionHashMap.put(transactionHash, transactionId);
                }

                rowCount.addAndGet(rows.size());
            }
        });
        if (rowCount.get() != transactions.getCount()) { return null; }

        final ImmutableListBuilder<TransactionId> transactionIds = new ImmutableListBuilder<TransactionId>(transactions.getCount());
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();

            final TransactionId transactionId = transactionHashMap.get(transactionHash);
            transactionIds.add(transactionId);
        }
        return transactionIds.build();
    }

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionId(transactionHash);
    }

    @Override
    public Map<Sha256Hash, TransactionId> getTransactionIds(final List<Sha256Hash> transactionHashes) throws DatabaseException {
        if (transactionHashes.isEmpty()) { return new HashMap<Sha256Hash, TransactionId>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE hash IN (?)")
                .setInClauseParameters(transactionHashes, ValueExtractor.SHA256_HASH)
        );

        final HashMap<Sha256Hash, TransactionId> transactionIds = new HashMap<Sha256Hash, TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));
            transactionIds.put(transactionHash, transactionId);
        }
        return transactionIds;
    }

    @Override
    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.copyOf(row.getBytes("hash"));
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
    public Map<Sha256Hash, BlockId> getBlockIds(final BlockchainSegmentId blockchainSegmentId, final List<Sha256Hash> transactionHashes) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = new ArrayList<Row>();
        final HashSet<BlockchainSegmentId> blockchainSegmentIds = new HashSet<BlockchainSegmentId>();

        final BatchRunner<Sha256Hash> batchRunner = new BatchRunner<Sha256Hash>(1024);
        batchRunner.run(transactionHashes, new BatchRunner.Batch<Sha256Hash>() {
            @Override
            public void run(final List<Sha256Hash> transactionHashes) throws Exception {
                rows.addAll(databaseConnection.query(
                    new Query("SELECT transactions.hash AS transaction_hash, block_transactions.block_id, blocks.blockchain_segment_id FROM block_transactions INNER JOIN blocks ON blocks.id = block_transactions.block_id INNER JOIN transactions ON transactions.id = block_transactions.transaction_id WHERE transactions.hash IN (?)")
                        .setInClauseParameters(transactionHashes, ValueExtractor.SHA256_HASH)
                ));
            }
        });

        for (final Row row : rows) {
            final BlockchainSegmentId rowBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
            blockchainSegmentIds.add(rowBlockchainSegmentId);
        }
        final Map<BlockchainSegmentId, Boolean> connectedBlockchainSegments = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, JavaListWrapper.wrap(blockchainSegmentIds), BlockRelationship.ANY);

        final HashMap<Sha256Hash, BlockId> transactionBlockIds = new HashMap<Sha256Hash, BlockId>();
        for (final Row row : rows) {
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
            final BlockId blockId = BlockId.wrap(row.getLong("block_id"));
            final BlockchainSegmentId rowBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));

            if (Util.coalesce(connectedBlockchainSegments.get(rowBlockchainSegmentId), false)) {
                transactionBlockIds.put(transactionHash, blockId);
            }
        }
        return transactionBlockIds;
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
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final Transaction transaction = _getTransaction(transactionId, true);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        if (outputIndex >= transactionOutputs.getCount()) { return null; }

        return transactionOutputs.get(outputIndex);
    }

    @Override
    public TransactionOutput getUnspentTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        return unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public List<TransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        return unspentTransactionOutputDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
    }
}

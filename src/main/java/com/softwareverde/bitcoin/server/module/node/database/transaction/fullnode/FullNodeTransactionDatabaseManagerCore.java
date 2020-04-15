package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

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
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.*;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

        final Integer transactionCount = transactions.getCount();

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
            new Query("SELECT id FROM unconfirmed_transactions WHERE transaction_id = ?")
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
                new Query("SELECT id, hash, version, lock_time FROM unconfirmed_transactions WHERE transaction_id = ?")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final UnconfirmedTransactionId unconfirmedTransactionId = UnconfirmedTransactionId.wrap(row.getLong("id"));
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

    protected Thread _createCommitSubRoutineThread(final Container<Boolean> threadContinueContainer, final BlockingQueue<UnspentTransactionOutput> concurrentBatch, final BatchRunner.Batch<UnspentTransactionOutput> batch) {
        final int batchSize = 1024;
        final BatchRunner<UnspentTransactionOutput> batchRunner = new BatchRunner<UnspentTransactionOutput>(batchSize);

        return new Thread(new Runnable() {
            @Override
            public void run() {
                while (threadContinueContainer.value) {
                    int itemCount = 0;
                    final MutableList<UnspentTransactionOutput> batchedItems = new MutableList<UnspentTransactionOutput>(batchSize);
                    try {
                        while (itemCount < batchSize) {
                            final UnspentTransactionOutput transactionOutputIdentifier = concurrentBatch.take();
                            batchedItems.add(transactionOutputIdentifier);
                            itemCount += 1;
                        }
                    }
                    catch (final InterruptedException exception) {
                        // Continue...
                    }

                    if (! batchedItems.isEmpty()) {
                        try {
                            batchRunner.run(batchedItems, batch);
                        }
                        catch (final DatabaseException exception) {
                            Logger.debug(exception);
                        }
                    }
                }
            }
        });
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
    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final Transaction transaction = _getTransaction(transactionId, true);
        if (transaction == null) {
            throw new DatabaseException("Unable to load transaction: " + transactionId);
        }

        _storeUnconfirmedTransaction(transactionId, transaction);
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

        for (int i = 0; i < transactions.getCount(); ++i) {
            final TransactionId transactionId = transactionIds.get(i);
            final Transaction transaction = transactions.get(i);

            _storeUnconfirmedTransaction(transactionId, transaction);
        }
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
            new Query("SELECT id, transaction_id FROM unconfirmed_transactions")
        );

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

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "unconfirmed_transaction_inputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON unconfirmed_transaction_inputs.unconfirmed_transaction_id = unconfirmed_transactions.id " +
                "WHERE " +
                    "(unconfirmed_transaction_inputs.previous_transaction_hash, unconfirmed_transaction_inputs.previous_transaction_output_index) IN (?) " +
                "GROUP BY unconfirmed_transactions.transaction_id"
            )
                .setInClauseParameters(transactionOutputIdentifiers, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
        );

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOn(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "unconfirmed_transaction_outputs " +
                    "INNER JOIN unconfirmed_transaction_inputs " +
                        "ON unconfirmed_transaction_outputs.id = unconfirmed_transaction_inputs.previous_transaction_output_id " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON unconfirmed_transaction_inputs.transaction_id = unconfirmed_transactions.transaction_id " +
                "WHERE " +
                        "unconfirmed_transaction_outputs.transaction_id IN (?) " +
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
    public Long calculateTransactionFee(final Transaction transaction) throws DatabaseException {
        // TODO: Optimize.

        long totalInputAmount = 0L;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final TransactionId previousTransactionId = _getTransactionId(previousTransactionHash);
            if (previousTransactionId == null) { return null; }

            final Transaction previousTransaction = _getTransaction(previousTransactionId, true);
            if (previousTransaction == null) { return null; }

            final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
            final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();
            if (previousTransactionOutputIndex >= previousTransactionOutputs.getCount()) { return null; }

            final TransactionOutput previousTransactionOutput = previousTransactionOutputs.get(previousTransactionOutputIndex);
            totalInputAmount += previousTransactionOutput.getAmount();
        }

        final Long totalOutputValue = transaction.getTotalOutputValue();

        return (totalInputAmount - totalOutputValue);
    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        return transactionOutputDatabaseManager.getSlpTokenId(transactionId);
    }

    @Override
    public TransactionId storeTransaction(final Transaction transaction) throws DatabaseException {
        final Sha256Hash transactionHash = transaction.getHash();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Check if the Transaction already exists...
            final TransactionId transactionId = _getTransactionId(transactionHash);
            if (transactionId != null) {
                return transactionId;
            }
        }

        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();
        final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);

        final Long transactionId = databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, byte_count) VALUES (?, ?)")
                .setParameter(transactionHash)
                .setParameter(transactionByteCount)
        );

        return TransactionId.wrap(transactionId);
    }

    @Override
    public List<TransactionId> storeTransactions(final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final TransactionDeflater transactionDeflater = _masterInflater.getTransactionDeflater();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO transactions (hash, byte_count) VALUES (?, ?)");

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final Integer transactionByteCount = transactionDeflater.getByteCount(transaction);

            batchedInsertQuery.setParameter(transactionHash);
            batchedInsertQuery.setParameter(transactionByteCount);
        }

        databaseConnection.executeSql(batchedInsertQuery);

        final Query query = new Query("SELECT id, hash FROM transactions WHERE hash IN (?)");
        query.setInClauseParameters(transactions, ValueExtractor.HASHABLE);

        final HashMap<Sha256Hash, TransactionId> transactionHashMap = new HashMap<Sha256Hash, TransactionId>(transactions.getCount());
        final java.util.List<Row> rows = databaseConnection.query(query);
        if (rows.size() != transactions.getCount()) { return null; }

        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("id"));
            final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("hash"));

            transactionHashMap.put(transactionHash, transactionId);
        }

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
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Ensure the output is in the UTXO set...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT is_spent FROM unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
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
                    new Query("SELECT 1 FROM committed_unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
                        .setParameter(transactionHash)
                        .setParameter(outputIndex)
                ));

                if (rows.isEmpty()) {
                    return null;
                }
            }
        }

        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final Transaction transaction = _getTransaction(transactionId, true);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        if (outputIndex >= transactionOutputs.getCount()) { return null; }

        return transactionOutputs.get(outputIndex);
    }

    @Override
    public List<TransactionOutput> getUnspentTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (transactionOutputIdentifiers.isEmpty()) { return new MutableList<TransactionOutput>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final int transactionOutputIdentifierCount = transactionOutputIdentifiers.getCount();

        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiersNotInCache;
        final HashSet<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers;
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
                        .setInClauseParameters(unspentTransactionOutputIdentifiersNotInCache, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER)
                );
                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.copyOf(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");

                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);
                    unspentTransactionOutputIdentifiers.add(transactionOutputIdentifier);
                }
            }
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

    @Override
    public void markTransactionOutputsAsUnspent(final List<TransactionOutputIdentifier> unspentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException {
        if (unspentTransactionOutputIdentifiers.isEmpty()) { return; }

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

    @Override
    public void markTransactionOutputsAsSpent(final List<TransactionOutputIdentifier> spentTransactionOutputIdentifiers, final Long blockHeight) throws DatabaseException {
        if (spentTransactionOutputIdentifiers.isEmpty()) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(1024);
        batchRunner.run(spentTransactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
            @Override
            public void run(final List<TransactionOutputIdentifier> batchItems) throws Exception {
                final Query query = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_hash, `index`, block_height, is_spent) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE is_spent = 1");
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

    static class UnspentTransactionOutput extends TransactionOutputIdentifier {
        public static ValueExtractor<UnspentTransactionOutput> VALUE_EXTRACTOR = new ValueExtractor<UnspentTransactionOutput>() {
            @Override
            public InClauseParameter extractValues(final UnspentTransactionOutput transactionOutputIdentifier) {
                if (transactionOutputIdentifier == null) { return InClauseParameter.NULL; }

                final Long blockHeight = transactionOutputIdentifier.getBlockHeight();
                final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();
                final Integer previousTransactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                final TypedParameter blockHeightTypedParameter = (blockHeight != null ? new TypedParameter(blockHeight) : TypedParameter.NULL);
                final TypedParameter previousTransactionHashTypedParameter = (previousTransactionHash != null ? new TypedParameter(previousTransactionHash.getBytes()) : TypedParameter.NULL);
                final TypedParameter previousTransactionOutputIndexTypedParameter = (previousTransactionOutputIndex != null ? new TypedParameter(previousTransactionOutputIndex) : TypedParameter.NULL);

                return new InClauseParameter(blockHeightTypedParameter, previousTransactionHashTypedParameter, previousTransactionOutputIndexTypedParameter);
            }
        };

        protected final Long _blockHeight;

        public UnspentTransactionOutput(final Sha256Hash transactionHash, final Integer index, final Long blockHeight) {
            super(transactionHash, index);
            _blockHeight = blockHeight;
        }

        public Long getBlockHeight() {
            return _blockHeight;
        }
    }

    @Override
    public void commitUnspentTransactionOutputs(final DatabaseConnectionFactory databaseConnectionFactory) throws DatabaseException {
        final MilliTimer commitUtxoTimerInsert = new MilliTimer();
        final MilliTimer commitUtxoTimerDelete = new MilliTimer();
        final MilliTimer commitUtxoTimerPurge = new MilliTimer();
        final MilliTimer commitUtxoTimerCleanup = new MilliTimer();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Long minUtxoBlockHeight;
        final Long maxUtxoBlockHeight;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COALESCE(MIN(block_height), 0) AS min_block_height, COALESCE(MAX(block_height), 0) AS max_block_height FROM unspent_transaction_outputs")
            );
            final Row row = rows.get(0);
            maxUtxoBlockHeight = row.getLong("max_block_height");
            minUtxoBlockHeight = row.getLong("min_block_height");
        }

        final LinkedBlockingQueue<UnspentTransactionOutput> transactionOutputsDeleteMemoryBatch = new LinkedBlockingQueue<UnspentTransactionOutput>();
        final LinkedBlockingQueue<UnspentTransactionOutput> transactionOutputsDeleteCommittedBatch = new LinkedBlockingQueue<UnspentTransactionOutput>();
        final LinkedBlockingQueue<UnspentTransactionOutput> transactionOutputsCommitBatch = new LinkedBlockingQueue<UnspentTransactionOutput>();

        final Container<Boolean> threadContinueContainer = new Container<Boolean>(true);

        final Thread deleteMemoryUnspentOutputsThread = _createCommitSubRoutineThread(threadContinueContainer, transactionOutputsDeleteMemoryBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {

                    final MutableList<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight = new MutableList<UnspentTransactionOutput>(batchItems.getCount());

                    Query deleteQuery = null;
                    Long lastBlockHeight = null;
                    for (final UnspentTransactionOutput unspentTransactionOutput : batchItems) {
                        final Long blockHeight = unspentTransactionOutput.getBlockHeight();

                        if (deleteQuery == null) {
                            deleteQuery = new Query("DELETE FROM unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?) AND block_height = ?");
                        }
                        else if (! Util.areEqual(blockHeight, lastBlockHeight)) {
                            deleteQuery.setInClauseParameters(unspentTransactionOutputsByBlockHeight, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);
                            deleteQuery.setParameter(lastBlockHeight);

                            databaseConnection.executeSql(deleteQuery);

                            deleteQuery = null;
                            unspentTransactionOutputsByBlockHeight.clear();
                        }

                        unspentTransactionOutputsByBlockHeight.add(unspentTransactionOutput);
                        lastBlockHeight = blockHeight;
                    }

                    if (deleteQuery != null) {
                        deleteQuery.setInClauseParameters(unspentTransactionOutputsByBlockHeight, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);
                        deleteQuery.setParameter(lastBlockHeight);

                        databaseConnection.executeSql(deleteQuery);

                        unspentTransactionOutputsByBlockHeight.clear();
                    }
                }
            }
        });
        deleteMemoryUnspentOutputsThread.start();

        final Thread deleteCommittedUnspentOutputsThread = _createCommitSubRoutineThread(threadContinueContainer, transactionOutputsDeleteCommittedBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                // TODO: Remove code duplication by having the executed batches be guaranteed to be in the same block height...
                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {

                    final MutableList<UnspentTransactionOutput> unspentTransactionOutputsByBlockHeight = new MutableList<UnspentTransactionOutput>(batchItems.getCount());

                    Query deleteQuery = null;
                    Long lastBlockHeight = null;
                    for (final UnspentTransactionOutput unspentTransactionOutput : batchItems) {
                        final Long blockHeight = unspentTransactionOutput.getBlockHeight();

                        if (deleteQuery == null) {
                            deleteQuery = new Query("DELETE FROM committed_unspent_transaction_outputs WHERE (transaction_hash, `index`) IN (?) AND block_height = ?");
                        }
                        else if (! Util.areEqual(blockHeight, lastBlockHeight)) {
                            deleteQuery.setInClauseParameters(unspentTransactionOutputsByBlockHeight, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);
                            deleteQuery.setParameter(lastBlockHeight);

                            databaseConnection.executeSql(deleteQuery);

                            deleteQuery = null;
                            unspentTransactionOutputsByBlockHeight.clear();
                        }

                        unspentTransactionOutputsByBlockHeight.add(unspentTransactionOutput);
                        lastBlockHeight = blockHeight;
                    }

                    if (deleteQuery != null) {
                        deleteQuery.setInClauseParameters(unspentTransactionOutputsByBlockHeight, ValueExtractor.TRANSACTION_OUTPUT_IDENTIFIER);
                        deleteQuery.setParameter(lastBlockHeight);

                        databaseConnection.executeSql(deleteQuery);

                        unspentTransactionOutputsByBlockHeight.clear();
                    }
                }
            }
        });
        deleteCommittedUnspentOutputsThread.start();

        final Thread commitUnspentOutputsThread = _createCommitSubRoutineThread(threadContinueContainer, transactionOutputsCommitBatch, new BatchRunner.Batch<UnspentTransactionOutput>() {
            @Override
            public void run(final List<UnspentTransactionOutput> batchItems) throws Exception {
                final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO committed_unspent_transaction_outputs (block_height, transaction_hash, `index`) VALUES (?, ?, ?)");
                for (final UnspentTransactionOutput transactionOutputIdentifier : batchItems) {
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getBlockHeight());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getTransactionHash());
                    batchedInsertQuery.setParameter(transactionOutputIdentifier.getOutputIndex());
                }

                try (final DatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    databaseConnection.executeSql(batchedInsertQuery);
                }
            }
        });
        commitUnspentOutputsThread.start();

        final long maxKeepCount = (MAX_UTXO_CACHE_COUNT / 2L);

        long keepCount = 0L;
        Long blockHeight = maxUtxoBlockHeight;
        do {
            final java.util.List<Row> blockHeightRows = databaseConnection.query(
                new Query("SELECT block_height FROM unspent_transaction_outputs WHERE block_height <= ? GROUP BY block_height ORDER BY block_height DESC LIMIT 1024")
                    .setParameter(blockHeight)
            );
            for (final Row blockHeightRow : blockHeightRows) {
                blockHeight = blockHeightRow.getLong("block_height");
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT transaction_hash, `index`, is_spent FROM unspent_transaction_outputs WHERE block_height = ?")
                        .setParameter(blockHeight)
                );

                for (final Row row : rows) {
                    final Sha256Hash transactionHash = Sha256Hash.wrap(row.getBytes("transaction_hash"));
                    final Integer outputIndex = row.getInteger("index");
                    final Boolean isSpent = row.getBoolean("is_spent");

                    final UnspentTransactionOutput spendableTransactionOutput = new UnspentTransactionOutput(transactionHash, outputIndex, blockHeight);

                    if (Util.coalesce(isSpent, false)) {
                        transactionOutputsDeleteCommittedBatch.add(spendableTransactionOutput);
                        transactionOutputsDeleteMemoryBatch.add(spendableTransactionOutput);
                    }
                    else {
                        transactionOutputsCommitBatch.add(spendableTransactionOutput);

                        if (keepCount >= maxKeepCount) {
                            transactionOutputsDeleteMemoryBatch.add(spendableTransactionOutput);
                        }
                        else {
                            keepCount += 1L;
                        }
                    }
                }
            }
        } while (blockHeight > minUtxoBlockHeight);

        // Notify the threads that the list-building is complete...
        threadContinueContainer.value = false;
        deleteCommittedUnspentOutputsThread.interrupt();
        deleteMemoryUnspentOutputsThread.interrupt();
        commitUnspentOutputsThread.interrupt();

        try { // Wait for the threads to complete...
            deleteCommittedUnspentOutputsThread.join();
            deleteMemoryUnspentOutputsThread.join();
            commitUnspentOutputsThread.join();
        }
        catch (final InterruptedException exception) {
            final Thread currentThread = Thread.currentThread();
            currentThread.interrupt(); // Continue but retain the interrupted flag...
        }

        // Save the committed set's block height...
        databaseConnection.executeSql(
            new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                .setParameter(UTXO_CACHE_BLOCK_HEIGHT_KEY)
                .setParameter(maxUtxoBlockHeight)
        );

        commitUtxoTimerPurge.start();

        commitUtxoTimerCleanup.start();
        databaseConnection.executeSql(new Query("UPDATE unspent_transaction_outputs SET is_spent = NULL"));
        commitUtxoTimerCleanup.stop();

        Logger.info("Commit Utxo Timer: insert=" + commitUtxoTimerInsert.getMillisecondsElapsed() + "ms, delete=" + commitUtxoTimerDelete.getMillisecondsElapsed() + "ms, purge=" + commitUtxoTimerPurge.getMillisecondsElapsed() + "ms, cleanup=" + commitUtxoTimerCleanup.getMillisecondsElapsed() + "ms");
    }

    @Override
    public Long getUncommittedUnspentTransactionOutputCount() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS row_count FROM unspent_transaction_outputs")
        );
        final Row row = rows.get(0);
        return row.getLong("row_count");
    }

    @Override
    public Long getCommittedUnspentTransactionOutputBlockHeight() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, value FROM properties WHERE `key` = ?")
                .setParameter(UTXO_CACHE_BLOCK_HEIGHT_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return Util.coalesce(row.getLong("value"), 0L);
    }
}

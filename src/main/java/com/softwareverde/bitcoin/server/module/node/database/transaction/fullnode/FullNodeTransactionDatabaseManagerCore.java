package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.BlockCache;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.UnconfirmedTransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
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
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class FullNodeTransactionDatabaseManagerCore implements FullNodeTransactionDatabaseManager {
    protected final SystemTime _systemTime = new SystemTime();
    protected final FullNodeDatabaseManager _databaseManager;
    protected final MasterInflater _masterInflater;
    protected final BlockCache _blockCache;

    // TODO: Inserting a transaction requires a write lock...

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
            final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));
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

    protected Transaction _getTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        { // Attempt to load the Transaction from a Block on disk...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT blocks.hash AS block_hash, blocks.block_height, block_transactions.disk_offset, transactions.byte_count FROM transactions INNER JOIN block_transactions ON transactions.id = block_transactions.transaction_id INNER JOIN blocks ON blocks.id = block_transactions.block_id WHERE transactions.id = ? LIMIT 1")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final Sha256Hash blockHash = Sha256Hash.fromHexString(row.getString("block_hash"));
                final Long blockHeight = row.getLong("block_height");
                final Long diskOffset = row.getLong("disk_offset");
                final Integer byteCount = row.getInteger("byte_count");

                final ByteArray transactionData = _blockCache.readFromBlock(blockHash, blockHeight, diskOffset, byteCount);
                if (transactionData == null) { return null; }

                final TransactionInflater transactionInflater = _masterInflater.getTransactionInflater();
                return transactionInflater.fromBytes(transactionData);
            }
        }

        { // Attempt to load the Transaction from the Unconfirmed Transactions table...
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, hash, version, lock_time FROM unconfirmed_transactions WHERE transaction_id = ?")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final UnconfirmedTransactionId unconfirmedTransactionId = UnconfirmedTransactionId.wrap(row.getLong("id"));
                final Long version = row.getLong("version");
                final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));

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

    public FullNodeTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager, final BlockCache blockCache, final MasterInflater masterInflater) {
        _databaseManager = databaseManager;
        _masterInflater = masterInflater;
        _blockCache = blockCache;
    }

    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _getTransaction(transactionId);
    }

    public Transaction getTransaction(final TransactionId transactionId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        return _getTransaction(transactionId);
    }

    public Boolean previousOutputsExist(final Transaction transaction) throws DatabaseException {
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final Integer previousOutputIndex = transactionInput.getPreviousOutputIndex();

            final TransactionId previousTransactionId = _getTransactionId(previousTransactionHash);
            if (previousTransactionId == null) { return false; }

            final Transaction previousTransaction = _getTransaction(previousTransactionId);
            if (previousTransaction == null) { return false; }

            final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
            if (previousOutputIndex >= previousTransactionOutputs.getCount()) { return false; }
        }

        return true;
    }

    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final Transaction transaction = _getTransaction(transactionId);
        if (transaction == null) {
            throw new DatabaseException("Unable to load transaction: " + transactionId);
        }

        _storeUnconfirmedTransaction(transactionId, transaction);
    }

    public void addToUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        final MutableList<Transaction> transactions = new MutableList<Transaction>(transactionIds.getCount());

        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = _getTransaction(transactionId);
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

    public void removeFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionId);
    }

    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        _deleteFromUnconfirmedTransactions(transactionIds);
    }

    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        return (! rows.isEmpty());
    }

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

    public List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(final List<TransactionId> transactionIds) throws DatabaseException {
        if (transactionIds.isEmpty()) { return new MutableList<TransactionId>(0); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        // TODO: Optimize...
        final HashSet<TransactionOutputIdentifier> transactionOutputIdentifiers = new HashSet<TransactionOutputIdentifier>();
        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = _getTransaction(transactionId);
            if (transaction == null) {
                Logger.error("Unable to find Transaction: " + transactionId);
            }
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                transactionOutputIdentifiers.add(TransactionOutputIdentifier.fromTransactionInput(transactionInput));
            }
        }

        final java.util.List<Row> rows;
        {
            final Query query = new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "unconfirmed_transaction_inputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON unconfirmed_transaction_inputs.unconfirmed_transaction_id = unconfirmed_transactions.id " +
                "WHERE " +
                    "(unconfirmed_transaction_inputs.previous_transaction_hash, unconfirmed_transaction_inputs.previous_transaction_output_index) IN (?) " +
                "GROUP BY unconfirmed_transactions.transaction_id"
            );

//            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
//                final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();
//                final Integer previousTransactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
//
//                query.setInClauseParameters(
//                    new InClauseParameter(
//                        new TypedParameter(previousTransactionHash.toString()),
//                        new TypedParameter(previousTransactionOutputIndex)
//                    )
//                );
//            }
            query.setInClauseParameters(transactionOutputIdentifiers, new ValueExtractor<TransactionOutputIdentifier>() {
                @Override
                public InClauseParameter extractValues(final TransactionOutputIdentifier transactionOutputIdentifier) {
                    final Sha256Hash previousTransactionHash = transactionOutputIdentifier.getTransactionHash();
                    final Integer previousTransactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

                    return new InClauseParameter(
                        (previousTransactionHash != null ? new TypedParameter(previousTransactionHash.toString()) : TypedParameter.NULL),
                        (previousTransactionOutputIndex != null ? new TypedParameter(previousTransactionOutputIndex) : TypedParameter.NULL)
                    );
                }
            });

            rows = databaseConnection.query(query);
        }

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            listBuilder.add(transactionId);
        }
        return listBuilder.build();
    }

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

    public Integer getUnconfirmedTransactionCount() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS transaction_count FROM unconfirmed_transactions")
        );
        final Row row = rows.get(0);

        return row.getInteger("transaction_count");
    }

    public Long calculateTransactionFee(final Transaction transaction) throws DatabaseException {
        // TODO: Optimize.

        long totalInputAmount = 0L;
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
            final TransactionId previousTransactionId = _getTransactionId(previousTransactionHash);
            if (previousTransactionId == null) { return null; }

            final Transaction previousTransaction = _getTransaction(previousTransactionId);
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

    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        final TransactionId transactionId = _getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = _databaseManager.getSlpTransactionDatabaseManager();
        return slpTransactionDatabaseManager.getSlpTokenId(transactionId);
    }

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
            final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("hash"));

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

    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionId(transactionHash);
    }

    public Sha256Hash getTransactionHash(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, hash FROM transactions WHERE id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

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

    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return _getBlockIds(transactionId);
    }

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

        final Transaction transaction = _getTransaction(transactionId);
        if (transaction == null) { return null; }

        final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
        if (outputIndex >= transactionOutputs.getCount()) { return null; }

        return transactionOutputs.get(outputIndex);
    }
}

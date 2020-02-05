package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.BlockCache;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.*;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableLockTime;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.HashMap;

public class NonIndexingFullNodeTransactionDatabaseManagerCore extends FullNodeTransactionDatabaseManagerCore {
    protected final MasterInflater _masterInflater;
    protected final BlockCache _blockCache;

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

        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();
        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.insertTransactionInput(transactionId, transactionInput);
        }

        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.insertTransactionOutput(transactionId, transactionOutput);
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
                new Query("SELECT id, version, lock_time FROM unconfirmed_transactions WHERE transaction_id = ?")
                    .setParameter(transactionId)
            );
            if (! rows.isEmpty()) {
                final Row row = rows.get(0);
                final UnconfirmedTransactionId unconfirmedTransactionId = UnconfirmedTransactionId.wrap(row.getLong("id"));
                final Long version = row.getLong("version");
                final LockTime lockTime = new ImmutableLockTime(row.getLong("lock_time"));

                final MutableTransaction transaction = new MutableTransaction();
                transaction.setVersion(version);
                transaction.setLockTime(lockTime);

                final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();
                final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIds(transactionId);
                for (final TransactionInputId transactionInputId : transactionInputIds) {
                    final TransactionInput transactionInput = transactionInputDatabaseManager.getTransactionInput(transactionInputId);
                    transaction.addTransactionInput(transactionInput);
                }

                final TransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getTransactionOutputDatabaseManager();
                final List<TransactionOutputId> transactionOutputIds = transactionOutputDatabaseManager.getTransactionOutputIds(transactionId);
                for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
                    final TransactionOutput transactionOutput = transactionOutputDatabaseManager.getTransactionOutput(transactionOutputId);
                    transaction.addTransactionOutput(transactionOutput);
                }

                return transaction;
            }
        }

        return null;
    }

    public NonIndexingFullNodeTransactionDatabaseManagerCore(final FullNodeDatabaseManager databaseManager, final BlockCache blockCache, final MasterInflater masterInflater) {
        super(databaseManager);

        _masterInflater = masterInflater;
        _blockCache = blockCache;
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId) throws DatabaseException {
        return _getTransaction(transactionId);
    }

    @Override
    public Transaction getTransaction(final TransactionId transactionId, final Boolean shouldUpdateUnspentOutputCache) throws DatabaseException {
        return _getTransaction(transactionId);
    }

    @Override
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

    @Override
    public void addToUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        final Transaction transaction = _getTransaction(transactionId);
        if (transaction == null) {
            throw new DatabaseException("Unable to load transaction: " + transactionId);
        }

        _storeUnconfirmedTransaction(transactionId, transaction);
    }

    @Override
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

    @Override
    public void removeFromUnconfirmedTransactions(final TransactionId transactionId) throws DatabaseException {
        super.removeFromUnconfirmedTransactions(transactionId);
    }

    @Override
    public void removeFromUnconfirmedTransactions(final List<TransactionId> transactionIds) throws DatabaseException {
        super.removeFromUnconfirmedTransactions(transactionIds);
    }

    @Override
    public Boolean isUnconfirmedTransaction(final TransactionId transactionId) throws DatabaseException {
        return super.isUnconfirmedTransaction(transactionId);
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionIds() throws DatabaseException {
        return super.getUnconfirmedTransactionIds();
    }

    @Override
    public List<TransactionId> getUnconfirmedTransactionsDependingOnSpentInputsOf(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query(
                "SELECT " +
                    "unconfirmed_transactions.transaction_id " +
                "FROM " +
                    "unconfirmed_transaction_inputs " +
                    "INNER JOIN unconfirmed_transactions " +
                        "ON unconfirmed_transaction_inputs.transaction_id = unconfirmed_transactions.transaction_id " +
                "WHERE " +
                    "unconfirmed_transaction_inputs.previous_transaction_output_id IN (" +
                        "SELECT previous_transaction_output_id FROM unconfirmed_transaction_inputs WHERE transaction_id IN (?)" +
                    ")" +
                "GROUP BY unconfirmed_transactions.transaction_id"
            ).setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
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
            ).setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
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
        return super.getUnconfirmedTransactionCount();
    }

    @Override
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

    @Override
    public void updateTransaction(final Transaction transaction) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteTransaction(final TransactionId transactionId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SlpTokenId getSlpTokenId(final Sha256Hash transactionHash) throws DatabaseException {
        return null; // TODO: Possible to keep SLP validation...
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

    @Override
    public TransactionId getTransactionId(final Sha256Hash transactionHash) throws DatabaseException {
        return _getTransactionId(transactionHash);
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
        return Sha256Hash.fromHexString(row.getString("hash"));
    }

    @Override
    public BlockId getBlockId(final BlockchainSegmentId blockchainSegmentId, final TransactionId transactionId) throws DatabaseException {
        return super.getBlockId(blockchainSegmentId, transactionId);
    }

    @Override
    public List<BlockId> getBlockIds(final TransactionId transactionId) throws DatabaseException {
        return super.getBlockIds(transactionId);
    }

    @Override
    public List<BlockId> getBlockIds(final Sha256Hash transactionHash) throws DatabaseException {
        return super.getBlockIds(transactionHash);
    }
}

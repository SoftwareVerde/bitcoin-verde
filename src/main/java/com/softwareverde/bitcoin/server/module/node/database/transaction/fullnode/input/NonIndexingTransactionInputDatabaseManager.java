package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.UnconfirmedTransactionId;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.hash.sha256.Sha256Hash;

import java.util.Map;

public class NonIndexingTransactionInputDatabaseManager extends TransactionInputDatabaseManager {
    protected UnconfirmedTransactionId _getUnconfirmedTransactionId(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transactions WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return UnconfirmedTransactionId.wrap(row.getLong("id"));
    }

    public NonIndexingTransactionInputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public TransactionInputId getTransactionInputId(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
        if (unconfirmedTransactionId == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_inputs WHERE unconfirmed_transaction_id = ? AND previous_transaction_hash = ? AND previous_transaction_output_index = ?")
                .setParameter(unconfirmedTransactionId)
                .setParameter(transactionInput.getPreviousOutputTransactionHash())
                .setParameter(transactionInput.getPreviousOutputIndex())
        );
        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return TransactionInputId.wrap(row.getLong("id"));
    }

    @Override
    public TransactionInputId insertTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
        if (unconfirmedTransactionId == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer index;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS `index` FROM unconfirmed_transaction_inputs WHERE unconfirmed_transaction_id = ?")
                    .setParameter(unconfirmedTransactionId)
            );
            final Row row = rows.get(0);
            index = row.getInteger("index");
        }

        final Long transactionInputId = databaseConnection.executeSql(
            new Query("INSERT INTO unconfirmed_transaction_inputs (unconfirmed_transaction_id, `index`, previous_transaction_hash, previous_transaction_output_index, sequence_number, unlocking_script) VALUES (?, ?, ?, ?, ?, ?)")
                .setParameter(unconfirmedTransactionId)
                .setParameter(index)
                .setParameter(transactionInput.getPreviousOutputTransactionHash())
                .setParameter(transactionInput.getPreviousOutputIndex())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInput.getUnlockingScript().getBytes())
        );

        return TransactionInputId.wrap(transactionInputId);
    }

    @Override
    public List<TransactionInputId> insertTransactionInputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unconfirmed_transaction_inputs (unconfirmed_transaction_id, `index`, previous_transaction_hash, previous_transaction_output_index, sequence_number, unlocking_script) VALUES (?, ?, ?, ?, ?, ?)");

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final TransactionId transactionId = transactionIds.get(transactionHash);
            final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
            if (unconfirmedTransactionId == null) { return null; }

            int index = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                batchedInsertQuery.setParameter(unconfirmedTransactionId);
                batchedInsertQuery.setParameter(index);
                batchedInsertQuery.setParameter(transactionInput.getPreviousOutputTransactionHash());
                batchedInsertQuery.setParameter(transactionInput.getPreviousOutputIndex());
                batchedInsertQuery.setParameter(transactionInput.getSequenceNumber());
                batchedInsertQuery.setParameter(transactionInput.getUnlockingScript().getBytes());

                index += 1;
            }
        }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final Long firstTransactionInputId = databaseConnection.executeSql(batchedInsertQuery);
        final Integer insertCount = databaseConnection.getRowsAffectedCount();

        final MutableList<TransactionInputId> transactionInputIds = new MutableList<TransactionInputId>(insertCount);
        for (int i = 0; i < insertCount; ++i) {
            final TransactionInputId transactionInputId = TransactionInputId.wrap(firstTransactionInputId + i);
            transactionInputIds.add(transactionInputId);
        }
        return transactionInputIds;
    }

    @Override
    public TransactionInput getTransactionInput(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash previousTransactionHash = Sha256Hash.fromHexString(row.getString("previous_transaction_hash"));
        final Integer previousTransactionOutputIndex = row.getInteger("previous_transaction_output_index");
        final SequenceNumber sequenceNumber = new ImmutableSequenceNumber(row.getLong("sequence_number"));
        final UnlockingScript unlockingScript = new ImmutableUnlockingScript(MutableByteArray.wrap(row.getBytes("unlocking_script")));

        final MutableTransactionInput transactionInput = new MutableTransactionInput();
        transactionInput.setPreviousOutputTransactionHash(previousTransactionHash);
        transactionInput.setPreviousOutputIndex(previousTransactionOutputIndex);
        transactionInput.setSequenceNumber(sequenceNumber);
        transactionInput.setUnlockingScript(unlockingScript);
        return transactionInput;
    }

    @Override
    public TransactionOutputId findPreviousTransactionOutputId(final TransactionInput transactionInput) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransactionOutputId getPreviousTransactionOutputId(final TransactionInputId transactionInputId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TransactionInputId> getTransactionInputIds(final TransactionId transactionId) throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransactionId getPreviousTransactionId(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, previous_transaction_hash FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash previousTransactionHash = Sha256Hash.fromHexString(row.getString("previous_transaction_hash"));

        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        return transactionDatabaseManager.getTransactionId(previousTransactionHash);
    }

    @Override
    public void updateTransactionInput(final TransactionInputId transactionInputId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("UPDATE unconfirmed_transaction_inputs SET previous_transaction_hash = ?, previous_transaction_output_index = ?, sequence_number = ?, unlocking_script = ? WHERE id = ?")
                .setParameter(transactionInput.getPreviousOutputTransactionHash())
                .setParameter(transactionInput.getPreviousOutputIndex())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInput.getUnlockingScript().getBytes())
                .setParameter(transactionInputId)
        );
    }

    @Override
    public void deleteTransactionInput(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
    }

    @Override
    public TransactionId getTransactionId(final TransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id FROM unconfirmed_transaction_inputs INNER JOIN unconfirmed_transactions ON unconfirmed_transaction_inputs.unconfirmed_transaction_id = unconfirmed_transactions.id WHERE unconfirmed_transaction_inputs.id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    @Override
    public List<TransactionInputId> getTransactionInputIdsSpendingTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        throw new UnsupportedOperationException(); // TODO
    }
}

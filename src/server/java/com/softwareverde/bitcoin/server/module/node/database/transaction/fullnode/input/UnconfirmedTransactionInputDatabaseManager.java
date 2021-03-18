package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output.UnconfirmedTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.UnconfirmedTransactionInputId;
import com.softwareverde.bitcoin.transaction.locktime.ImmutableSequenceNumber;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.unlocking.ImmutableUnlockingScript;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.util.Map;

public class UnconfirmedTransactionInputDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    public UnconfirmedTransactionInputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public UnconfirmedTransactionInputId getUnconfirmedTransactionInputId(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_inputs WHERE transaction_id = ? AND previous_transaction_hash = ? AND previous_transaction_output_index = ?")
                .setParameter(transactionId)
                .setParameter(transactionInput.getPreviousOutputTransactionHash())
                .setParameter(transactionInput.getPreviousOutputIndex())
        );
        if (rows.isEmpty()) { return null; }
        final Row row = rows.get(0);

        return UnconfirmedTransactionInputId.wrap(row.getLong("id"));
    }

    public UnconfirmedTransactionInputId insertUnconfirmedTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer index;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS `index` FROM unconfirmed_transaction_inputs WHERE transaction_id = ?")
                    .setParameter(transactionId)
            );
            final Row row = rows.get(0);
            index = row.getInteger("index");
        }

        final Long transactionInputId = databaseConnection.executeSql(
            new Query("INSERT INTO unconfirmed_transaction_inputs (transaction_id, `index`, previous_transaction_hash, previous_transaction_output_index, sequence_number, unlocking_script) VALUES (?, ?, ?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(index)
                .setParameter(transactionInput.getPreviousOutputTransactionHash())
                .setParameter(transactionInput.getPreviousOutputIndex())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInput.getUnlockingScript().getBytes())
        );

        return UnconfirmedTransactionInputId.wrap(transactionInputId);
    }

    public List<UnconfirmedTransactionInputId> insertUnconfirmedTransactionInputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unconfirmed_transaction_inputs (transaction_id, `index`, previous_transaction_hash, previous_transaction_output_index, sequence_number, unlocking_script) VALUES (?, ?, ?, ?, ?, ?)");

        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final TransactionId transactionId = transactionIds.get(transactionHash);

            int index = 0;
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                batchedInsertQuery.setParameter(transactionId);
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

        final MutableList<UnconfirmedTransactionInputId> transactionInputIds = new MutableList<UnconfirmedTransactionInputId>(insertCount);
        for (int i = 0; i < insertCount; ++i) {
            final UnconfirmedTransactionInputId transactionInputId = UnconfirmedTransactionInputId.wrap(firstTransactionInputId + i);
            transactionInputIds.add(transactionInputId);
        }
        return transactionInputIds;
    }

    public TransactionInput getUnconfirmedTransactionInput(final UnconfirmedTransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash previousTransactionHash = Sha256Hash.copyOf(row.getBytes("previous_transaction_hash"));
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

    public UnconfirmedTransactionOutputId getPreviousTransactionOutputId(final UnconfirmedTransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, previous_transaction_hash, previous_transaction_output_index FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash previousTransactionHash = Sha256Hash.copyOf(row.getBytes("previous_transaction_hash"));
        final Integer previousTransactionOutputIndex = row.getInteger("previous_transaction_output_index");

        final UnconfirmedTransactionOutputDatabaseManager transactionOutputDatabaseManager = _databaseManager.getUnconfirmedTransactionOutputDatabaseManager();
        return transactionOutputDatabaseManager.getUnconfirmedTransactionOutputId(new TransactionOutputIdentifier(previousTransactionHash, previousTransactionOutputIndex));
    }

    public List<UnconfirmedTransactionInputId> getUnconfirmedTransactionInputIds(final TransactionId transactionId) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
        if (transactionHash == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_inputs WHERE transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final MutableList<UnconfirmedTransactionInputId> transactionInputIds = new MutableList<UnconfirmedTransactionInputId>(rows.size());
        for (final Row row : rows) {
            final UnconfirmedTransactionInputId transactionInputId = UnconfirmedTransactionInputId.wrap(row.getLong("id"));
            transactionInputIds.add(transactionInputId);
        }
        return transactionInputIds;
    }

    public TransactionId getUnconfirmedPreviousTransactionId(final UnconfirmedTransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, previous_transaction_hash FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Sha256Hash previousTransactionHash = Sha256Hash.copyOf(row.getBytes("previous_transaction_hash"));

        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        return transactionDatabaseManager.getTransactionId(previousTransactionHash);
    }

    public void deleteTransactionInput(final UnconfirmedTransactionInputId transactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(transactionInputId)
        );
    }

    public TransactionId getTransactionId(final UnconfirmedTransactionInputId unconfirmedTransactionInputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM unconfirmed_transaction_inputs WHERE id = ?")
                .setParameter(unconfirmedTransactionInputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    public UnconfirmedTransactionInputId getUnconfirmedTransactionInputIdSpendingTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_inputs WHERE previous_transaction_hash = ? AND previous_transaction_output_index = ?")
                .setParameter(transactionOutputIdentifier.getTransactionHash())
                .setParameter(transactionOutputIdentifier.getOutputIndex())
        );
        if (rows.isEmpty()) { return null; }


        final Row row = rows.get(0);
        return UnconfirmedTransactionInputId.wrap(row.getLong("id"));
    }
}

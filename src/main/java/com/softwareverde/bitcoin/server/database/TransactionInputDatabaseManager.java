package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionInputDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected TransactionOutputId _findPreviousTransactionOutputId(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);
        return transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionInput.getPreviousOutputIndex());
    }

    protected TransactionInputId _findTransactionInputId(final TransactionId transactionId, final TransactionOutputId previousTransactionOutputId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? AND previous_transaction_output_id = ?")
            .setParameter(transactionId)
            .setParameter(previousTransactionOutputId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionInputId.wrap(row.getLong("id"));
    }

    protected void _updateTransactionInput(final TransactionInputId transactionInputId, final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);

        _databaseConnection.executeSql(
            new Query("UPDATE transaction_inputs SET transaction_id = ?, previous_transaction_output_id = ?, unlocking_script = ?, sequence_number = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getUnlockingScript().getBytes())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInputId)
        );
    }

    protected TransactionInputId _insertTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);

        return TransactionInputId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, unlocking_script, sequence_number) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getUnlockingScript().getBytes())
                .setParameter(transactionInput.getSequenceNumber())
        ));
    }

    public TransactionInputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionInputId storeTransactionInput(final TransactionId transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputId previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);
        final TransactionInputId transactionInputId = _findTransactionInputId(transactionId, previousTransactionOutputId);

        if (transactionInputId != null) {
            _updateTransactionInput(transactionInputId, transactionId, transactionInput);
            return transactionInputId;
        }

        return _insertTransactionInput(transactionId, transactionInput);
    }
}

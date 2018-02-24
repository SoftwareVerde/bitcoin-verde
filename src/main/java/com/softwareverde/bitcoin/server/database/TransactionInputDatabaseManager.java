package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionInputDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected Long _findPreviousTransactionOutputId(final Long transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);
        return transactionOutputDatabaseManager.findTransactionOutput(transactionId, transactionInput.getOutputTransactionIndex());
    }

    protected Long _findTransactionInputId(final Long transactionId, final Long previousTransactionOutputId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_inputs WHERE transaction_id = ? AND previous_transaction_output_id = ?")
            .setParameter(transactionId)
            .setParameter(previousTransactionOutputId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("id");
    }

    protected void _updateTransactionInput(final Long transactionInputId, final Long transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final Long previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);

        _databaseConnection.executeSql(
            new Query("UPDATE transaction_inputs SET transaction_id = ?, previous_transaction_output_id = ?, unlocking_script = ?, sequence_number = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getUnlockingScript().getBytes())
                .setParameter(transactionInput.getSequenceNumber())
                .setParameter(transactionInputId)
        );
    }

    protected Long _insertTransactionInput(final Long transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final Long previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);

        return _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_inputs (transaction_id, previous_transaction_output_id, unlocking_script, sequence_number) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(previousTransactionOutputId)
                .setParameter(transactionInput.getUnlockingScript().getBytes())
                .setParameter(transactionInput.getSequenceNumber())
        );
    }

    public TransactionInputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public Long storeTransactionInput(final Long transactionId, final TransactionInput transactionInput) throws DatabaseException {
        final Long previousTransactionOutputId = _findPreviousTransactionOutputId(transactionId, transactionInput);
        final Long transactionInputId = _findTransactionInputId(transactionId, previousTransactionOutputId);

        if (transactionInputId != null) {
            _updateTransactionInput(transactionInputId, transactionId, transactionInput);
            return transactionInputId;
        }

        return _insertTransactionInput(transactionId, transactionInput);
    }
}

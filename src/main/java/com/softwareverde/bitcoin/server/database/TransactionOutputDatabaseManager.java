package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionOutputDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected Long _findTransactionOutput(final Long transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("id");
    }

    protected void _updateTransactionOutput(final Long transactionOutputId, final Long transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE transaction_outputs SET transaction_id = ?, `index` = ?, amount = ?, locking_script = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutput.getIndex())
                .setParameter(transactionOutput.getAmount())
                .setParameter(transactionOutput.getLockingScript().getBytes())
                .setParameter(transactionOutputId)
        );
    }

    protected Long _insertTransactionOutput(final Long transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount, locking_script) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionOutput.getIndex())
                .setParameter(transactionOutput.getAmount())
                .setParameter(transactionOutput.getLockingScript().getBytes())
        );
    }

    protected TransactionOutput _getTransactionOutput(final Long transactionOutputId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);

        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setIndex(row.getInteger("index"));
        mutableTransactionOutput.setAmount(row.getLong("amount"));
        mutableTransactionOutput.setLockingScript(row.getBytes("locking_script"));
        return mutableTransactionOutput;
    }

    public TransactionOutputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public Long storeTransactionOutput(final Long transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final Long transactionOutputId = _findTransactionOutput(transactionId, transactionOutput.getIndex());
        if (transactionOutputId != null) {
            _updateTransactionOutput(transactionOutputId, transactionId, transactionOutput);
            return transactionOutputId;
        }

        return _insertTransactionOutput(transactionId, transactionOutput);
    }

    public Long findTransactionOutput(final Long transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        return _findTransactionOutput(transactionId, transactionOutputIndex);
    }

    public TransactionOutput getTransactionOutput(final Long transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }
}

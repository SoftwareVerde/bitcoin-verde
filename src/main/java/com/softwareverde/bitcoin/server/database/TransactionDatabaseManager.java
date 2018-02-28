package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class TransactionDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected void _storeTransactionInputs(final Long transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection);

        for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
            transactionInputDatabaseManager.storeTransactionInput(transactionId, transactionInput);
        }
    }

    protected void _storeTransactionOutputs(final Long transactionId, final Transaction transaction) throws DatabaseException {
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = new TransactionOutputDatabaseManager(_databaseConnection);

        for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
            transactionOutputDatabaseManager.storeTransactionOutput(transactionId, transactionOutput);
        }
    }

    protected Long _getTransactionIdFromHash(final Hash transactionHash) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE hash = ?")
                .setParameter(BitcoinUtil.toHexString(transactionHash))
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("id");
    }

    protected void _updateTransaction(final Long transactionId, final Long blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        _databaseConnection.executeSql(
            new Query("UPDATE transactions SET hash = ?, block_id = ?, version = ?, has_witness_data = ?, lock_time = ? WHERE id = ?")
                .setParameter(BitcoinUtil.toHexString(transaction.calculateSha256Hash()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter((transaction.hasWitnessData() ? 1 : 0))
                .setParameter(lockTime.isTimestamp() ? lockTime.getTimestamp() : lockTime.getBlockHeight())
                .setParameter(transactionId)
        );
    }

    protected Long _insertTransaction(final Long blockId, final Transaction transaction) throws DatabaseException {
        final LockTime lockTime = transaction.getLockTime();
        return _databaseConnection.executeSql(
            new Query("INSERT INTO transactions (hash, block_id, version, has_witness_data, lock_time) VALUES (?, ?, ?, ?, ?)")
                .setParameter(BitcoinUtil.toHexString(transaction.calculateSha256Hash()))
                .setParameter(blockId)
                .setParameter(transaction.getVersion())
                .setParameter((transaction.hasWitnessData() ? 1 : 0))
                .setParameter(lockTime.isTimestamp() ? lockTime.getTimestamp() : lockTime.getBlockHeight())
        );
    }

    public TransactionDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public Long storeTransaction(final Long blockId, final Transaction transaction) throws DatabaseException {
        final Long transactionId;
        {
            final Long existingTransactionId = _getTransactionIdFromHash(transaction.calculateSha256Hash());
            if (existingTransactionId != null) {
                _updateTransaction(existingTransactionId, blockId, transaction);
                transactionId = existingTransactionId;
            }
            else {
                final Long newTransactionId = _insertTransaction(blockId, transaction);
                transactionId = newTransactionId;
            }
        }

        _storeTransactionInputs(transactionId, transaction);
        _storeTransactionOutputs(transactionId, transaction);
        return transactionId;
    }

    public Long getTransactionIdFromHash(final Hash transactionHash) throws DatabaseException {
        return _getTransactionIdFromHash(transactionHash);
    }
}

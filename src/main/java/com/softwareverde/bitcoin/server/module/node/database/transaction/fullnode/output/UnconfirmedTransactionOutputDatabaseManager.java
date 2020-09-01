package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnconfirmedTransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.util.Map;

public class UnconfirmedTransactionOutputDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    protected UnconfirmedTransactionOutputId _insertUnconfirmedTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer index;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS `index` FROM unconfirmed_transaction_outputs WHERE transaction_id = ?")
                    .setParameter(transactionId)
            );
            final Row row = rows.get(0);
            index = row.getInteger("index");
        }

        final Long amount = transactionOutput.getAmount();
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final Long transactionOutputId = databaseConnection.executeSql(
            new Query("INSERT INTO unconfirmed_transaction_outputs (transaction_id, `index`, amount, locking_script) VALUES (?, ?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(index)
                .setParameter(amount)
                .setParameter(lockingScript.getBytes())
        );

        return UnconfirmedTransactionOutputId.wrap(transactionOutputId);
    }

    public UnconfirmedTransactionOutputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public UnconfirmedTransactionOutputId insertUnconfirmedTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertUnconfirmedTransactionOutput(transactionId, transactionOutput);
    }

    public List<UnconfirmedTransactionOutputId> insertUnconfirmedTransactionOutputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unconfirmed_transaction_outputs (transaction_id, `index`, amount, locking_script) VALUES (?, ?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final TransactionId transactionId = transactionIds.get(transactionHash);

            int index = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final Long amount = transactionOutput.getAmount();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                batchedInsertQuery.setParameter(transactionId);
                batchedInsertQuery.setParameter(index);
                batchedInsertQuery.setParameter(amount);
                batchedInsertQuery.setParameter(lockingScript.getBytes());

                index += 1;
            }
        }

        final Long firstInsertId = databaseConnection.executeSql(batchedInsertQuery);
        final MutableList<UnconfirmedTransactionOutputId> transactionOutputIds = new MutableList<UnconfirmedTransactionOutputId>(transactions.getCount());
        for (int i = 0; i < transactions.getCount(); ++i) {
            final UnconfirmedTransactionOutputId transactionOutputId = UnconfirmedTransactionOutputId.wrap(firstInsertId + i);
            transactionOutputIds.add(transactionOutputId);
        }
        return transactionOutputIds;
    }

    public UnconfirmedTransactionOutputId getUnconfirmedTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionOutputIdentifier.getTransactionHash());
        if (transactionId == null) { return null; }

        final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(outputIndex)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return UnconfirmedTransactionOutputId.wrap(row.getLong("id"));
    }

    public Boolean isTransactionOutputSpent(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_inputs WHERE previous_transaction_hash = ? AND previous_transaction_output_index = ?")
                .setParameter(transactionOutputIdentifier.getTransactionHash())
                .setParameter(transactionOutputIdentifier.getOutputIndex())
        );
        return (! rows.isEmpty());
    }

    public TransactionOutput getUnconfirmedTransactionOutput(final UnconfirmedTransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Integer index = row.getInteger("index");
        final Long amount = row.getLong("amount");
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(row.getBytes("locking_script")));

        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setIndex(index);
        transactionOutput.setAmount(amount);
        transactionOutput.setLockingScript(lockingScript);
        return transactionOutput;
    }

    public TransactionOutput getUnconfirmedTransactionOutput(final TransactionId transactionId, final Integer outputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(outputIndex)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Integer index = row.getInteger("index");
        final Long amount = row.getLong("amount");
        final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(row.getBytes("locking_script")));

        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setIndex(index);
        transactionOutput.setAmount(amount);
        transactionOutput.setLockingScript(lockingScript);
        return transactionOutput;
    }

    public List<UnconfirmedTransactionOutputId> getUnconfirmedTransactionOutputIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_outputs WHERE transaction_id = ? ORDER BY `index` ASC")
                .setParameter(transactionId)
        );

        final MutableList<UnconfirmedTransactionOutputId> transactionOutputIds = new MutableList<UnconfirmedTransactionOutputId>(rows.size());
        for (final Row row : rows) {
            final UnconfirmedTransactionOutputId transactionOutputId =  UnconfirmedTransactionOutputId.wrap(row.getLong("id"));
            transactionOutputIds.add(transactionOutputId);
        }
        return transactionOutputIds;
    }

    public void deleteTransactionOutput(final UnconfirmedTransactionOutputId unconfirmedTransactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        databaseConnection.executeSql(
            new Query("DELETE FROM unconfirmed_transaction_outputs WHERE id = ?")
                .setParameter(unconfirmedTransactionOutputId)
        );
    }

    public TransactionId getTransactionId(final UnconfirmedTransactionOutputId unconfirmedTransactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM unconfirmed_transaction_outputs WHERE id = ?")
                .setParameter(unconfirmedTransactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }
}

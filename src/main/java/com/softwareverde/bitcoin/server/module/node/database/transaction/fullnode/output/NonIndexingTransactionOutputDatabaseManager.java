package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.UnconfirmedTransactionId;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.util.Map;

public class NonIndexingTransactionOutputDatabaseManager extends TransactionOutputDatabaseManager {
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

    protected TransactionOutputId _insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
        if (unconfirmedTransactionId == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer index;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT COUNT(*) AS `index` FROM unconfirmed_transaction_outputs WHERE unconfirmed_transaction_id = ?")
                    .setParameter(unconfirmedTransactionId)
            );
            final Row row = rows.get(0);
            index = row.getInteger("index");
        }

        final Long amount = transactionOutput.getAmount();
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final Long transactionOutputId = databaseConnection.executeSql(
            new Query("INSERT INTO unconfirmed_transaction_outputs (unconfirmed_transaction_id, `index`, amount, locking_script) VALUES (?, ?, ?, ?)")
                .setParameter(unconfirmedTransactionId)
                .setParameter(index)
                .setParameter(amount)
                .setParameter(lockingScript.getBytes())
        );

        return TransactionOutputId.wrap(transactionOutputId);
    }

    public NonIndexingTransactionOutputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionOutput);
    }

    @Override
    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final Sha256Hash transactionHash, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionOutput);
    }

    @Override
    public List<TransactionOutputId> insertTransactionOutputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unconfirmed_transaction_outputs (unconfirmed_transaction_id, `index`, amount, locking_script) VALUES (?, ?, ?, ?)");
        for (final Transaction transaction : transactions) {
            final Sha256Hash transactionHash = transaction.getHash();
            final TransactionId transactionId = transactionIds.get(transactionHash);
            final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
            if (unconfirmedTransactionId == null) { return null; }

            int index = 0;
            for (final TransactionOutput transactionOutput : transaction.getTransactionOutputs()) {
                final Long amount = transactionOutput.getAmount();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                batchedInsertQuery.setParameter(unconfirmedTransactionId);
                batchedInsertQuery.setParameter(index);
                batchedInsertQuery.setParameter(amount);
                batchedInsertQuery.setParameter(lockingScript.getBytes());

                index += 1;
            }
        }

        final Long firstInsertId = databaseConnection.executeSql(batchedInsertQuery);
        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(transactions.getCount());
        for (int i = 0; i < transactions.getCount(); ++i) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(firstInsertId + i);
            transactionOutputIds.add(transactionOutputId);
        }
        return transactionOutputIds;
    }

    @Override
    public TransactionOutputId findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionOutputIdentifier.getTransactionHash());
        if (transactionId == null) { return null; }

        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
        if (unconfirmedTransactionId == null) { return null; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_outputs WHERE unconfirmed_transaction_id = ?")
                .setParameter(unconfirmedTransactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionOutputId.wrap(row.getLong("id"));
    }

    @Override
    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
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

    @Override
    public TransactionOutput getTransactionOutput(final TransactionId transactionId, final Integer outputIndex) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT * FROM unconfirmed_transaction_outputs WHERE unconfirmed_transaction_id = ? AND `index` = ?")
                .setParameter(unconfirmedTransactionId)
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

    @Override
    public void markTransactionOutputAsSpent(final TransactionOutputId transactionOutputId, final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        // Nothing.
    }

    @Override
    public void markTransactionOutputsAsSpent(final List<TransactionOutputId> transactionOutputIds, final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        // Nothing.
    }

    @Override
    public List<TransactionOutputId> getTransactionOutputIds(final TransactionId transactionId) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM unconfirmed_transaction_outputs WHERE unconfirmed_transaction_id = ? ORDER BY `index` ASC")
                .setParameter(unconfirmedTransactionId)
        );

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(rows.size());
        for (final Row row : rows) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));
            transactionOutputIds.add(transactionOutputId);
        }
        return transactionOutputIds;
    }

    @Override
    public void updateTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final UnconfirmedTransactionId unconfirmedTransactionId = _getUnconfirmedTransactionId(transactionId);
        final Integer index = transactionOutput.getIndex();
        final Long amount = transactionOutput.getAmount();
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("UPDATE FROM unconfirmed_transaction_outputs SET unconfirmed_transaction_id = ?, `index` = ?, amount = ?, locking_script = ? WHERE id = ?")
                .setParameter(unconfirmedTransactionId)
                .setParameter(index)
                .setParameter(amount)
                .setParameter(lockingScript.getBytes())
                .setParameter(transactionOutputId)
        );
    }

    @Override
    public Boolean isTransactionOutputSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        // TODO
    }

    @Override
    public void deleteTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        // TODO
    }

    @Override
    public TransactionId getTransactionId(final LockingScriptId lockingScriptId) throws DatabaseException {
        // TODO
    }

    @Override
    public List<LockingScriptId> getLockingScriptsWithUnprocessedTypes(final Integer maxCount) throws DatabaseException {
        // TODO
    }

    @Override
    public void setLockingScriptType(final LockingScriptId lockingScriptId, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) throws DatabaseException {
        // TODO
    }

    @Override
    public void setLockingScriptTypes(final List<LockingScriptId> lockingScriptIds, final List<ScriptType> scriptTypes, final List<AddressId> addressIds, final List<TransactionId> slpTransactionIds) throws DatabaseException {
        // TODO
    }

    @Override
    public LockingScript getLockingScript(final LockingScriptId lockingScriptId) throws DatabaseException {
        // TODO
    }

    @Override
    public List<LockingScript> getLockingScripts(final List<LockingScriptId> lockingScriptIds) throws DatabaseException {
        // TODO
    }

    @Override
    public TransactionOutputId getTransactionOutputId(final LockingScriptId lockingScriptId) throws DatabaseException {
        // TODO
    }

    @Override
    public TransactionId getTransactionId(final TransactionOutputId transactionOutputId) throws DatabaseException {
        // TODO
    }
}

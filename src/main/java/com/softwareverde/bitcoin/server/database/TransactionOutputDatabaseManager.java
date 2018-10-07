package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.LockingScriptId;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.BatchedUpdateQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.util.Util;

public class TransactionOutputDatabaseManager {

    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected TransactionOutputId _findTransactionOutput(final Boolean isSpent, final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final TransactionOutputId cachedTransactionOutputId = _databaseManagerCache.getCachedTransactionOutputId(transactionId, transactionOutputIndex);
        if (cachedTransactionOutputId != null) { return cachedTransactionOutputId; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE is_spent = ? AND transaction_id = ? AND `index` = ?")
                .setParameter(isSpent ? 1 : 0)
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));

        _databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    /**
     * Attempts to first find an unspent TransactionOutput that matches the TransactionId/Index combination.
     *  If an unspent TransactionOutput is not found, the search is repeated for spent TransactionOutputs.
     */
    protected TransactionOutputId _findTransactionOutput(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final TransactionOutputId unspentTransactionOutputId = _findTransactionOutput(false, transactionId, transactionOutputIndex);
        if (unspentTransactionOutputId != null) { return unspentTransactionOutputId; }

        final TransactionOutputId spentTransactionOutputId = _findTransactionOutput(true, transactionId, transactionOutputIndex);
        return spentTransactionOutputId;
    }

    protected TransactionOutputId _insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final Integer transactionOutputIndex = transactionOutput.getIndex();

        final Long transactionOutputIdLong = _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
                .setParameter(transactionOutput.getAmount())
        );

        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionOutputIdLong);
        if (transactionOutputId == null) { return null; }

        _insertLockingScript(transactionOutputId, lockingScript);

        _databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    protected void _insertLockingScript(final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
        // final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        // final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

        // final AddressId addressId;
        // if (scriptType != ScriptType.CUSTOM_SCRIPT) {
        //     final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(_databaseConnection, _databaseManagerCache);
        //     addressId = addressDatabaseManager.storeScriptAddress(lockingScript);
        // }
        // else {
        //     addressId = null;
        // }

        final ByteArray lockingScriptByteArray = lockingScript.getBytes();
        _databaseConnection.executeSql(
            new Query("INSERT INTO locking_scripts (type, transaction_output_id, script, address_id) VALUES (?, ?, ?, ?)")
                .setParameter(ScriptType.UNKNOWN)                   // scriptType
                .setParameter(transactionOutputId)
                .setParameter(lockingScriptByteArray.getBytes())
                .setParameter(null)                                 // addressId
        );
    }

    protected void _updateLockingScript(final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

        final AddressId addressId;
        if (scriptType != ScriptType.CUSTOM_SCRIPT) {
            final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(_databaseConnection, _databaseManagerCache);
            addressId = addressDatabaseManager.storeScriptAddress(lockingScript);
        }
        else {
            addressId = null;
        }

        final ByteArray lockingScriptByteArray = lockingScript.getBytes();
        _databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET type = ?, script = ?, address_id = ? WHERE transaction_output_id = ?")
                .setParameter(scriptType)
                .setParameter(lockingScriptByteArray.getBytes())
                .setParameter(addressId)
                .setParameter(transactionOutputId)
        );
    }

    protected void _insertLockingScripts(final List<TransactionOutputId> transactionOutputIds, final List<LockingScript> lockingScripts) throws DatabaseException {
        if (! Util.areEqual(transactionOutputIds.getSize(), lockingScripts.getSize())) {
            throw new DatabaseException("Attempted to insert LockingScripts without matching TransactionOutputIds.");
        }

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO locking_scripts (type, transaction_output_id, script, address_id) VALUES (?, ?, ?, ?)");

        // final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(_databaseConnection, _databaseManagerCache);
        // final List<AddressId> addressIds = addressDatabaseManager.storeScriptAddresses(lockingScripts);
        // final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        for (int i = 0; i < transactionOutputIds.getSize(); ++i) {
            final TransactionOutputId transactionOutputId = transactionOutputIds.get(i);
            final LockingScript lockingScript = lockingScripts.get(i);

            // final AddressId addressId = addressIds.get(i);
            // final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

            final ByteArray lockingScriptByteArray = lockingScript.getBytes();
            batchInsertQuery.setParameter(ScriptType.UNKNOWN);                  // scriptType
            batchInsertQuery.setParameter(transactionOutputId);
            batchInsertQuery.setParameter(lockingScriptByteArray.getBytes());
            batchInsertQuery.setParameter(null);                                // addressId
        }

        _databaseConnection.executeSql(batchInsertQuery);
    }

    protected TransactionOutput _getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final Row transactionOutputRow;
        {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT * FROM transaction_outputs WHERE id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            transactionOutputRow = rows.get(0);
        }

        final LockingScript lockingScript;
        {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id, script FROM locking_scripts WHERE transaction_output_id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            lockingScript = new ImmutableLockingScript(row.getBytes("script"));
        }

        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setIndex(transactionOutputRow.getInteger("index"));
        mutableTransactionOutput.setAmount(transactionOutputRow.getLong("amount"));
        mutableTransactionOutput.setLockingScript(lockingScript);
        return mutableTransactionOutput;
    }

    protected Boolean _isTransactionOutputSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = new TransactionInputDatabaseManager(_databaseConnection, _databaseManagerCache);
        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIdsSpendingTransactionOutput(transactionOutputId);
        final Boolean transactionOutputIsSpent = (! transactionInputIds.isEmpty());
        return transactionOutputIsSpent;
    }

    public TransactionOutputDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionOutput);
    }

    public List<TransactionOutputId> insertTransactionOutputs(final List<TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        if (! Util.areEqual(transactionIds.getSize(), transactions.getSize())) { return null; }
        if (transactions.isEmpty()) { return new MutableList<TransactionOutputId>(0); }

        final Integer transactionCount = transactions.getSize();

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)");

        final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>(transactionCount * 2);

        for (int i = 0; i < transactionCount; ++i) {
            final TransactionId transactionId = transactionIds.get(i);
            final Transaction transaction = transactions.get(i);

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Long transactionOutputAmount = transactionOutput.getAmount();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                lockingScripts.add(lockingScript);

                batchInsertQuery.setParameter(transactionId);
                batchInsertQuery.setParameter(transactionOutputIndex);
                batchInsertQuery.setParameter(transactionOutputAmount);
            }
        }

        final Long firstTransactionOutputId = _databaseConnection.executeSql(batchInsertQuery);
        if (firstTransactionOutputId == null) { return null; }

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(lockingScripts.getSize());
        for (int i = 0; i < lockingScripts.getSize(); ++i) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(firstTransactionOutputId + i);
            transactionOutputIds.add(transactionOutputId);
        }

        _insertLockingScripts(transactionOutputIds, lockingScripts);

        return transactionOutputIds;
    }

    public TransactionOutputId findTransactionOutput(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        return _findTransactionOutput(transactionId, transactionOutputIndex);
    }

    public TransactionOutputId findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);

        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();
        final TransactionId transactionId = transactionDatabaseManager.getTransactionIdFromHash(transactionOutputIdentifier.getTransactionHash());
        if (transactionId == null) { return null; }

        final TransactionOutputId transactionOutputId = _findTransactionOutput(transactionId, transactionOutputIndex);
        return transactionOutputId;
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }

    public void markTransactionOutputAsSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE transaction_outputs SET is_spent = 1 WHERE id = ?")
                .setParameter(transactionOutputId)
        );
    }

    public void markTransactionOutputsAsSpent(final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final Query batchedUpdateQuery = new BatchedUpdateQuery("UPDATE transaction_outputs SET is_spent = 1 WHERE id IN(?)");
        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
            batchedUpdateQuery.setParameter(transactionOutputId);
        }

        _databaseConnection.executeSql(batchedUpdateQuery);
    }

    public List<TransactionOutputId> getTransactionOutputIds(final TransactionId transactionId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ?")
                .setParameter(transactionId)
        );

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(rows.size());
        for (final Row row : rows) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));
            transactionOutputIds.add(transactionOutputId);
        }

        return transactionOutputIds;
    }

    public void updateTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionOutputIdCache();

        final LockingScript lockingScript = transactionOutput.getLockingScript();

        _databaseConnection.executeSql(
            new Query("UPDATE transaction_outputs SET transaction_id = ?, `index` = ?, amount = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutput.getIndex())
                .setParameter(transactionOutput.getAmount())
                .setParameter(transactionOutputId)
        );

        _updateLockingScript(transactionOutputId, lockingScript);
    }

    public Boolean isTransactionOutputSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _isTransactionOutputSpent(transactionOutputId);
    }

    public void deleteTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionOutputIdCache();

        final Boolean transactionOutputWasSpent = _isTransactionOutputSpent(transactionOutputId);
        if (transactionOutputWasSpent) {
            throw new DatabaseException("Cannot delete spent TransactionOutput: " + transactionOutputId);
        }

        _databaseConnection.executeSql(
            new Query("DELETE FROM locking_scripts WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        _databaseConnection.executeSql(
            new Query("DELETE FROM transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
    }

    public List<LockingScriptId> getLockingScriptsWithUnprocessedTypes(final Integer maxCount) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM locking_scripts WHERE type = ? LIMIT " + Util.coalesce(maxCount, 1))
                .setParameter(ScriptType.UNKNOWN)
        );

        final ImmutableListBuilder<LockingScriptId> lockingScriptIds = new ImmutableListBuilder<LockingScriptId>(rows.size());
        for (final Row row : rows) {
            final LockingScriptId lockingScriptId = LockingScriptId.wrap(row.getLong("id"));
            lockingScriptIds.add(lockingScriptId);
        }

        return lockingScriptIds.build();
    }

    public void setLockingScriptType(final LockingScriptId lockingScriptId, final ScriptType scriptType, final AddressId addressId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET type = ?, address_id = ? WHERE id = ?")
                .setParameter(scriptType)
                .setParameter(addressId)
                .setParameter(lockingScriptId)
        );
    }

    public void setLockingScriptTypes(final List<LockingScriptId> lockingScriptIds, final List<ScriptType> scriptTypes, final List<AddressId> addressIds) throws DatabaseException {
        final Integer lockingScriptCount = lockingScriptIds.getSize();
        if ( (! Util.areEqual(lockingScriptCount, scriptTypes.getSize())) || (! Util.areEqual(lockingScriptCount, addressIds.getSize())) )  { throw new DatabaseException("Attempting to update LockingScriptTypes with mismatching Ids."); }
        if (lockingScriptIds.isEmpty()) { return; }

        for (int i = 0; i < lockingScriptCount; ++i) {
            final LockingScriptId lockingScriptId = lockingScriptIds.get(i);
            final ScriptType scriptType = scriptTypes.get(i);
            final AddressId addressId = addressIds.get(i);

            _databaseConnection.executeSql(
                new Query("UPDATE locking_scripts SET type = ?, address_id = ? WHERE id = ?")
                    .setParameter(scriptType)
                    .setParameter(addressId)
                    .setParameter(lockingScriptId)
            );
        }
    }

    public LockingScript getLockingScript(final LockingScriptId lockingScriptId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, script FROM locking_scripts WHERE id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return new ImmutableLockingScript(row.getBytes("script"));
    }

    public List<LockingScript> getLockingScripts(final List<LockingScriptId> lockingScriptId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, script FROM locking_scripts WHERE id IN (" + DatabaseUtil.createInClause(lockingScriptId) + ")")
        );
        if (rows.isEmpty()) { return null; }

        final ImmutableListBuilder<LockingScript> lockingScriptsBuilder = new ImmutableListBuilder<LockingScript>(rows.size());
        for (final Row row : rows) {
            final LockingScript lockingScript = new ImmutableLockingScript(row.getBytes("script"));
            lockingScriptsBuilder.add(lockingScript);
        }

        return lockingScriptsBuilder.build();
    }
}

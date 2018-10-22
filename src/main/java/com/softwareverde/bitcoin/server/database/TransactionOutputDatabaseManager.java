package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
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
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
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
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TransactionOutputDatabaseManager {

    protected static class UnspentTransactionOutputs {
        public final Sha256Hash transactionHash;
        public final List<Integer> unspentTransactionOutputIndices;

        public UnspentTransactionOutputs(final Sha256Hash transactionHash, final List<Integer> unspentTransactionOutputIndices) {
            this.transactionHash = transactionHash;
            this.unspentTransactionOutputIndices = unspentTransactionOutputIndices.asConst();
        }
    }

    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected TransactionOutputId _getTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final TransactionOutputId cachedTransactionOutputId = _databaseManagerCache.getCachedTransactionOutputId(transactionId, transactionOutputIndex);
        if (cachedTransactionOutputId != null) { return cachedTransactionOutputId; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));

        _databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    protected TransactionOutputId _findUnspentTransactionOutput(final Sha256Hash transactionHash, final Integer transactionOutputIndex) throws DatabaseException {
        { // Attempt to find the UTXO from the in-memory cache...
            final TransactionOutputId cachedUnspentTransactionOutputId = _databaseManagerCache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
            if (cachedUnspentTransactionOutputId != null) { return cachedUnspentTransactionOutputId; }
            Logger.log("INFO: Cache Miss for Output: " + transactionHash + ":" + transactionOutputIndex);
            // Logger.log(new Exception());
            // BitcoinUtil.exitFailure();
        }

        final TransactionId cachedTransactionId = _databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) {
            final TransactionOutputId cachedTransactionOutputId = _databaseManagerCache.getCachedTransactionOutputId(cachedTransactionId, transactionOutputIndex);
            if (cachedTransactionOutputId != null) {
                return cachedTransactionOutputId;
            }
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, transaction_output_id FROM unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
                .setParameter(transactionHash)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));

        if (cachedTransactionId != null) {
            _databaseManagerCache.cacheTransactionOutputId(cachedTransactionId, transactionOutputIndex, transactionOutputId);
        }

        return transactionOutputId;
    }


    protected void _insertUnspentTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final Sha256Hash nullableTransactionHash, final Integer transactionOutputIndex) throws DatabaseException {
        final Sha256Hash transactionHash;
        if (nullableTransactionHash == null) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);
            transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
        }
        else {
            transactionHash = nullableTransactionHash;
        }

        _databaseConnection.executeSql(
            new Query("INSERT INTO unspent_transaction_outputs (transaction_output_id, transaction_hash, `index`) VALUES (?, ?, ?)")
                .setParameter(transactionOutputId)
                .setParameter(transactionHash)
                .setParameter(transactionOutputIndex)
        );

        _databaseManagerCache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
    }

    protected void _insertUnspentTransactionOutputs(final List<TransactionOutputId> transactionOutputIds, final List<UnspentTransactionOutputs> unspentTransactionOutputsList) throws DatabaseException {
        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_output_id, transaction_hash, `index`) VALUES (?, ?, ?)");
        int transactionOutputIdIndex = 0;
        for (final UnspentTransactionOutputs unspentTransactionOutputs : unspentTransactionOutputsList) {
            for (final Integer unspentTransactionOutputIndex : unspentTransactionOutputs.unspentTransactionOutputIndices) {
                final TransactionOutputId transactionOutputId = transactionOutputIds.get(transactionOutputIdIndex);

                batchedInsertQuery.setParameter(transactionOutputId);
                batchedInsertQuery.setParameter(unspentTransactionOutputs.transactionHash);
                batchedInsertQuery.setParameter(unspentTransactionOutputIndex);

                _databaseManagerCache.cacheUnspentTransactionOutputId(unspentTransactionOutputs.transactionHash, unspentTransactionOutputIndex, transactionOutputId);

                transactionOutputIdIndex += 1;
            }
        }

        _databaseConnection.executeSql(batchedInsertQuery);
    }

    protected TransactionOutputId _insertTransactionOutput(final TransactionId transactionId, final Sha256Hash nullableTransactionHash, final TransactionOutput transactionOutput) throws DatabaseException {
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

        _insertUnspentTransactionOutput(transactionOutputId, transactionId, nullableTransactionHash, transactionOutputIndex);

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

    protected Boolean _wasTransactionOutputSpentInAnyChain(final TransactionOutputId transactionOutputId) throws DatabaseException {
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
        return _insertTransactionOutput(transactionId, null, transactionOutput);
    }

    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final Sha256Hash transactionHash, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionHash, transactionOutput);
    }

    public List<TransactionOutputId> insertTransactionOutputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        if (! Util.areEqual(transactionIds.size(), transactions.getSize())) { return null; }
        if (transactions.isEmpty()) { return new MutableList<TransactionOutputId>(0); }

        final Integer transactionCount = transactions.getSize();

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)");

        final MutableList<UnspentTransactionOutputs> unspentTransactionOutputs = new MutableList<UnspentTransactionOutputs>(transactionCount * 2);
        final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>(transactionCount * 2);

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final Sha256Hash transactionHash = transaction.getHash();
            if (! transactionIds.containsKey(transactionHash)) { return null; }
            final TransactionId transactionId = transactionIds.get(transactionHash);

            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();

            final ImmutableListBuilder<Integer> unspentTransactionOutputIndicesListBuilder = new ImmutableListBuilder<Integer>(transactionOutputs.getSize());

            for (final TransactionOutput transactionOutput : transactionOutputs) {
                final Integer transactionOutputIndex = transactionOutput.getIndex();
                final Long transactionOutputAmount = transactionOutput.getAmount();
                final LockingScript lockingScript = transactionOutput.getLockingScript();

                unspentTransactionOutputIndicesListBuilder.add(transactionOutputIndex);
                lockingScripts.add(lockingScript);

                batchInsertQuery.setParameter(transactionId);
                batchInsertQuery.setParameter(transactionOutputIndex);
                batchInsertQuery.setParameter(transactionOutputAmount);
            }

            unspentTransactionOutputs.add(new UnspentTransactionOutputs(transactionHash, unspentTransactionOutputIndicesListBuilder.build()));
        }

        final Long firstTransactionOutputId = _databaseConnection.executeSql(batchInsertQuery);
        if (firstTransactionOutputId == null) { return null; }

        final Integer transactionOutputCount = lockingScripts.getSize();

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(transactionOutputCount);
        for (int i = 0; i < transactionOutputCount; ++i) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(firstTransactionOutputId + i);
            transactionOutputIds.add(transactionOutputId);
        }

        _insertUnspentTransactionOutputs(transactionOutputIds, unspentTransactionOutputs);

        _insertLockingScripts(transactionOutputIds, lockingScripts);

        return transactionOutputIds;
    }

    public TransactionOutputId findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

        final TransactionOutputId unspentTransactionOutputId = _findUnspentTransactionOutput(transactionHash, transactionOutputIndex);
        if (unspentTransactionOutputId != null) { return unspentTransactionOutputId; }

        Logger.log("INFO: Unspent Index Miss for Output: " + transactionHash + ":" + transactionOutputIndex);

        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);
        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        final TransactionOutputId transactionOutputId = _getTransactionOutputId(transactionId, transactionOutputIndex);
        return transactionOutputId;
    }

    public Map<TransactionOutputIdentifier, TransactionOutputId> getPreviousTransactionOutputs(final List<Transaction> transactions) throws DatabaseException {
        final Integer transactionCount = transactions.getSize();
        final HashMap<TransactionOutputIdentifier, TransactionOutputId> previousTransactionOutputsMap = new HashMap<TransactionOutputIdentifier, TransactionOutputId>(transactionCount * 2);
        final HashSet<TransactionOutputIdentifier> unfoundPreviousTransactionOutputs = new HashSet<TransactionOutputIdentifier>(transactionCount * 2);
        final MutableList<Sha256Hash> unfoundPreviousOutputTransactionHashes = new MutableList<Sha256Hash>(transactionCount * 2);
        for (final Transaction transaction : transactions) {
            for (final TransactionInput transactionInput : transaction.getTransactionInputs()) {
                final Sha256Hash transactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Integer outputIndex = transactionInput.getPreviousOutputIndex();

                if (Util.areEqual(Sha256Hash.EMPTY_HASH, transactionHash)) {
                    // if (! Util.areEqual(-1, outputIndex)) { return null; } // NOTE: This isn't actually enforced in any of the other reference clients...
                    continue;
                }

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                final TransactionOutputId cachedTransactionOutputId = _databaseManagerCache.getCachedUnspentTransactionOutputId(transactionHash, outputIndex);
                previousTransactionOutputsMap.put(transactionOutputIdentifier, cachedTransactionOutputId);

                if (cachedTransactionOutputId == null) {
                    unfoundPreviousOutputTransactionHashes.add(transactionHash);
                    unfoundPreviousTransactionOutputs.add(transactionOutputIdentifier);
                }
            }
        }

        { // Search the UTXO set for the TransactionOutputs...
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id, transaction_output_id, transaction_hash, `index` FROM unspent_transaction_outputs WHERE transaction_hash IN (" + DatabaseUtil.createInClause(unfoundPreviousOutputTransactionHashes) + ")")
            );
            for (final Row row : rows) {
                final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
                final Sha256Hash transactionHash = Sha256Hash.fromHexString(row.getString("transaction_hash"));
                final Integer index = row.getInteger("index");

                final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, index);
                previousTransactionOutputsMap.put(transactionOutputIdentifier, transactionOutputId);
                unfoundPreviousTransactionOutputs.remove(transactionOutputIdentifier);
            }
        }

        if (! unfoundPreviousTransactionOutputs.isEmpty()) {
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(_databaseConnection, _databaseManagerCache);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : unfoundPreviousTransactionOutputs) {
                final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                if (transactionId == null) {
                    Logger.log("Could not find Transaction for PreviousTransactionOutput: " + transactionHash);
                    return null;
                }

                final TransactionOutputId transactionOutputId = _getTransactionOutputId(transactionId, transactionOutputIdentifier.getOutputIndex());
                if (transactionOutputId == null) {
                    Logger.log("Could not find Transaction for PreviousTransactionOutput: " + transactionId + ":" + transactionOutputIdentifier.getOutputIndex());
                    return null;
                }

                previousTransactionOutputsMap.put(transactionOutputIdentifier, transactionOutputId);
            }
        }

        return previousTransactionOutputsMap;
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }

    public void markTransactionOutputAsSpent(final TransactionOutputId transactionOutputId, final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        if (transactionOutputId == null) { return; }

        _databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        _databaseManagerCache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
    }

    public void markTransactionOutputsAsSpent(final List<TransactionOutputId> transactionOutputIds, final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        if (transactionOutputIds.isEmpty()) { return; }

        final Query batchedUpdateQuery = new BatchedUpdateQuery("DELETE FROM unspent_transaction_outputs WHERE transaction_output_id IN (?)");
        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
            batchedUpdateQuery.setParameter(transactionOutputId);
        }

        _databaseConnection.executeSql(batchedUpdateQuery);

        _databaseManagerCache.invalidateUnspentTransactionOutputIds(transactionOutputIdentifiers);
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
        return _wasTransactionOutputSpentInAnyChain(transactionOutputId);
    }

    public void deleteTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        _databaseManagerCache.invalidateTransactionOutputIdCache();

        final Boolean transactionOutputWasSpent = _wasTransactionOutputSpentInAnyChain(transactionOutputId);
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

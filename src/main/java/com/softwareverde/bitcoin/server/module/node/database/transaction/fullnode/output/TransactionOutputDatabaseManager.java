package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.BatchedUpdateQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.input.TransactionInputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.slp.SlpTransactionDatabaseManager;
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
import com.softwareverde.bitcoin.transaction.script.ScriptTypeId;
import com.softwareverde.bitcoin.transaction.script.locking.ImmutableLockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.util.DatabaseUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionOutputDatabaseManager {

    protected static class UnspentTransactionOutputs {
        public final Sha256Hash transactionHash;
        public final List<Integer> unspentTransactionOutputIndices;

        public UnspentTransactionOutputs(final Sha256Hash transactionHash, final List<Integer> unspentTransactionOutputIndices) {
            this.transactionHash = transactionHash;
            this.unspentTransactionOutputIndices = unspentTransactionOutputIndices.asConst();
        }
    }

    protected final FullNodeDatabaseManager _databaseManager;

    protected TransactionOutputId _getTransactionOutputId(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final TransactionOutputId cachedTransactionOutputId = databaseManagerCache.getCachedTransactionOutputId(transactionId, transactionOutputIndex);
        if (cachedTransactionOutputId != null) { return cachedTransactionOutputId; }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id, `index` FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));

        databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    public static final AtomicInteger cacheMiss = new AtomicInteger(0);
    public static final AtomicInteger cacheHit = new AtomicInteger(0);

    protected TransactionOutputId _findUnspentTransactionOutput(final Sha256Hash transactionHash, final Integer transactionOutputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        { // Attempt to find the UTXO from the in-memory cache...
            final TransactionOutputId cachedUnspentTransactionOutputId = databaseManagerCache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
            if (cachedUnspentTransactionOutputId != null) { cacheHit.incrementAndGet(); return cachedUnspentTransactionOutputId; }
            // Logger.debug("Cache Miss for Output: " + transactionHash + ":" + transactionOutputIndex);
        }

        final TransactionId cachedTransactionId = databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) {
            final TransactionOutputId cachedTransactionOutputId = databaseManagerCache.getCachedTransactionOutputId(cachedTransactionId, transactionOutputIndex);
            if (cachedTransactionOutputId != null) {
                return cachedTransactionOutputId;
            }
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_output_id FROM unspent_transaction_outputs WHERE transaction_hash = ? AND `index` = ?")
                .setParameter(transactionHash)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));

        if (cachedTransactionId != null) {
            databaseManagerCache.cacheTransactionOutputId(cachedTransactionId, transactionOutputIndex, transactionOutputId);
        }

        cacheMiss.incrementAndGet();
        return transactionOutputId;
    }


    protected void _insertUnspentTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final Sha256Hash nullableTransactionHash, final Integer transactionOutputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final Sha256Hash transactionHash;
        if (nullableTransactionHash == null) {
            transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
        }
        else {
            transactionHash = nullableTransactionHash;
        }

        databaseConnection.executeSql(
            new Query("INSERT INTO unspent_transaction_outputs (transaction_output_id, transaction_hash, `index`) VALUES (?, ?, ?)")
                .setParameter(transactionOutputId)
                .setParameter(transactionHash)
                .setParameter(transactionOutputIndex)
        );

        databaseManagerCache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
    }

    protected void _insertUnspentTransactionOutputs(final List<TransactionOutputId> transactionOutputIds, final List<UnspentTransactionOutputs> unspentTransactionOutputsList) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_output_id, transaction_hash, `index`) VALUES (?, ?, ?)");
        int transactionOutputIdIndex = 0;
        for (final UnspentTransactionOutputs unspentTransactionOutputs : unspentTransactionOutputsList) {
            for (final Integer unspentTransactionOutputIndex : unspentTransactionOutputs.unspentTransactionOutputIndices) {
                final TransactionOutputId transactionOutputId = transactionOutputIds.get(transactionOutputIdIndex);

                batchedInsertQuery.setParameter(transactionOutputId);
                batchedInsertQuery.setParameter(unspentTransactionOutputs.transactionHash);
                batchedInsertQuery.setParameter(unspentTransactionOutputIndex);

                databaseManagerCache.cacheUnspentTransactionOutputId(unspentTransactionOutputs.transactionHash, unspentTransactionOutputIndex, transactionOutputId);

                transactionOutputIdIndex += 1;
            }
        }

        databaseConnection.executeSql(batchedInsertQuery);
    }

    protected TransactionOutputId _insertTransactionOutput(final TransactionId transactionId, final Sha256Hash nullableTransactionHash, final TransactionOutput transactionOutput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final Integer transactionOutputIndex = transactionOutput.getIndex();

        final Long transactionOutputIdLong = databaseConnection.executeSql(
            new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
                .setParameter(transactionOutput.getAmount())
        );

        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionOutputIdLong);
        if (transactionOutputId == null) { return null; }

        _insertUnspentTransactionOutput(transactionOutputId, transactionId, nullableTransactionHash, transactionOutputIndex);

        _insertLockingScript(transactionOutputId, lockingScript);

        databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    protected LockingScript _getLockingScript(final LockingScriptId lockingScriptId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, script FROM locking_scripts WHERE id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return new ImmutableLockingScript(MutableByteArray.wrap(row.getBytes("script")));
    }

    protected void _insertLockingScript(final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final ScriptTypeId scriptTypeId = ScriptType.UNKNOWN.getScriptTypeId();

        final ByteArray lockingScriptByteArray = lockingScript.getBytes();
        final Long lockingScriptId = databaseConnection.executeSql(
            new Query("INSERT INTO locking_scripts (script_type_id, transaction_output_id, script, address_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?)")
                .setParameter(scriptTypeId)
                .setParameter(transactionOutputId)
                .setParameter(lockingScriptByteArray.getBytes())
                .setParameter(Query.NULL) // addressId
                .setParameter(Query.NULL) // slpTransactionId
        );

        databaseConnection.executeSql(
            new Query("INSERT INTO address_processor_queue (locking_script_id) VALUES (?)")
                .setParameter(lockingScriptId)
        );
    }

    protected TransactionId _getTransactionId(final LockingScriptId lockingScriptId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_outputs.transaction_id FROM locking_scripts INNER JOIN transaction_outputs ON locking_scripts.transaction_output_id = transaction_outputs.id WHERE locking_scripts.id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    protected void _updateLockingScript(final TransactionId transactionId, final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final AddressDatabaseManager addressDatabaseManager = _databaseManager.getAddressDatabaseManager();
        final SlpTransactionDatabaseManager slpTransactionDatabaseManager = _databaseManager.getSlpTransactionDatabaseManager();

        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();

        final TransactionId slpTokenTransactionId;
        final AddressId addressId;
        switch (scriptType) {
            case PAY_TO_PUBLIC_KEY:
            case PAY_TO_SCRIPT_HASH:
            case PAY_TO_PUBLIC_KEY_HASH: {
                addressId = addressDatabaseManager.storeScriptAddress(lockingScript);
                slpTokenTransactionId = null;
            } break;

            case UNKNOWN: {
                addressId = null;
                slpTokenTransactionId = null;
            } break;

            default: {
                slpTokenTransactionId = slpTransactionDatabaseManager.calculateSlpTokenGenesisTransactionId(transactionId, lockingScript);
                addressId = null;
            }
        }

        final ByteArray lockingScriptByteArray = lockingScript.getBytes();
        databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET script_type_id = ?, script = ?, address_id = ?, slp_transaction_id = ? WHERE transaction_output_id = ?")
                .setParameter(scriptTypeId)
                .setParameter(lockingScriptByteArray.getBytes())
                .setParameter(addressId)
                .setParameter(slpTokenTransactionId)
                .setParameter(transactionOutputId)
        );
    }

    protected void _insertLockingScripts(final List<TransactionOutputId> transactionOutputIds, final List<LockingScript> lockingScripts) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (! Util.areEqual(transactionOutputIds.getSize(), lockingScripts.getSize())) {
            throw new DatabaseException("Attempted to insert LockingScripts without matching TransactionOutputIds.");
        }

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO locking_scripts (script_type_id, transaction_output_id, script, address_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?)");

        // final AddressDatabaseManager addressDatabaseManager = new AddressDatabaseManager(databaseConnection, databaseManagerCache);
        // final List<AddressId> addressIds = addressDatabaseManager.storeScriptAddresses(lockingScripts);
        // final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        for (int i = 0; i < transactionOutputIds.getSize(); ++i) {
            final TransactionOutputId transactionOutputId = transactionOutputIds.get(i);
            final LockingScript lockingScript = lockingScripts.get(i);

            // final AddressId addressId = addressIds.get(i);
            // final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);

            final ScriptType scriptType = ScriptType.UNKNOWN;
            final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();

            final ByteArray lockingScriptByteArray = lockingScript.getBytes();
            batchInsertQuery.setParameter(scriptTypeId);
            batchInsertQuery.setParameter(transactionOutputId);
            batchInsertQuery.setParameter(lockingScriptByteArray.getBytes());
            batchInsertQuery.setParameter(Query.NULL); // addressId
            batchInsertQuery.setParameter(Query.NULL); // slpTransactionId
        }

        final Long firstLockingScriptId = databaseConnection.executeSql(batchInsertQuery);
        final Integer insertCount = databaseConnection.getRowsAffectedCount();

        { // Queue LockingScript for address processing...
            final Query addressProcessorQueueQuery = new BatchedInsertQuery("INSERT INTO address_processor_queue (locking_script_id) VALUES (?)");
            for (int i = 0; i < insertCount; ++i) {
                final LockingScriptId lockingScriptId = LockingScriptId.wrap(firstLockingScriptId + i);
                addressProcessorQueueQuery.setParameter(lockingScriptId);
            }
            databaseConnection.executeSql(addressProcessorQueueQuery);
        }

    }

    protected TransactionOutput _getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Row transactionOutputRow;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT * FROM transaction_outputs WHERE id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            transactionOutputRow = rows.get(0);
        }

        final LockingScript lockingScript;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, script FROM locking_scripts WHERE transaction_output_id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(row.getBytes("script")));
        }

        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setIndex(transactionOutputRow.getInteger("index"));
        mutableTransactionOutput.setAmount(transactionOutputRow.getLong("amount"));
        mutableTransactionOutput.setLockingScript(lockingScript);
        return mutableTransactionOutput;
    }

    protected Boolean _wasTransactionOutputSpentInAnyChain(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final TransactionInputDatabaseManager transactionInputDatabaseManager = _databaseManager.getTransactionInputDatabaseManager();

        final List<TransactionInputId> transactionInputIds = transactionInputDatabaseManager.getTransactionInputIdsSpendingTransactionOutput(transactionOutputId);
        final Boolean transactionOutputIsSpent = (! transactionInputIds.isEmpty());
        return transactionOutputIsSpent;
    }

    public TransactionOutputDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, null, transactionOutput);
    }

    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final Sha256Hash transactionHash, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionHash, transactionOutput);
    }

    public List<TransactionOutputId> insertTransactionOutputs(final Map<Sha256Hash, TransactionId> transactionIds, final List<Transaction> transactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (! Util.areEqual(transactionIds.size(), transactions.getSize())) {
            Logger.warn("Error storing TransactionOutputs. Parameter mismatch: expected " + transactionIds.size() + ", got " + transactions.getSize());
            return null;
        }
        if (transactions.isEmpty()) { return new MutableList<TransactionOutputId>(0); }

        final Integer transactionCount = transactions.getSize();

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)");

        final MutableList<UnspentTransactionOutputs> unspentTransactionOutputs = new MutableList<UnspentTransactionOutputs>(transactionCount * 2);
        final MutableList<LockingScript> lockingScripts = new MutableList<LockingScript>(transactionCount * 2);

        for (int i = 0; i < transactionCount; ++i) {
            final Transaction transaction = transactions.get(i);
            final Sha256Hash transactionHash = transaction.getHash();
            if (! transactionIds.containsKey(transactionHash)) {
                Logger.warn("Error storing TransactionOutputs. Missing Transaction: " + transactionHash);
                return null;
            }
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

        final Long firstTransactionOutputId = databaseConnection.executeSql(batchInsertQuery);
        if (firstTransactionOutputId == null) {
            Logger.warn("Error storing TransactionOutputs. Error running batch insert.");
            return null;
        }

        final Integer transactionOutputCount = lockingScripts.getSize();

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>(transactionOutputCount);
        for (int i = 0; i < transactionOutputCount; ++i) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(firstTransactionOutputId + i);
            transactionOutputIds.add(transactionOutputId);
        }

        // TODO: Do not cache provably unspendable outputs...
        _insertUnspentTransactionOutputs(transactionOutputIds, unspentTransactionOutputs);

        _insertLockingScripts(transactionOutputIds, lockingScripts);

        return transactionOutputIds;
    }

    public TransactionOutputId findTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
        final Integer transactionOutputIndex = transactionOutputIdentifier.getOutputIndex();

        final TransactionOutputId unspentTransactionOutputId = _findUnspentTransactionOutput(transactionHash, transactionOutputIndex);
        if (unspentTransactionOutputId != null) { return unspentTransactionOutputId; }

        // Logger.debug("Unspent Index Miss for Output: " + transactionHash + ":" + transactionOutputIndex);

        final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
        if (transactionId == null) { return null; }

        return _getTransactionOutputId(transactionId, transactionOutputIndex);
    }

    public Map<TransactionOutputIdentifier, TransactionOutputId> getPreviousTransactionOutputs(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final ConcurrentHashMap<TransactionOutputIdentifier, TransactionOutputId> transactionOutputIds = new ConcurrentHashMap<TransactionOutputIdentifier, TransactionOutputId>();

        final BatchRunner<TransactionOutputIdentifier> batchRunner = new BatchRunner<TransactionOutputIdentifier>(512);
        batchRunner.run(transactionOutputIdentifiers, new BatchRunner.Batch<TransactionOutputIdentifier>() {
            @Override
            public void run(final List<TransactionOutputIdentifier> batchItems) throws DatabaseException {
                final java.util.List<Row> rows;
                final Query query = new Query("SELECT transactions.hash, transaction_outputs.id AS transaction_output_id, transaction_outputs.`index` AS transaction_output_index FROM transactions INNER JOIN transaction_outputs ON (transactions.id = transaction_outputs.transaction_id) WHERE (transactions.hash, transaction_outputs.`index`) IN (" + DatabaseUtil.createInTupleClause(batchItems) + ")");

                final DatabaseConnectionFactory connectionFactory = _databaseManager.getDatabaseConnectionFactory();
                if (connectionFactory != null) {
                    try (final DatabaseConnection databaseConnection = connectionFactory.newConnection()) {
                        rows = databaseConnection.query(query);
                    }
                }
                else {
                    Logger.debug("DatabaseConnectionFactory not set, falling back to synchronous database connection.");
                    final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
                    rows = databaseConnection.query(query);
                }

                for (final Row row : rows) {
                    final Sha256Hash previousTransactionHash = Sha256Hash.fromHexString(row.getString("hash"));
                    final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
                    final Integer transactionOutputIndex = row.getInteger("transaction_output_index");

                    final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(previousTransactionHash, transactionOutputIndex);
                    transactionOutputIds.put(transactionOutputIdentifier, transactionOutputId);
                }
            }
        });

        return transactionOutputIds;
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }

    public TransactionOutput getTransactionOutput(final TransactionId transactionId, final Integer outputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(outputIndex)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));
        return _getTransactionOutput(transactionOutputId);
    }

    public void markTransactionOutputAsSpent(final TransactionOutputId transactionOutputId, final TransactionOutputIdentifier transactionOutputIdentifier) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        if (transactionOutputId == null) { return; }

        databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        databaseManagerCache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
    }

    public void markTransactionOutputsAsSpent(final List<TransactionOutputId> transactionOutputIds, final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        if (transactionOutputIds.isEmpty()) { return; }

        final Query batchedUpdateQuery = new BatchedUpdateQuery("DELETE FROM unspent_transaction_outputs WHERE transaction_output_id IN (?)");
        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
            batchedUpdateQuery.setParameter(transactionOutputId);
        }

        databaseConnection.executeSql(batchedUpdateQuery);

        databaseManagerCache.invalidateUnspentTransactionOutputIds(transactionOutputIdentifiers);
    }

    public List<TransactionOutputId> getTransactionOutputIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? ORDER BY `index` ASC")
                .setParameter(transactionId)
        );

        final ImmutableListBuilder<TransactionOutputId> transactionOutputIds = new ImmutableListBuilder<>(rows.size());
        for (final Row row : rows) {
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("id"));
            transactionOutputIds.add(transactionOutputId);
        }

        return transactionOutputIds.build();
    }

    public void updateTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        final LockingScript lockingScript = transactionOutput.getLockingScript();

        databaseConnection.executeSql(
            new Query("UPDATE transaction_outputs SET transaction_id = ?, `index` = ?, amount = ? WHERE id = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutput.getIndex())
                .setParameter(transactionOutput.getAmount())
                .setParameter(transactionOutputId)
        );

        _updateLockingScript(transactionId, transactionOutputId, lockingScript);
    }

    public Boolean isTransactionOutputSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _wasTransactionOutputSpentInAnyChain(transactionOutputId);
    }

    public void deleteTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        // final Boolean transactionOutputWasSpent = _wasTransactionOutputSpentInAnyChain(transactionOutputId);
        // if (transactionOutputWasSpent) {
        //     throw new DatabaseException("Cannot delete spent TransactionOutput: " + transactionOutputId);
        // }
        //
        // databaseConnection.executeSql(
        //     new Query("DELETE FROM locking_scripts WHERE transaction_output_id = ?")
        //         .setParameter(transactionOutputId)
        // );

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
    }

    public void deleteTransactionOutputs(final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_outputs WHERE id IN (" + DatabaseUtil.createInClause(transactionOutputIds) + ")")
        );
    }

    public TransactionId getTransactionId(final LockingScriptId lockingScriptId) throws DatabaseException {
        return _getTransactionId(lockingScriptId);
    }

    public List<LockingScriptId> getLockingScriptsWithUnprocessedTypes(final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT locking_script_id FROM address_processor_queue ORDER BY id ASC LIMIT " + Util.coalesce(maxCount, 1))
        );

        final ImmutableListBuilder<LockingScriptId> lockingScriptIds = new ImmutableListBuilder<LockingScriptId>(rows.size());
        for (final Row row : rows) {
            final LockingScriptId lockingScriptId = LockingScriptId.wrap(row.getLong("locking_script_id"));
            lockingScriptIds.add(lockingScriptId);
        }

        return lockingScriptIds.build();
    }

    public void setLockingScriptType(final LockingScriptId lockingScriptId, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();

        databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET script_type_id = ?, address_id = ?, slp_transaction_id = ? WHERE id = ?")
                .setParameter(scriptTypeId)
                .setParameter(addressId)
                .setParameter(slpTransactionId)
                .setParameter(lockingScriptId)
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM address_processor_queue WHERE locking_script_id = ?")
                .setParameter(lockingScriptId)
        );
    }

    public void setLockingScriptTypes(final List<LockingScriptId> lockingScriptIds, final List<ScriptType> scriptTypes, final List<AddressId> addressIds, final List<TransactionId> slpTransactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Integer lockingScriptCount = lockingScriptIds.getSize();
        if ( (! Util.areEqual(lockingScriptCount, scriptTypes.getSize())) || (! Util.areEqual(lockingScriptCount, addressIds.getSize())) || (! Util.areEqual(lockingScriptCount, slpTransactionIds.getSize())) )  {
            throw new DatabaseException("Attempting to update LockingScriptTypes with mismatching Ids.");
        }
        if (lockingScriptIds.isEmpty()) { return; }

        for (int i = 0; i < lockingScriptCount; ++i) {
            final LockingScriptId lockingScriptId = lockingScriptIds.get(i);
            final ScriptType scriptType = scriptTypes.get(i);
            final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();
            final AddressId addressId = addressIds.get(i);
            final TransactionId slpTransactionId = slpTransactionIds.get(i);

            databaseConnection.executeSql(
                new Query("UPDATE locking_scripts SET script_type_id = ?, address_id = ?, slp_transaction_id = ? WHERE id = ?")
                    .setParameter(scriptTypeId)
                    .setParameter(addressId)
                    .setParameter(slpTransactionId)
                    .setParameter(lockingScriptId)
            );
        }

        databaseConnection.executeSql(
            new Query("DELETE FROM address_processor_queue WHERE locking_script_id IN (" + DatabaseUtil.createInClause(lockingScriptIds) + ")")
        );
    }

    public LockingScript getLockingScript(final LockingScriptId lockingScriptId) throws DatabaseException {
        return _getLockingScript(lockingScriptId);
    }

    public List<LockingScript> getLockingScripts(final List<LockingScriptId> lockingScriptIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final int scriptCount = lockingScriptIds.getSize();
        final HashMap<LockingScriptId, LockingScript> keyMap = new HashMap<LockingScriptId, LockingScript>(scriptCount);
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, script FROM locking_scripts WHERE id IN (" + DatabaseUtil.createInClause(lockingScriptIds, keyMap) + ")")
        );
        if (rows.size() != scriptCount) { return null; }

        for (final Row row : rows) {
            final LockingScriptId lockingScriptId = LockingScriptId.wrap(row.getLong("id"));
            final LockingScript lockingScript = new ImmutableLockingScript(MutableByteArray.wrap(row.getBytes("script")));
            keyMap.put(lockingScriptId, lockingScript);
        }

        return DatabaseUtil.sortMappedRows(rows, lockingScriptIds, keyMap);
    }

    public TransactionOutputId getTransactionOutputId(final LockingScriptId lockingScriptId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_output_id FROM locking_scripts WHERE id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long transactionOutputId = row.getLong("transaction_output_id");
        return TransactionOutputId.wrap(transactionOutputId);
    }

    public TransactionId getTransactionId(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id FROM transaction_outputs WHERE id = ?")
                .setParameter(transactionOutputId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long transactionId = row.getLong("transaction_id");
        return TransactionId.wrap(transactionId);
    }
}

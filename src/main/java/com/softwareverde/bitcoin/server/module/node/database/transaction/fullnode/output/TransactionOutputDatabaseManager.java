package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.output;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
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
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), transactionOutputIndex);

        databaseManagerCache.cacheTransactionOutputId(transactionId, transactionOutputIndex, transactionOutputId);

        return transactionOutputId;
    }

    public static final AtomicInteger cacheMiss = new AtomicInteger(0);
    public static final AtomicInteger cacheHit = new AtomicInteger(0);

    protected TransactionOutputId _findUnspentTransactionOutput(final Sha256Hash transactionHash, final Integer transactionOutputIndex) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final TransactionId cachedTransactionId = databaseManagerCache.getCachedTransactionId(transactionHash.asConst());
        if (cachedTransactionId != null) {
            final TransactionOutputId cachedTransactionOutputId = databaseManagerCache.getCachedTransactionOutputId(cachedTransactionId, transactionOutputIndex);
            if (cachedTransactionOutputId != null) {
                return cachedTransactionOutputId;
            }
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_id, transaction_output_index FROM unspent_transaction_outputs WHERE transaction_hash = ? AND transaction_output_index = ?")
                .setParameter(transactionHash)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long transactionId = row.getLong("transaction_id");
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId, transactionOutputIndex);

        if (cachedTransactionId != null) {
            databaseManagerCache.cacheTransactionOutputId(cachedTransactionId, transactionOutputIndex, transactionOutputId);
        }

        cacheMiss.incrementAndGet();
        return transactionOutputId;
    }


    protected void _insertUnspentTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionId transactionId, final Sha256Hash nullableTransactionHash) throws DatabaseException {
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
            new Query("INSERT INTO unspent_transaction_outputs (transaction_id, transaction_output_index, transaction_hash) VALUES (?, ?, ?)")
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
                .setParameter(transactionHash)
        );
    }

    protected void _insertUnspentTransactionOutputs(final List<TransactionOutputId> transactionOutputIds, final List<UnspentTransactionOutputs> unspentTransactionOutputsList) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO unspent_transaction_outputs (transaction_id, transaction_output_index, transaction_hash) VALUES (?, ?, ?)");
        int transactionOutputIdIndex = 0;
        for (final UnspentTransactionOutputs unspentTransactionOutputs : unspentTransactionOutputsList) {
            for (final Integer unspentTransactionOutputIndex : unspentTransactionOutputs.unspentTransactionOutputIndices) {
                final TransactionOutputId transactionOutputId = transactionOutputIds.get(transactionOutputIdIndex);

                batchedInsertQuery.setParameter(transactionOutputId.getTransactionId());
                batchedInsertQuery.setParameter(transactionOutputId.getOutputIndex());
                batchedInsertQuery.setParameter(unspentTransactionOutputs.transactionHash);

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

        databaseConnection.executeSql(
            new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
                .setParameter(transactionOutput.getAmount())
        );

        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), transactionOutputIndex);

        _insertUnspentTransactionOutput(transactionOutputId, transactionId, nullableTransactionHash);

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
            new Query("INSERT INTO locking_scripts (script_type_id, transaction_id, transaction_output_index, script, address_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?)")
                .setParameter(scriptTypeId)
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
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
            new Query("SELECT transaction_id FROM locking_scripts WHERE locking_scripts.id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("transaction_id"));
    }

    protected void _updateLockingScript(final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
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
                slpTokenTransactionId = slpTransactionDatabaseManager.calculateSlpTokenGenesisTransactionId(transactionOutputId.getTransactionId(), lockingScript);
                addressId = null;
            }
        }

        final ByteArray lockingScriptByteArray = lockingScript.getBytes();
        databaseConnection.executeSql(
            new Query("UPDATE locking_scripts SET script_type_id = ?, script = ?, address_id = ?, slp_transaction_id = ? WHERE transaction_id = ? AND transaction_output_index = ?")
                .setParameter(scriptTypeId)
                .setParameter(lockingScriptByteArray.getBytes())
                .setParameter(addressId)
                .setParameter(slpTokenTransactionId)
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );
    }

    protected void _insertLockingScripts(final List<TransactionOutputId> transactionOutputIds, final List<LockingScript> lockingScripts) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (! Util.areEqual(transactionOutputIds.getSize(), lockingScripts.getSize())) {
            throw new DatabaseException("Attempted to insert LockingScripts without matching TransactionOutputIds.");
        }

        final Query batchInsertQuery = new BatchedInsertQuery("INSERT INTO locking_scripts (script_type_id, transaction_id, transaction_output_index, script, address_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?, ?)");

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
            batchInsertQuery.setParameter(transactionOutputId.getTransactionId());
            batchInsertQuery.setParameter(transactionOutputId.getOutputIndex());
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
                new Query("SELECT * FROM transaction_outputs WHERE transaction_id = ? AND transaction_output_index = ?")
                    .setParameter(transactionOutputId.getTransactionId())
                    .setParameter(transactionOutputId.getOutputIndex())
            );
            if (rows.isEmpty()) { return null; }

            transactionOutputRow = rows.get(0);
        }

        final LockingScript lockingScript;
        {
            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id, script FROM locking_scripts WHERE transaction_id = ? AND transaction_output_index = ?")
                    .setParameter(transactionOutputId.getTransactionId())
                    .setParameter(transactionOutputId.getOutputIndex())
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

    protected Map<TransactionId, Integer> _getTransactionOutputCounts(final Iterable<TransactionId> transactionIds) throws  DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id, COUNT(*) AS output_count FROM transaction_outputs WHERE transaction_id IN (" + DatabaseUtil.createInClause(transactionIds) + ") GROUP BY transaction_id")
        );

        final HashMap<TransactionId, Integer> transactionOutputCounts = new HashMap<TransactionId, Integer>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            final Integer outputCount = row.getInteger("output_count");
            transactionOutputCounts.put(transactionId, outputCount);
        }

        return transactionOutputCounts;
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

        final MutableList<TransactionOutputId> transactionOutputIds = new MutableList<TransactionOutputId>();

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

                final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), transactionOutputIndex);
                transactionOutputIds.add(transactionOutputId);
            }

            unspentTransactionOutputs.add(new UnspentTransactionOutputs(transactionHash, unspentTransactionOutputIndicesListBuilder.build()));
        }

        databaseConnection.executeSql(batchInsertQuery);

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

    /**
     * Returns the TransactionOutputIds that for the provided list of TransactionOutputIdentifiers.
     *  If TransactionOutputId could not be found, then null is returned for the whole map.
     *  The set of TransactionOutputIdentifiers should not include Coinbase identifiers.
     */
    public Map<TransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) throws DatabaseException {
        final int identifierCount = transactionOutputIdentifiers.getSize();
        final HashMap<TransactionOutputIdentifier, TransactionOutputId> transactionOutputIds = new HashMap<TransactionOutputIdentifier, TransactionOutputId>(identifierCount);

        final List<Sha256Hash> transactionHashes;
        {
            final HashSet<Sha256Hash> transactionHashesSet = new HashSet<Sha256Hash>(identifierCount);
            for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
                transactionHashesSet.add(transactionOutputIdentifier.getTransactionHash());
            }
            transactionHashes = new ImmutableList<Sha256Hash>(transactionHashesSet);
        }

        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final Map<Sha256Hash, TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(transactionHashes);

        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            final TransactionId transactionId = transactionIds.get(transactionOutputIdentifier.getTransactionHash());
            if (transactionId == null) {
                Logger.debug("Could not find TransactionOutput: " + transactionOutputIdentifier);
                return null;
            }

            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), transactionOutputIdentifier.getOutputIndex());
            transactionOutputIds.put(transactionOutputIdentifier, transactionOutputId);
        }

        final Map<TransactionId, Integer> transactionOutputCounts = _getTransactionOutputCounts(transactionIds.values());
        for (final TransactionOutputIdentifier transactionOutputIdentifier : transactionOutputIdentifiers) {
            final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
            final TransactionId transactionId = transactionIds.get(transactionHash);
            final Integer transactionOutputCount = transactionOutputCounts.get(transactionId);

            final Integer outputIndex = transactionOutputIdentifier.getOutputIndex();
            if ( (outputIndex >= transactionOutputCount) || (outputIndex < 0) ) {
                Logger.debug("Could not find TransactionOutput: " + transactionOutputIdentifier);
                return null;
            }
        }
        // TODO: Ensure the output count for each transaction is greater than the outputIdentifier's index...

        return transactionOutputIds;
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }

    public TransactionOutput getTransactionOutput(final TransactionId transactionId, final Integer outputIndex) throws DatabaseException {
        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), outputIndex);
        return _getTransactionOutput(transactionOutputId);
    }

    public void markTransactionOutputAsSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (transactionOutputId == null) { return; }

        databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs WHERE transaction_id = ? AND transaction_output_index = ?")
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );
    }

    public void markTransactionOutputsAsSpent(final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (transactionOutputIds.isEmpty()) { return; }

        final String inClause = DatabaseUtil.createInClause(transactionOutputIds, new DatabaseUtil.ValueExtractor<TransactionOutputId>() {
            @Override
            public Tuple<Object, Object> extractValues(final TransactionOutputId transactionOutputId) {
                return new Tuple<Object, Object>(transactionOutputId.getTransactionId(), transactionOutputId.getOutputIndex());
            }
        });

        databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs WHERE (transaction_id, transaction_output_index) IN (" + inClause + ")")
        );
    }

    public List<TransactionOutputId> getTransactionOutputIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id, `index` FROM transaction_outputs WHERE transaction_id = ? ORDER BY `index` ASC")
                .setParameter(transactionId)
        );

        final ImmutableListBuilder<TransactionOutputId> transactionOutputIds = new ImmutableListBuilder<>(rows.size());
        for (final Row row : rows) {
            final Integer outputIndex = row.getInteger("index");
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionId.longValue(), outputIndex);
            transactionOutputIds.add(transactionOutputId);
        }

        return transactionOutputIds.build();
    }

    public void updateTransactionOutput(final TransactionOutputId transactionOutputId, final TransactionOutput transactionOutput) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        final LockingScript lockingScript = transactionOutput.getLockingScript();

        databaseConnection.executeSql(
            new Query("UPDATE transaction_outputs SET amount = ? WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionOutput.getAmount())
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );

        _updateLockingScript(transactionOutputId, lockingScript);
    }

    public Boolean isTransactionOutputSpent(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _wasTransactionOutputSpentInAnyChain(transactionOutputId);
    }

    public void deleteTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        databaseConnection.executeSql(
            new Query("DELETE FROM locking_scripts WHERE transaction_id = ? AND transaction_output_index = ?")
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM unspent_transaction_outputs WHERE transaction_id = ? AND transaction_output_index = ?")
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionOutputId.getTransactionId())
                .setParameter(transactionOutputId.getOutputIndex())
        );
    }

    public void deleteTransactionOutputs(final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        databaseManagerCache.invalidateTransactionOutputIdCache();

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_outputs WHERE (transaction_id, `index`) IN (" + DatabaseUtil.createInClause(transactionOutputIds, DatabaseUtil.Extractors.TransactionOutputId) + ")")
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
            new Query("SELECT id, transaction_id, transaction_output_index FROM locking_scripts WHERE id = ?")
                .setParameter(lockingScriptId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long transactionId = row.getLong("transaction_id");
        final Integer transactionOutputIndex = row.getInteger("transaction_output_index");
        return TransactionOutputId.wrap(transactionId, transactionOutputIndex);
    }

    public TransactionId getTransactionId(final TransactionOutputId transactionOutputId) throws DatabaseException {
        if (transactionOutputId == null) { return null; }
        return transactionOutputId.getTransactionId();
    }
}

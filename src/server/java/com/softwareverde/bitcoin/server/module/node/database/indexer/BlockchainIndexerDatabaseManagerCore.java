package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.BatchRunner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.ScriptTypeId;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.constable.set.mutable.MutableTreeSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;



public class BlockchainIndexerDatabaseManagerCore implements BlockchainIndexerDatabaseManager {
    protected static final Boolean STORE_ADDRESS_IS_ENABLED = false; // Since Electrum requires script hash, storing address is redundant and is therefore disabled in order to save space.
    protected static final String LAST_INDEXED_TRANSACTION_KEY = "last_indexed_transaction_id";

    protected final FullNodeDatabaseManager _databaseManager;
    protected final AddressInflater _addressInflater;

    public BlockchainIndexerDatabaseManagerCore(final AddressInflater addressInflater, final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
        _addressInflater = addressInflater;
    }

    /**
     * Inflates a unique set of TransactionId -> BlockchainSegmentId from the provided rows.
     *  Each row must contain two key/value sets, with labels: {blockchain_segment_id, transaction_id}
     */
    protected final Set<Tuple<TransactionId, BlockchainSegmentId>> _extractTransactionBlockchainSegmentIds(final java.util.List<Row> rows) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final MutableTreeSet<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = new MutableTreeSet<>();
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            final BlockchainSegmentId transactionBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));
            final Tuple<TransactionId, BlockchainSegmentId> tuple = new Tuple<>(transactionId, transactionBlockchainSegmentId);

            // For pruned+index mode, some outputs will have had their transaction data pruned; if the output is in the UTXO database, then it is on the head blockchain segment.
            if (transactionBlockchainSegmentId == null) {
                final Boolean isUnconfirmedTransaction = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
                if (! isUnconfirmedTransaction) {
                    final java.util.List<String> availableColumns = row.getColumnNames();
                    final boolean isDebitOutput = availableColumns.contains("output_index"); // Spent previous output rows cannot be in the UTXO set...
                    if (isDebitOutput) {
                        final Integer outputIndex = row.getInteger("output_index");
                        final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
                        final TransactionOutputIdentifier transactionOutputIdentifier = new TransactionOutputIdentifier(transactionHash, outputIndex);

                        final Object transactionOutput = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutput(transactionOutputIdentifier);
                        if (transactionOutput != null) {
                            tuple.second = headBlockchainSegmentId;
                        }
                    }
                }
            }

            transactionBlockchainSegmentIds.add(tuple);
        }
        return transactionBlockchainSegmentIds;
    }

    protected Set<TransactionId> _filterTransactionsConnectedToBlockchainSegment(final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds, final BlockchainSegmentId blockchainSegmentId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final MutableHashMap<BlockchainSegmentId, Boolean> connectedBlockchainSegmentIds = new MutableHashMap<>(); // Used to cache the lookup result of connected BlockchainSegments.
        final MutableTreeSet<TransactionId> transactionIds = new MutableTreeSet<>();

        // Remove BlockchainSegments that are not connected to the desired blockchainSegmentId, and ensure TransactionIds are unique...
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        for (final Tuple<TransactionId, BlockchainSegmentId> tuple : transactionBlockchainSegmentIds) {
            final TransactionId transactionId = tuple.first;
            final BlockchainSegmentId transactionBlockchainSegmentId = tuple.second;

            if (transactionBlockchainSegmentId == null) { // If Transaction was not attached to a block...
                if (! includeUnconfirmedTransactions) { // If unconfirmedTransactions are excluded, then remove the transaction...
                    continue;
                }
                else { // Exclude if the transaction is not in the mempool...
                    final Boolean transactionIsUnconfirmed = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
                    if (! transactionIsUnconfirmed) {
                        continue;
                    }
                }
            }
            else { // If the BlockchainSegment is not connected to the desired blockchainSegment then remove it...
                final Boolean transactionIsConnectedToBlockchainSegment;
                {
                    final Boolean cachedIsConnectedToBlockchainSegment = connectedBlockchainSegmentIds.get(transactionBlockchainSegmentId);
                    if (cachedIsConnectedToBlockchainSegment != null) {
                        transactionIsConnectedToBlockchainSegment = cachedIsConnectedToBlockchainSegment;
                    }
                    else {
                        final Boolean isConnectedToBlockchainSegment = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, transactionBlockchainSegmentId, BlockRelationship.ANY);
                        transactionIsConnectedToBlockchainSegment = isConnectedToBlockchainSegment;
                        connectedBlockchainSegmentIds.put(transactionBlockchainSegmentId, isConnectedToBlockchainSegment);
                    }
                }

                if (! transactionIsConnectedToBlockchainSegment) {
                    continue;
                }
            }

            transactionIds.add(transactionId);
        }

        return transactionIds;
    }

    protected AddressTransactions _getAddressTransactions(final BlockchainSegmentId blockchainSegmentId, final TypedAddress address, final Sha256Hash nullableScriptHash, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Sha256Hash scriptHash;
        if (nullableScriptHash == null) { // Reverse engineer the Address to a scriptHash for lookup...
            scriptHash = ScriptBuilder.computeScriptHash(address);
        }
        else {
            scriptHash = nullableScriptHash;
        }

        final java.util.List<Row> rows;
        final MutableHashMap<TransactionId, MutableList<Integer>> previousOutputs = new MutableHashMap<>();
        { // Load credits, with output_indexes...
            final java.util.List<Row> transactionOutputRows = databaseConnection.query(
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_outputs.transaction_id, indexed_transaction_outputs.output_index FROM indexed_transaction_outputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_outputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_outputs.script_hash = ?")
                    .setParameter(scriptHash)
            );
            if (transactionOutputRows.isEmpty()) {
                new AddressTransactions(blockchainSegmentId);
            }

            // Build the outputIndexes map...
            for (final Row row : transactionOutputRows) {
                final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
                final Integer outputIndex = row.getInteger("output_index");

                MutableList<Integer> indexes = previousOutputs.get(transactionId);
                if (indexes == null) {
                    indexes = new MutableArrayList<>(1);
                    previousOutputs.put(transactionId, indexes);
                }

                indexes.add(outputIndex);
            }

            rows = transactionOutputRows;
        }

        final MutableHashMap<TransactionId, MutableList<Integer>> spentOutputs = new MutableHashMap<>();
        if (! rows.isEmpty()) { // Load debits, with input_indexes...
            final java.util.List<Row> transactionInputRows = databaseConnection.query(
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_inputs.transaction_id, indexed_transaction_inputs.spends_transaction_id, indexed_transaction_inputs.spends_output_index FROM indexed_transaction_inputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_inputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE (indexed_transaction_inputs.spends_transaction_id, indexed_transaction_inputs.spends_output_index) IN (?)")
                    .setInClauseParameters(rows, new ValueExtractor<Row>() {
                        @Override
                        public InClauseParameter extractValues(final Row transactionOutputRows) {
                            final Long transactionId = transactionOutputRows.getLong("transaction_id");
                            final Integer outputIndex = transactionOutputRows.getInteger("output_index");
                            final TypedParameter transactionIdTypedParameter = (transactionId != null ? new TypedParameter(transactionId) : TypedParameter.NULL);
                            final TypedParameter outputIndexTypedParameter = (outputIndex != null ? new TypedParameter(outputIndex) : TypedParameter.NULL);
                            return new InClauseParameter(transactionIdTypedParameter, outputIndexTypedParameter);
                        }
                    })
            );

            // Build the inputIndexes map...
            for (final Row row : transactionInputRows) {
                // final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
                final TransactionId spentTransactionId = TransactionId.wrap(row.getLong("spends_transaction_id"));
                final Integer spentOutputIndex = row.getInteger("spends_output_index");

                MutableList<Integer> indexes = spentOutputs.get(spentTransactionId);
                if (indexes == null) {
                    indexes = new MutableArrayList<>(1);
                    spentOutputs.put(spentTransactionId, indexes);
                }

                indexes.add(spentOutputIndex);
            }

            rows.addAll(transactionInputRows);
        }

        // Get Transactions that are connected to the provided blockchainSegmentId...
        final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = _extractTransactionBlockchainSegmentIds(rows);
        final Set<TransactionId> connectedTransactionIds = _filterTransactionsConnectedToBlockchainSegment(transactionBlockchainSegmentIds, blockchainSegmentId, includeUnconfirmedTransactions);

        // Remove Transactions that are not connected to this blockchain...
        { // PreviousOutputs
            previousOutputs.mutableVisit(new MutableMap.MutableVisitor<>() {
                @Override
                public boolean run(final Tuple<TransactionId, MutableList<Integer>> mapEntry) {
                    final TransactionId transactionId = mapEntry.first;
                    if (! connectedTransactionIds.contains(transactionId)) {
                        mapEntry.first = null; // Remove entry.
                    }

                    return true;
                }
            });
        }
        { // SpentOutputs
            spentOutputs.mutableVisit(new MutableMap.MutableVisitor<>() {
                @Override
                public boolean run(final Tuple<TransactionId, MutableList<Integer>> mapEntry) {
                    final TransactionId transactionId = mapEntry.first;
                    if (! connectedTransactionIds.contains(transactionId)) {
                        mapEntry.first = null; // Remove entry.
                    }
                    return true;
                }
            });
        }

        return new AddressTransactions(blockchainSegmentId, new ImmutableList<>(connectedTransactionIds), previousOutputs, spentOutputs);
    }

    protected Long _getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final TypedAddress address, final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final List<Integer> emptyList = new MutableArrayList<>(0);

        final AddressTransactions addressTransactions = _getAddressTransactions(blockchainSegmentId, address, scriptHash, includeUnconfirmedTransactions);

        long balance = 0L;
        for (final TransactionId transactionId : addressTransactions.transactionIds) {
            final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            final boolean isPruningMode = (transactionHash != null && transaction == null);

            if (transaction != null) {
                // Collect the previousTransactions, and add the remembered outputs that credit into this account...
                final List<TransactionOutput> previousTransactionOutputs = transaction.getTransactionOutputs();
                for (final Integer outputIndex : Util.coalesce(addressTransactions.previousOutputs.get(transactionId), emptyList)) {
                    final TransactionOutput previousTransactionOutput = previousTransactionOutputs.get(outputIndex);
                    balance += previousTransactionOutput.getAmount();
                }
            }
            else { // Attempt to load available UTXOs for pruned+indexed mode...
                if (! isPruningMode) { continue; } // Should not happen.
                if (! Util.areEqual(blockchainSegmentId, headBlockchainSegmentId)) { continue; } // Alternate blockchains are not supported with pruning mode...

                final MutableHashMap<Integer, TransactionOutput> unspentTransactionOutputMap;
                { // Collect the UTXOs...
                    final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = _databaseManager.getUnspentTransactionOutputDatabaseManager();
                    final List<TransactionOutputIdentifier> transactionOutputIdentifiers = unspentTransactionOutputDatabaseManager.getFastSyncOutputIdentifiers(transactionHash);
                    final List<UnspentTransactionOutput> unspentTransactionOutputs = unspentTransactionOutputDatabaseManager.getUnspentTransactionOutputs(transactionOutputIdentifiers);
                    unspentTransactionOutputMap = new MutableHashMap<>();
                    for (final UnspentTransactionOutput unspentTransactionOutput : unspentTransactionOutputs) {
                        final Integer unspentOutputIndex = unspentTransactionOutput.getIndex();
                        unspentTransactionOutputMap.put(unspentOutputIndex, unspentTransactionOutput);
                    }
                }

                for (final Integer outputIndex : Util.coalesce(addressTransactions.previousOutputs.get(transactionId), emptyList)) {
                    final TransactionOutput transactionOutput = unspentTransactionOutputMap.get(outputIndex);
                    if (transactionOutput == null) { continue; } // Should not happen.

                    balance += transactionOutput.getAmount();
                }
            }
        }

        for (final TransactionId transactionId : addressTransactions.spentOutputs.getKeys()) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
            if (transaction == null) { continue; } // Should not happen.

            // Deduct all debits from the account for this transaction's outputs...
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            for (final Integer outputIndex : Util.coalesce(addressTransactions.spentOutputs.get(transactionId), emptyList)) {
                final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
                balance -= transactionOutput.getAmount();
            }
        }

        return balance;
    }

    protected Long _getLastIndexedTransactionId() {
        final PropertiesStore propertiesStore = _databaseManager.getPropertiesStore();
        return Util.coalesce(propertiesStore.getLong(LAST_INDEXED_TRANSACTION_KEY));
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockchainSegmentId blockchainSegmentId, final TypedAddress address, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final AddressTransactions addressTransactions = _getAddressTransactions(blockchainSegmentId, address, null, includeUnconfirmedTransactions);
        return addressTransactions.transactionIds;
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockchainSegmentId blockchainSegmentId, final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final AddressTransactions addressTransactions = _getAddressTransactions(blockchainSegmentId, null, scriptHash, includeUnconfirmedTransactions);
        return addressTransactions.transactionIds;
    }

    @Override
    public Long getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final TypedAddress address, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        return _getAddressBalance(blockchainSegmentId, address, null, includeUnconfirmedTransactions);
    }

    @Override
    public Long getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final Sha256Hash scriptHash, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        return _getAddressBalance(blockchainSegmentId, null, scriptHash, includeUnconfirmedTransactions);
    }

    @Override
    public Map<Integer, TransactionId> getTransactionsSpendingOutputsOf(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id, spends_output_index FROM indexed_transaction_inputs WHERE spends_transaction_id = ? ORDER BY spends_output_index ASC")
                .setParameter(transactionId)
        );

        final MutableHashMap<Integer, TransactionId> spentOutputsMap = new MutableHashMap<>();
        for (final Row row : rows) {
            final TransactionId spendingTransactionId = TransactionId.wrap(row.getLong("transaction_id"));
            final Integer spendsOutputIndex = row.getInteger("spends_output_index");

            spentOutputsMap.put(spendsOutputIndex, spendingTransactionId);
        }
        return spentOutputsMap;
    }

    @Override
    public SlpTokenId getSlpTokenId(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transactions.hash FROM indexed_transaction_outputs INNER JOIN transactions ON transactions.id = indexed_transaction_outputs.slp_transaction_id WHERE indexed_transaction_outputs.transaction_id = ?")
                .setParameter(transactionId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return SlpTokenId.wrap(Sha256Hash.copyOf(row.getBytes("hash")));
    }

    @Override
    public List<TransactionId> getSlpTransactionIds(final SlpTokenId slpTokenId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT indexed_transaction_outputs.transaction_id FROM indexed_transaction_outputs INNER JOIN transactions ON transactions.id = indexed_transaction_outputs.slp_transaction_id WHERE transactions.hash = ?")
                .setParameter(slpTokenId)
        );

        final MutableList<TransactionId> transactionIds = new MutableArrayList<>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    @Override
    public void queueTransactionsForProcessing(final List<TransactionId> transactionIds) throws DatabaseException {

    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws DatabaseException {
        final Long firstTransactionId = _getLastIndexedTransactionId();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id AS id FROM block_transactions WHERE transaction_id > ? ORDER BY transaction_id ASC LIMIT " + batchSize)
                .setParameter(firstTransactionId)
        );

        if (rows.isEmpty()) {
            rows.addAll(databaseConnection.query(
                new Query("SELECT transaction_id AS id FROM unconfirmed_transactions WHERE transaction_id > ? ORDER BY transaction_id ASC LIMIT " + batchSize)
                    .setParameter(firstTransactionId)
            ));
        }

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<>(rows.size());
        for (final Row row : rows) {
            final Long rowId = row.getLong("id");
            final TransactionId transactionId = TransactionId.wrap(rowId);
            listBuilder.add(transactionId);
        }

        return listBuilder.build();
    }

    @Override
    public void markTransactionProcessed(final TransactionId transactionId) {
        final PropertiesStore propertiesStore = _databaseManager.getPropertiesStore();
        propertiesStore.getAndSetLong(LAST_INDEXED_TRANSACTION_KEY, new PropertiesStore.GetAndSetter<Long>() {
            @Override
            public Long run(final Long value) {
                return Math.max(Util.coalesce(value), (transactionId == null ? 0L : transactionId.longValue()));
            }
        });
    }

    @Override
    public void indexTransactionOutputs(final List<TransactionId> transactionIds, final List<Integer> outputIndexes, final List<Long> amounts, final List<ScriptType> scriptTypes, final List<Address> addresses, final List<Sha256Hash> scriptHashes, final List<TransactionId> slpTransactionIds, final List<ByteArray> memoActionTypes, final List<ByteArray> memoActionIdentifiers) throws DatabaseException {
        final int itemCount = transactionIds.getCount();
        if (transactionIds.getCount()           != itemCount) { throw new DatabaseException("Mismatch parameter count transactionIds expected "         + itemCount + " got " + transactionIds.getCount()); }
        if (outputIndexes.getCount()            != itemCount) { throw new DatabaseException("Mismatch parameter count outputIndexes expected "          + itemCount + " got " + outputIndexes.getCount()); }
        if (amounts.getCount()                  != itemCount) { throw new DatabaseException("Mismatch parameter count amounts expected "                + itemCount + " got " + amounts.getCount()); }
        if (scriptHashes.getCount()             != itemCount) { throw new DatabaseException("Mismatch parameter count scriptHashes expected "           + itemCount + " got " + scriptHashes.getCount()); }
        if (scriptTypes.getCount()              != itemCount) { throw new DatabaseException("Mismatch parameter count scriptTypes expected "            + itemCount + " got " + scriptTypes.getCount()); }
        if (addresses.getCount()                != itemCount) { throw new DatabaseException("Mismatch parameter count addresses expected "              + itemCount + " got " + addresses.getCount()); }
        if (slpTransactionIds.getCount()        != itemCount) { throw new DatabaseException("Mismatch parameter count slpTransactionIds expected "      + itemCount + " got " + slpTransactionIds.getCount()); }
        if (memoActionTypes.getCount()          != itemCount) { throw new DatabaseException("Mismatch parameter count memoActionTypes expected "        + itemCount + " got " + memoActionTypes.getCount()); }
        if (memoActionIdentifiers.getCount()    != itemCount) { throw new DatabaseException("Mismatch parameter count memoActionIdentifiers expected "  + itemCount + " got " + memoActionIdentifiers.getCount()); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        // The BatchRunner expects to receive a single list; in order to use it here, a list of indexes is created as the batch.
        final MutableList<Integer> indexes = new MutableArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            indexes.add(i);
        }

        final Integer batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<Integer> batchRunner = new BatchRunner<>(batchSize, false);
        batchRunner.run(indexes, new BatchRunner.Batch<Integer>() {
            @Override
            public void run(final List<Integer> batchItems) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO indexed_transaction_outputs (transaction_id, output_index, amount, address, script_hash, script_type_id, slp_transaction_id, memo_action_type, memo_action_identifier) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), address = VALUES(address), script_hash = VALUES(script_hash), script_type_id = VALUES(script_type_id), slp_transaction_id = VALUES(slp_transaction_id), memo_action_type = VALUES(memo_action_type), memo_action_identifier = VALUES(memo_action_identifier)");
                for (final Integer itemIndex : batchItems) {
                    final TransactionId transactionId = transactionIds.get(itemIndex);
                    final Integer outputIndex = outputIndexes.get(itemIndex);
                    final Long amount = amounts.get(itemIndex);
                    final Address address = (STORE_ADDRESS_IS_ENABLED ? addresses.get(itemIndex) : null);
                    final Sha256Hash scriptHash = scriptHashes.get(itemIndex);
                    final ScriptType scriptType = scriptTypes.get(itemIndex);
                    final TransactionId slpTransactionId = slpTransactionIds.get(itemIndex);
                    final ByteArray memoActionType = memoActionTypes.get(itemIndex);
                    final ByteArray memoActionIdentifier = memoActionIdentifiers.get(itemIndex);

                    final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();

                    batchedInsertQuery
                        .setParameter(transactionId)
                        .setParameter(outputIndex)
                        .setParameter(amount)
                        .setParameter(address)
                        .setParameter(scriptHash)
                        .setParameter(scriptTypeId)
                        .setParameter(slpTransactionId)
                        .setParameter(memoActionType)
                        .setParameter(memoActionIdentifier);
                }

                databaseConnection.executeSql(batchedInsertQuery);
            }
        });
    }

    @Override
    public void indexTransactionInputs(final List<TransactionId> transactionIds, final List<Integer> inputIndexes, final List<TransactionOutputId> transactionOutputIds) throws DatabaseException {
        final int itemCount = transactionIds.getCount();
        if (transactionIds.getCount()               != itemCount) { throw new DatabaseException("Mismatch parameter count transactionIds expected "         + itemCount + " got " + transactionIds.getCount()); }
        if (inputIndexes.getCount()                 != itemCount) { throw new DatabaseException("Mismatch parameter count inputIndexes expected "           + itemCount + " got " + inputIndexes.getCount()); }
        if (transactionOutputIds.getCount()         != itemCount) { throw new DatabaseException("Mismatch parameter count transactionOutputIds expected "   + itemCount + " got " + transactionOutputIds.getCount()); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        // The BatchRunner expects to receive a single list; in order to use it here, a list of indexes is created as the batch.
        final MutableList<Integer> indexes = new MutableArrayList<>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            indexes.add(i);
        }

        final Integer batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<Integer> batchRunner = new BatchRunner<>(batchSize);
        batchRunner.run(indexes, new BatchRunner.Batch<Integer>() {
            @Override
            public void run(final List<Integer> batchItems) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO indexed_transaction_inputs (transaction_id, input_index, spends_transaction_id, spends_output_index) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE spends_transaction_id = VALUES(spends_transaction_id), spends_output_index = VALUES(spends_output_index)");
                for (final Integer itemIndex : batchItems) {
                    final TransactionId transactionId = transactionIds.get(itemIndex);
                    final Integer inputIndex = inputIndexes.get(itemIndex);
                    final TransactionOutputId transactionOutputId = transactionOutputIds.get(itemIndex);

                    batchedInsertQuery
                        .setParameter(transactionId)
                        .setParameter(inputIndex)
                        .setParameter(transactionOutputId.getTransactionId())
                        .setParameter(transactionOutputId.getOutputIndex());
                }

                databaseConnection.executeSql(batchedInsertQuery);
            }
        });
    }

    @Override
    public void deleteTransactionIndexes() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        _databaseManager.startTransaction();

        databaseConnection.executeSql(
            new Query("DELETE FROM indexed_transaction_outputs")
        );
        databaseConnection.executeSql(
            new Query("DELETE FROM indexed_transaction_inputs")
        );
        databaseConnection.executeSql(
            new Query("DELETE FROM properties WHERE `key` = ?")
                .setParameter(LAST_INDEXED_TRANSACTION_KEY)
        );

        _databaseManager.commitTransaction();
    }

    @Override
    public TransactionId getMostRecentTransactionId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions ORDER BY id DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionId.wrap(row.getLong("id"));
    }

    @Override
    public TransactionId getLastIndexedTransactionId() throws DatabaseException {
        return TransactionId.wrap(_getLastIndexedTransactionId());
    }
}

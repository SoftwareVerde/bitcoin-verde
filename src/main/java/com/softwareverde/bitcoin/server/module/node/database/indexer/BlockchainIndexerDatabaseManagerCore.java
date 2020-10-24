package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
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
import com.softwareverde.bitcoin.slp.SlpTokenId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.ScriptTypeId;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.query.parameter.InClauseParameter;
import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockchainIndexerDatabaseManagerCore implements BlockchainIndexerDatabaseManager {
    protected static class AddressTransactions {
        public final BlockchainSegmentId blockchainSegmentId;
        public final List<TransactionId> transactionIds;
        public final Map<TransactionId, MutableList<Integer>> outputIndexes;
        public final Map<TransactionId, MutableList<Integer>> inputIndexes;

        public AddressTransactions(final BlockchainSegmentId blockchainSegmentId) {
            this.blockchainSegmentId = blockchainSegmentId;
            this.transactionIds = new ImmutableList<TransactionId>();
            this.outputIndexes = new HashMap<TransactionId, MutableList<Integer>>(0);
            this.inputIndexes = new HashMap<TransactionId, MutableList<Integer>>(0);
        }

        public AddressTransactions(final BlockchainSegmentId blockchainSegmentId, final List<TransactionId> transactionIds, final HashMap<TransactionId, MutableList<Integer>> outputIndexes, final HashMap<TransactionId, MutableList<Integer>> inputIndexes) {
            this.blockchainSegmentId = blockchainSegmentId;
            this.transactionIds = transactionIds;
            this.outputIndexes = outputIndexes;
            this.inputIndexes = inputIndexes;
        }
    }

    protected static final String PENDING_TRANSACTION_KEY = "next_pending_transaction_id";

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
    protected final Set<Tuple<TransactionId, BlockchainSegmentId>> _extractTransactionBlockchainSegmentIds(final java.util.List<Row> rows) {
        final HashSet<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = new HashSet<Tuple<TransactionId, BlockchainSegmentId>>();
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            final BlockchainSegmentId transactionBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("blockchain_segment_id"));

            final Tuple<TransactionId, BlockchainSegmentId> tuple = new Tuple<TransactionId, BlockchainSegmentId>(transactionId, transactionBlockchainSegmentId);
            transactionBlockchainSegmentIds.add(tuple);
        }
        return transactionBlockchainSegmentIds;
    }

    protected List<TransactionId> _filterTransactionsConnectedToBlockchainSegment(final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds, final BlockchainSegmentId blockchainSegmentId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final HashMap<BlockchainSegmentId, Boolean> connectedBlockchainSegmentIds = new HashMap<BlockchainSegmentId, Boolean>(); // Used to cache the lookup result of connected BlockchainSegments.
        final HashSet<TransactionId> transactionIds = new HashSet<TransactionId>();

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
                    if (transactionIsUnconfirmed) {
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

        return new ImmutableList<TransactionId>(transactionIds);
    }

    protected AddressTransactions _getAddressTransactions(final BlockchainSegmentId blockchainSegmentId, final Address address) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows;
        final HashMap<TransactionId, MutableList<Integer>> outputIndexes = new HashMap<TransactionId, MutableList<Integer>>();
        { // Load debits, with output_indexes...
            final java.util.List<Row> transactionOutputRows = databaseConnection.query(
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_outputs.transaction_id, indexed_transaction_outputs.output_index FROM indexed_transaction_outputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_outputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_outputs.address = ?")
                    .setParameter(address)
            );
            if (transactionOutputRows.isEmpty()) {
                new AddressTransactions(blockchainSegmentId);
            }

            // Build the outputIndexes map...
            for (final Row row : transactionOutputRows) {
                final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
                final Integer outputIndex = row.getInteger("output_index");

                MutableList<Integer> indexes = outputIndexes.get(transactionId);
                if (indexes == null) {
                    indexes = new MutableList<Integer>(1);
                    outputIndexes.put(transactionId, indexes);
                }

                indexes.add(outputIndex);
            }

            rows = transactionOutputRows;
        }

        final HashMap<TransactionId, MutableList<Integer>> inputIndexes = new HashMap<TransactionId, MutableList<Integer>>();
        { // Load credits, with input_indexes...
            final java.util.List<Row> transactionInputRows = databaseConnection.query(
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_inputs.transaction_id, indexed_transaction_inputs.input_index FROM indexed_transaction_inputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_inputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE (indexed_transaction_inputs.spends_transaction_id, indexed_transaction_inputs.spends_output_index) IN (?)")
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
                final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
                final Integer inputIndex = row.getInteger("input_index");

                MutableList<Integer> indexes = inputIndexes.get(transactionId);
                if (indexes == null) {
                    indexes = new MutableList<Integer>(1);
                    inputIndexes.put(transactionId, indexes);
                }

                indexes.add(inputIndex);
            }

            rows.addAll(transactionInputRows);
        }

        // Get Transactions that are connected to the provided blockchainSegmentId...
        final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = _extractTransactionBlockchainSegmentIds(rows);
        final List<TransactionId> transactionIds = _filterTransactionsConnectedToBlockchainSegment(transactionBlockchainSegmentIds, blockchainSegmentId, true);

        return new AddressTransactions(blockchainSegmentId, transactionIds, inputIndexes, outputIndexes);
    }

    protected Long _getNextUnprocessedTransactionId() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT value FROM properties WHERE key = ?")
                .setParameter(PENDING_TRANSACTION_KEY)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return row.getLong("value");
    }

    protected void _updateNextUnprocessedTransactionId(final List<TransactionId> transactionIds) throws DatabaseException {
        if (transactionIds.isEmpty()) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        Long nextTransactionId = _getNextUnprocessedTransactionId();

        for (final TransactionId transactionId : transactionIds) {
            final long transactionIdLong = transactionId.longValue();
            if (transactionIdLong >= nextTransactionId) {
                nextTransactionId = transactionIdLong;
            }
        }

        databaseConnection.executeSql(
            new Query("INSERT INTO properties (`key`, value) VALUES (?, ?) ON DUPLICATE KEY UPDATE value = VALUES (value)")
                .setParameter(PENDING_TRANSACTION_KEY)
                .setParameter(nextTransactionId)
        );
    }

    @Override
    public List<TransactionId> getTransactionIds(final BlockchainSegmentId blockchainSegmentId, final Address address, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final AddressTransactions addressTransactions = _getAddressTransactions(blockchainSegmentId, address);
        return addressTransactions.transactionIds;
    }

    @Override
    public Long getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final Address address) throws DatabaseException {
        final AddressTransactions addressTransactions = _getAddressTransactions(blockchainSegmentId, address);

        final List<Integer> emptyList = new MutableList<Integer>(0);

        final HashSet<Sha256Hash> previousTransactionHashSet = new HashSet<Sha256Hash>(); // Unique set of the required previousTransactions used to calculate the account's debits...
        final HashMap<Sha256Hash, MutableList<Integer>> previousTransactionOutputIndexMap = new HashMap<Sha256Hash, MutableList<Integer>>(); // Map used to remember which output indexes are used for the previousTransactions...

        long balance = 0L;
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        for (final TransactionId transactionId : addressTransactions.transactionIds) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);

            // Collect the previous Transaction hashes and which previous TransactionOutput indexes that apply debits to this account...
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            for (final Integer inputIndex : Util.coalesce(addressTransactions.inputIndexes.get(transactionId), emptyList)) {
                final TransactionInput transactionInput = transactionInputs.get(inputIndex);

                final Sha256Hash previousTransactionHash = transactionInput.getPreviousOutputTransactionHash();
                final Integer previousTransactionOutputIndex = transactionInput.getPreviousOutputIndex();

                previousTransactionHashSet.add(previousTransactionHash);

                MutableList<Integer> previousTransactionOutputIndexes = previousTransactionOutputIndexMap.get(previousTransactionHash);
                if (previousTransactionOutputIndexes == null) {
                    previousTransactionOutputIndexes = new MutableList<Integer>(1);
                    previousTransactionOutputIndexMap.put(previousTransactionHash, previousTransactionOutputIndexes);
                }
                previousTransactionOutputIndexes.add(previousTransactionOutputIndex);
            }

            // Deduct all credits from the account for this transaction's outputs...
            final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
            for (final Integer outputIndex : Util.coalesce(addressTransactions.outputIndexes.get(transactionId), emptyList)) {
                final TransactionOutput transactionOutput = transactionOutputs.get(outputIndex);
                balance -= transactionOutput.getAmount();
            }
        }

        // Collect the previousTransactions, and add the remembered outputs that debit into this account...
        final Map<Sha256Hash, TransactionId> previousTransactionIds = transactionDatabaseManager.getTransactionIds(new ImmutableList<Sha256Hash>(previousTransactionHashSet));
        for (final Sha256Hash previousTransactionHash : previousTransactionIds.keySet()) {
            final TransactionId transactionId = previousTransactionIds.get(previousTransactionHash);

            final Transaction previousTransaction = transactionDatabaseManager.getTransaction(transactionId);

            final List<TransactionOutput> previousTransactionOutputs = previousTransaction.getTransactionOutputs();
            for (final Integer previousOutputIndex : previousTransactionOutputIndexMap.get(previousTransactionHash)) {
                final TransactionOutput previousTransactionOutput = previousTransactionOutputs.get(previousOutputIndex);
                balance += previousTransactionOutput.getAmount();
            }
        }

        return balance;
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

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    @Override
    public void queueTransactionsForProcessing(final List<TransactionId> transactionIds) throws DatabaseException {
        _updateNextUnprocessedTransactionId(transactionIds);
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws DatabaseException {
        final Long firstTransactionId = _getNextUnprocessedTransactionId();

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM transactions WHERE id >= ? ORDER BY id ASC LIMIT " + batchSize)
                .setParameter(firstTransactionId)
        );

        if (rows.isEmpty()) { return new MutableList<TransactionId>(0); }

        final ImmutableListBuilder<TransactionId> listBuilder = new ImmutableListBuilder<TransactionId>(rows.size());
        for (final Row row : rows) {
            final Long rowId = row.getLong("id");
            final TransactionId transactionId = TransactionId.wrap(rowId);
            listBuilder.add(transactionId);
        }

        return listBuilder.build();
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) throws DatabaseException {
        _updateNextUnprocessedTransactionId(transactionIds);
    }

    @Override
    public void indexTransactionOutputs(final List<TransactionId> transactionIds, final List<Integer> outputIndexes, final List<Long> amounts, final List<ScriptType> scriptTypes, final List<Address> addresses, final List<TransactionId> slpTransactionIds) throws DatabaseException {
        final int itemCount = transactionIds.getCount();
        if (transactionIds.getCount()       != itemCount) { throw new DatabaseException("Mismatch parameter count transactionIds expected "     + itemCount + " got " + transactionIds.getCount()); }
        if (outputIndexes.getCount()        != itemCount) { throw new DatabaseException("Mismatch parameter count outputIndexes expected "      + itemCount + " got " + outputIndexes.getCount()); }
        if (amounts.getCount()              != itemCount) { throw new DatabaseException("Mismatch parameter count amounts expected "            + itemCount + " got " + amounts.getCount()); }
        if (scriptTypes.getCount()          != itemCount) { throw new DatabaseException("Mismatch parameter count scriptTypes expected "        + itemCount + " got " + scriptTypes.getCount()); }
        if (addresses.getCount()            != itemCount) { throw new DatabaseException("Mismatch parameter count addresses expected "          + itemCount + " got " + addresses.getCount()); }
        if (slpTransactionIds.getCount()    != itemCount) { throw new DatabaseException("Mismatch parameter count slpTransactionIds expected "  + itemCount + " got " + slpTransactionIds.getCount()); }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        // The BatchRunner expects to receive a single list; in order to use it here, a list of indexes is created as the batch.
        final MutableList<Integer> indexes = new MutableList<Integer>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            indexes.add(i);
        }

        final Integer batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<Integer> batchRunner = new BatchRunner<Integer>(batchSize, false);
        batchRunner.run(indexes, new BatchRunner.Batch<Integer>() {
            @Override
            public void run(final List<Integer> batchItems) throws Exception {
                final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT INTO indexed_transaction_outputs (transaction_id, output_index, amount, address, script_type_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), address = VALUES(address), script_type_id = VALUES(script_type_id), slp_transaction_id = VALUES(slp_transaction_id)");
                for (final Integer itemIndex : batchItems) {
                    final TransactionId transactionId = transactionIds.get(itemIndex);
                    final Integer outputIndex = outputIndexes.get(itemIndex);
                    final Long amount = amounts.get(itemIndex);
                    final Address address = addresses.get(itemIndex);
                    final ScriptType scriptType = scriptTypes.get(itemIndex);
                    final TransactionId slpTransactionId = slpTransactionIds.get(itemIndex);

                    final ScriptTypeId scriptTypeId = scriptType.getScriptTypeId();

                    batchedInsertQuery
                        .setParameter(transactionId)
                        .setParameter(outputIndex)
                        .setParameter(amount)
                        .setParameter(address)
                        .setParameter(scriptTypeId)
                        .setParameter(slpTransactionId);
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
        final MutableList<Integer> indexes = new MutableList<Integer>(itemCount);
        for (int i = 0; i < itemCount; ++i) {
            indexes.add(i);
        }

        final Integer batchSize = Math.min(1024, _databaseManager.getMaxQueryBatchSize());
        final BatchRunner<Integer> batchRunner = new BatchRunner<Integer>(batchSize);
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
}

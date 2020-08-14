package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
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
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TransactionOutputDatabaseManagerCore implements TransactionOutputDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    protected AddressId _getAddressId(final Address address) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(address)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long addressId = row.getLong("id");
        return AddressId.wrap(addressId);
    }

    public TransactionOutputDatabaseManagerCore(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public AddressId storeAddress(final Address address) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final Long addressId = databaseConnection.executeSql(
            new Query("INSERT IGNORE INTO addresses (address) VALUES (?)")
                .setParameter(address)
        );
        if (databaseConnection.getRowsAffectedCount() > 0) {
            return AddressId.wrap(addressId);
        }

        return _getAddressId(address);
    }

    @Override
    public List<AddressId> getAddressIds(final TransactionId transactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT address_id FROM indexed_transaction_outputs WHERE transaction_id = ?")
        );
        rows.addAll(databaseConnection.query(
            new Query("SELECT address_id FROM indexed_transaction_inputs WHERE transaction_id = ?")
        ));

        final HashSet<AddressId> addressIds = new HashSet<AddressId>(rows.size());
        for (final Row row : rows) {
            final Long addressId = row.getLong("address_id");
            addressIds.add(AddressId.wrap(addressId));
        }
        return new ImmutableList<AddressId>(addressIds);
    }

    @Override
    public AddressId getAddressId(final Address address) throws DatabaseException {
        if (address == null) { return null; }
        return _getAddressId(address);
    }

    /**
     * Returns a list of rows of {blockchain_segment_id, transaction_id} from indexed_transaction_inputs for the provided addressId.
     */
    protected java.util.List<Row> _getTransactionIdsSpendingFrom(final AddressId addressId, final DatabaseConnection databaseConnection) throws DatabaseException {
        return databaseConnection.query(
            new Query("SELECT blocks.blockchain_segment_id, block_transactions.transaction_id FROM indexed_transaction_inputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_inputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_inputs.address_id = ? GROUP BY indexed_transaction_inputs.transaction_id, blocks.blockchain_segment_id")
                .setParameter(addressId)
        );
    }

    /**
     * Returns a list of rows of {blockchain_segment_id, transaction_id} from indexed_transaction_outputs for the provided addressId.
     */
    protected java.util.List<Row> _getTransactionIdsSendingTo(final AddressId addressId, final DatabaseConnection databaseConnection) throws DatabaseException {
        return databaseConnection.query(
            new Query("SELECT blocks.blockchain_segment_id, block_transactions.transaction_id FROM indexed_transaction_outputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_outputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_outputs.address_id = ? GROUP BY indexed_transaction_outputs.transaction_id, blocks.blockchain_segment_id")
                .setParameter(addressId)
        );
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
                    final Boolean cachedConnectedSegmentResult = connectedBlockchainSegmentIds.get(transactionBlockchainSegmentId);
                    if (cachedConnectedSegmentResult != null) {
                        transactionIsConnectedToBlockchainSegment = cachedConnectedSegmentResult;
                    }
                    else {
                        final Boolean notCachedTransactionIsConnectedToBlockchainSegment = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, transactionBlockchainSegmentId, BlockRelationship.ANCESTOR);
                        connectedBlockchainSegmentIds.put(transactionBlockchainSegmentId, notCachedTransactionIsConnectedToBlockchainSegment);
                        transactionIsConnectedToBlockchainSegment = notCachedTransactionIsConnectedToBlockchainSegment;
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

    @Override
    public List<TransactionId> getTransactionIds(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = _getTransactionIdsSpendingFrom(addressId, databaseConnection);
        rows.addAll(_getTransactionIdsSendingTo(addressId, databaseConnection));

        if (rows.isEmpty()) { return new MutableList<TransactionId>(0); }

        final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = _extractTransactionBlockchainSegmentIds(rows);
        return _filterTransactionsConnectedToBlockchainSegment(transactionBlockchainSegmentIds, blockchainSegmentId, includeUnconfirmedTransactions);
    }

    @Override
    public List<TransactionId> getTransactionIdsSendingTo(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = _getTransactionIdsSendingTo(addressId, databaseConnection);

        if (rows.isEmpty()) { return new MutableList<TransactionId>(0); }

        final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = _extractTransactionBlockchainSegmentIds(rows);
        return _filterTransactionsConnectedToBlockchainSegment(transactionBlockchainSegmentIds, blockchainSegmentId, includeUnconfirmedTransactions);
    }

    @Override
    public List<TransactionId> getTransactionIdsSpendingFrom(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = _getTransactionIdsSpendingFrom(addressId, databaseConnection);

        if (rows.isEmpty()) { return new MutableList<TransactionId>(0); }

        final Set<Tuple<TransactionId, BlockchainSegmentId>> transactionBlockchainSegmentIds = _extractTransactionBlockchainSegmentIds(rows);
        return _filterTransactionsConnectedToBlockchainSegment(transactionBlockchainSegmentIds, blockchainSegmentId, includeUnconfirmedTransactions);
    }

    @Override
    public Long getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows;
        final HashMap<TransactionId, MutableList<Integer>> outputIndexes = new HashMap<TransactionId, MutableList<Integer>>();
        { // Load debits, with output_indexes...
            final java.util.List<Row> transactionOutputRows = databaseConnection.query(
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_outputs.transaction_id, indexed_transaction_outputs.output_index FROM indexed_transaction_outputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_outputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_outputs.address_id = ?")
                    .setParameter(addressId)
            );
            if (transactionOutputRows.isEmpty()) { return 0L; }

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
                new Query("SELECT blocks.blockchain_segment_id, indexed_transaction_inputs.transaction_id, indexed_transaction_inputs.input_index FROM indexed_transaction_inputs LEFT OUTER JOIN block_transactions ON block_transactions.transaction_id = indexed_transaction_inputs.transaction_id LEFT OUTER JOIN blocks ON blocks.id = block_transactions.block_id WHERE indexed_transaction_inputs.address_id = ?")
                    .setParameter(addressId)
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

        final List<Integer> emptyList = new MutableList<Integer>(0);

        final HashSet<Sha256Hash> previousTransactionHashSet = new HashSet<Sha256Hash>(); // Unique set of the required previousTransactions used to calculate the account's debits...
        final HashMap<Sha256Hash, MutableList<Integer>> previousTransactionOutputIndexMap = new HashMap<Sha256Hash, MutableList<Integer>>(); // Map used to remember which output indexes are used for the previousTransactions...

        long balance = 0L;
        final TransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        for (final TransactionId transactionId : transactionIds) {
            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);

            // Collect the previous Transaction hashes and which previous TransactionOutput indexes that apply debits to this account...
            final List<TransactionInput> transactionInputs = transaction.getTransactionInputs();
            for (final Integer inputIndex : Util.coalesce(inputIndexes.get(transactionId), emptyList)) {
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
            for (final Integer outputIndex : Util.coalesce(outputIndexes.get(transactionId), emptyList)) {
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
        if (transactionIds.isEmpty()) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final BatchedInsertQuery query = new BatchedInsertQuery("INSERT IGNORE transaction_output_processor_queue (transaction_id) VALUES (?)");
        for (final TransactionId transactionId : transactionIds) {
            query.setParameter(transactionId);
        }

        databaseConnection.executeSql(query);
    }

    @Override
    public List<TransactionId> getUnprocessedTransactions(final Integer batchSize) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transaction_id FROM transaction_output_processor_queue LIMIT " + batchSize)
        );

        final MutableList<TransactionId> transactionIds = new MutableList<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            transactionIds.add(transactionId);
        }
        return transactionIds;
    }

    @Override
    public void dequeueTransactionsForProcessing(final List<TransactionId> transactionIds) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("DELETE FROM transaction_output_processor_queue WHERE transaction_id IN (?)")
                .setInClauseParameters(transactionIds, ValueExtractor.IDENTIFIER)
        );
    }

    @Override
    public void indexTransactionOutput(final TransactionId transactionId, final Integer outputIndex, final Long amount, final ScriptType scriptType, final AddressId addressId, final TransactionId slpTransactionId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        databaseConnection.executeSql(
            new Query("INSERT INTO indexed_transaction_outputs (transaction_id, output_index, amount, address_id, script_type_id, slp_transaction_id) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount), address_id = VALUES(address_id), script_type_id = VALUES(script_type_id), slp_transaction_id = VALUES(slp_transaction_id)")
                .setParameter(transactionId)
                .setParameter(outputIndex)
                .setParameter(amount)
                .setParameter(addressId)
                .setParameter(scriptType.getScriptTypeId())
                .setParameter(slpTransactionId)
        );
    }
}

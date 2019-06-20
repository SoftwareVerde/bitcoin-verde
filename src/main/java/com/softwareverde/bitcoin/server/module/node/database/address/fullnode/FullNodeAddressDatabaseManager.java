package com.softwareverde.bitcoin.server.module.node.database.address.fullnode;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.address.AddressDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.address.MutableSpendableTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.database.address.SpendableTransactionOutput;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.HashSet;

public class FullNodeAddressDatabaseManager implements AddressDatabaseManager {
    protected final FullNodeDatabaseManager _databaseManager;

    protected AddressId _getAddressId(final String addressString) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();

        final AddressId cachedAddressId = databaseManagerCache.getCachedAddressId(addressString);
        if (cachedAddressId != null) {
            return cachedAddressId;
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final AddressId addressId = AddressId.wrap(row.getLong("id"));

        databaseManagerCache.cacheAddressId(addressString, addressId);

        return addressId;
    }

    protected List<TransactionId> _getTransactionIdsSendingTo(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            // Include Transactions that send to the Address...
            new Query("SELECT transaction_outputs.transaction_id FROM transaction_outputs INNER JOIN locking_scripts ON transaction_outputs.id = locking_scripts.transaction_output_id WHERE locking_scripts.address_id = ? GROUP BY transaction_outputs.transaction_id")
                .setParameter(addressId)
        );

        final HashSet<TransactionId> transactionIdSet = new HashSet<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));

            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
            if (blockId == null) {
                if (! includeUnconfirmedTransactions) { continue; }
                else {
                    final Boolean isUnconfirmedTransaction = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
                    if (! isUnconfirmedTransaction) { continue; }
                }
            }

            transactionIdSet.add(transactionId);
        }

        return new ImmutableList<TransactionId>(transactionIdSet);
    }

    protected List<TransactionId> _getTransactionIdsSpendingFrom(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            // Include Transactions that spend from the Address...
            new Query("SELECT transaction_inputs.transaction_id FROM transaction_outputs INNER JOIN locking_scripts ON transaction_outputs.id = locking_scripts.transaction_output_id INNER JOIN transaction_inputs ON transaction_inputs.previous_transaction_output_id = transaction_outputs.id WHERE locking_scripts.address_id = ? GROUP BY transaction_inputs.transaction_id")
                .setParameter(addressId)
        );

        final HashSet<TransactionId> transactionIdSet = new HashSet<TransactionId>(rows.size());
        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));

            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
            if (blockId == null) {
                if (! includeUnconfirmedTransactions) { continue; }
                else {
                    final Boolean isUnconfirmedTransaction = transactionDatabaseManager.isUnconfirmedTransaction(transactionId);
                    if (! isUnconfirmedTransaction) { continue; }
                }
            }

            transactionIdSet.add(transactionId);
        }

        return new ImmutableList<TransactionId>(transactionIdSet);
    }

    protected List<SpendableTransactionOutput> _getAddressOutputs(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = _databaseManager.getTransactionDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT transactions.id AS transaction_id, transaction_outputs.id AS transaction_output_id, transaction_outputs.amount FROM transactions INNER JOIN transaction_outputs ON transactions.id = transaction_outputs.transaction_id INNER JOIN locking_scripts ON transaction_outputs.id = locking_scripts.transaction_output_id WHERE locking_scripts.address_id = ?")
                .setParameter(addressId)
        );
        if (rows.isEmpty()) { return new MutableList<SpendableTransactionOutput>(); }

        final MutableList<SpendableTransactionOutput> spendableTransactionOutputs = new MutableList<SpendableTransactionOutput>(rows.size());

        for (final Row row : rows) {
            final TransactionId transactionId = TransactionId.wrap(row.getLong("transaction_id"));
            final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
            final Long amount = row.getLong("amount");

            final BlockId blockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);

            final MutableSpendableTransactionOutput spendableTransactionOutput = new MutableSpendableTransactionOutput();
            {
                spendableTransactionOutput.setBlockId(blockId);
                spendableTransactionOutput.setTransactionId(transactionId);
                spendableTransactionOutput.setTransactionOutputId(transactionOutputId);
                spendableTransactionOutput.setAmount(amount);
                spendableTransactionOutput.setIsUnconfirmed(transactionDatabaseManager.isUnconfirmedTransaction(transactionId));
            }

            if (spendableTransactionOutput.isMined()) {
                final Boolean transactionWasMinedOnChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
                if (! transactionWasMinedOnChain) { continue; }
            }
            else if (! spendableTransactionOutput.isUnconfirmed()) {
                continue; // Do not include transactions that are neither mined nor in the mempool...
            }

            {
                final java.util.List<Row> rowsSpendingTransactionOutput = databaseConnection.query(
                    new Query("SELECT transactions.id AS transaction_id, transaction_inputs.id AS transaction_input_id FROM transaction_inputs INNER JOIN transactions ON transactions.id = transaction_inputs.transaction_id WHERE previous_transaction_output_id = ?")
                        .setParameter(spendableTransactionOutput.getTransactionOutputId())
                );

                for (final Row rowSpendingTransactionOutput : rowsSpendingTransactionOutput) {
                    final BlockId spendingBlockId = transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId);
                    // final TransactionId spendingTransactionId = TransactionId.wrap(rowSpendingTransactionOutput.getLong("transaction_id"));
                    final TransactionInputId spendingTransactionInputId = TransactionInputId.wrap(rowSpendingTransactionOutput.getLong("transaction_input_id"));

                    final Boolean spendingTransactionIsMined = (spendingBlockId != null);

                    final Boolean transactionOutputHasBeenSpent;
                    if (! spendingTransactionIsMined) {
                        transactionOutputHasBeenSpent = true;
                    }
                    else {
                        final Boolean spendingTransactionIsMinedOnMainChain = (blockHeaderDatabaseManager.isBlockConnectedToChain(spendingBlockId, blockchainSegmentId, BlockRelationship.ANY));
                        transactionOutputHasBeenSpent = spendingTransactionIsMinedOnMainChain;
                    }

                    if (transactionOutputHasBeenSpent) {
                        spendableTransactionOutput.setSpentByTransactionInputId(spendingTransactionInputId);
                        break;
                    }
                }
            }

            spendableTransactionOutputs.add(spendableTransactionOutput);
        }

        return spendableTransactionOutputs;
    }

    public FullNodeAddressDatabaseManager(final FullNodeDatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    public AddressId storeScriptAddress(final LockingScript lockingScript) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final DatabaseManagerCache databaseManagerCache = _databaseManager.getDatabaseManagerCache();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        if (scriptType == ScriptType.CUSTOM_SCRIPT) {
            return null;
        }

        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
        if (address == null) {
            Logger.log("Error determining address.");
            return null;
        }

        final String addressString = address.toBase58CheckEncoded();

        final AddressId cachedAddressId = databaseManagerCache.getCachedAddressId(addressString);
        if (cachedAddressId != null) { return cachedAddressId; }

        {
            final AddressId addressId = AddressId.wrap(databaseConnection.executeSql(
                new Query("INSERT IGNORE INTO addresses (address) VALUES (?)")
                    .setParameter(addressString)
            ));

            if ( (addressId != null) && (addressId.longValue() > 0) ) {
                databaseManagerCache.cacheAddressId(addressString, addressId);
                return addressId;
            }
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        final Row row = rows.get(0);
        final AddressId addressId = AddressId.wrap(row.getLong("id"));

        databaseManagerCache.cacheAddressId(addressString, addressId);

        return addressId;
    }

    /**
     * Returns a list of AddressIds for the provided lockingScripts.
     *  The returned list is guaranteed to be a 1-to-1 mapping, where AddressId is null if the LockingScript
     *  does not have an Address.
     */
    public List<AddressId> storeScriptAddresses(final List<LockingScript> lockingScripts) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final HashMap<String, AddressId> addressIdMap = new HashMap<String, AddressId>(lockingScripts.getSize());
        final HashMap<ScriptWrapper, String> lockingScriptAddresses = new HashMap<ScriptWrapper, String>(lockingScripts.getSize());
        final MutableList<String> newAddresses = new MutableList<String>(lockingScripts.getSize());

        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO addresses (address) VALUES (?)");
        for (final LockingScript lockingScript : lockingScripts) {
            final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
            if (scriptType == ScriptType.CUSTOM_SCRIPT) {
                lockingScriptAddresses.put(new ScriptWrapper(lockingScript), null);
                continue;
            }

            final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            if (address == null) {
                Logger.log("Error determining address.");
                return null;
            }

            final String addressString = address.toBase58CheckEncoded();
            lockingScriptAddresses.put(new ScriptWrapper(lockingScript), addressString);
            newAddresses.add(addressString);

            batchedInsertQuery.setParameter(addressString);
        }

        if (! newAddresses.isEmpty()) {
            databaseConnection.executeSql(batchedInsertQuery);
        }

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, address FROM addresses WHERE address IN (" + DatabaseUtil.createInClause(newAddresses) + ")")
        );
        for (final Row row : rows) {
            final AddressId addressId = AddressId.wrap(row.getLong("id"));
            final String address = row.getString("address");

            addressIdMap.put(address, addressId);
        }

        final MutableList<AddressId> addressIds = new MutableList<AddressId>(lockingScripts.getSize());
        for (final LockingScript lockingScript : lockingScripts) {
            final String address = lockingScriptAddresses.get(new ScriptWrapper(lockingScript));
            if (address == null) {
                addressIds.add(null);
            }
            else {
                final AddressId addressId = addressIdMap.get(address);
                if (addressId == null) { return null; }

                addressIds.add(addressId);
            }
        }

        return addressIds;
    }

    public AddressId getAddressId(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, transaction_output_id, address_id FROM locking_scripts WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return AddressId.wrap(row.getLong("address_id"));
    }

    public AddressId getAddressId(final String addressString) throws DatabaseException {
        return _getAddressId(addressString);
    }

    public AddressId getAddressId(final Address address) throws DatabaseException {
        return _getAddressId(address.toBase58CheckEncoded());
    }

    public List<SpendableTransactionOutput> getSpendableTransactionOutputs(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId) throws DatabaseException {
        return _getAddressOutputs(blockchainSegmentId, addressId);
    }

    /**
     * Returns a set of TransactionIds that either spend from or send to the provided AddressId.
     */
    public List<TransactionId> getTransactionIds(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        final List<TransactionId> transactionIdsIn = _getTransactionIdsSendingTo(blockchainSegmentId, addressId, includeUnconfirmedTransactions);
        final List<TransactionId> transactionIdsOut = _getTransactionIdsSpendingFrom(blockchainSegmentId, addressId, includeUnconfirmedTransactions);

        final ImmutableListBuilder<TransactionId> transactionIds = new ImmutableListBuilder<TransactionId>(transactionIdsIn.getSize() + transactionIdsOut.getSize());
        transactionIds.addAll(transactionIdsIn);
        transactionIds.addAll(transactionIdsOut);
        return transactionIds.build();
    }

    /**
     * Returns a set of TransactionIds that send to the provided AddressId.
     */
    public List<TransactionId> getTransactionIdsSendingTo(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        return _getTransactionIdsSendingTo(blockchainSegmentId, addressId, includeUnconfirmedTransactions);
    }

    /**
     * Returns a set of TransactionIds that spend from the provided AddressId.
     */
    public List<TransactionId> getTransactionIdsSpendingFrom(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId, final Boolean includeUnconfirmedTransactions) throws DatabaseException {
        return _getTransactionIdsSpendingFrom(blockchainSegmentId, addressId, includeUnconfirmedTransactions);
    }

    public Long getAddressBalance(final BlockchainSegmentId blockchainSegmentId, final AddressId addressId) throws DatabaseException {
        final List<SpendableTransactionOutput> spendableTransactionOutputs = _getAddressOutputs(blockchainSegmentId, addressId);

        long amount = 0;
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            if (! spendableTransactionOutput.wasSpent()) {
                amount += spendableTransactionOutput.getAmount();
            }
        }

        return amount;
    }
}

package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.input.TransactionInputId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.io.Logger;

import java.math.BigInteger;
import java.util.HashMap;

public class AddressDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    public static class SpendableTransactionOutput {
        protected BlockId _blockId;
        protected TransactionId _transactionId;
        protected TransactionOutputId _transactionOutputId;
        protected Long _amount;
        protected TransactionInputId _spentByTransactionInputId;

        public BlockId getBlockId() { return _blockId; }
        public TransactionId getTransactionId() { return _transactionId; }
        public TransactionOutputId getTransactionOutputId() { return _transactionOutputId; }
        public Long getAmount() { return _amount; }
        public TransactionInputId getSpentByTransactionInputId() { return _spentByTransactionInputId; }

        public Boolean wasSpent() { return (_spentByTransactionInputId != null); }
        public Boolean isMined() { return (_blockId != null); }
    }

    protected List<SpendableTransactionOutput> _getAddressOutputs(final AddressId addressId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query(
                "SELECT " +
                        "transactions.block_id, transactions.id AS transaction_id, transaction_outputs.id AS transaction_output_id, transaction_outputs.amount " +
                    "FROM " +
                        "transactions " +
                        "INNER JOIN transaction_outputs " +
                            "ON transactions.id = transaction_outputs.transaction_id " +
                        "INNER JOIN locking_scripts " +
                            "ON transaction_outputs.id = locking_scripts.transaction_output_id " +
                    "WHERE " +
                        "locking_scripts.address_id = ?"
            )
            .setParameter(addressId)
        );

        if (rows.isEmpty()) { return new MutableList<SpendableTransactionOutput>(); }

        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final MutableList<SpendableTransactionOutput> spendableTransactionOutputs = new MutableList<SpendableTransactionOutput>(rows.size());

        for (final Row row : rows) {
            final BlockId blockId = BlockId.wrap(row.getLong("block_id"));

            final SpendableTransactionOutput spendableTransactionOutput = new SpendableTransactionOutput();
            {
                spendableTransactionOutput._blockId = blockId;
                spendableTransactionOutput._transactionId = TransactionId.wrap(row.getLong("transaction_id"));
                spendableTransactionOutput._transactionOutputId = TransactionOutputId.wrap(row.getLong("transaction_output_id"));
                spendableTransactionOutput._amount = row.getLong("amount");
            }

            if (spendableTransactionOutput.isMined()) {
                final Boolean transactionWasMinedOnMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANY);

                if (! transactionWasMinedOnMainChain) {
                    continue;
                }
            }

            {
                final java.util.List<Row> rowsSpendingTransactionOutput = _databaseConnection.query(
                    new Query(
                        "SELECT " +
                                "transactions.block_id, transactions.id AS transaction_id, transaction_inputs.id AS transaction_input_id " +
                            "FROM " +
                                "transaction_inputs " +
                                "INNER JOIN transactions " +
                                    "ON transactions.id = transaction_inputs.transaction_id " +
                            "WHERE " +
                                "previous_transaction_output_id = ?"
                    )
                    .setParameter(spendableTransactionOutput._transactionOutputId)
                );

                for (final Row rowSpendingTransactionOutput : rowsSpendingTransactionOutput) {
                    final BlockId spendingBlockId = BlockId.wrap(rowSpendingTransactionOutput.getLong("block_id"));
                    final TransactionId spendingTransactionId = TransactionId.wrap(rowSpendingTransactionOutput.getLong("transaction_id"));
                    final TransactionInputId spendingTransactionInputId = TransactionInputId.wrap(rowSpendingTransactionOutput.getLong("transaction_input_id"));

                    final Boolean spendingTransactionIsMined = (spendingBlockId != null);

                    final Boolean transactionOutputHasBeenSpent;
                    if (! spendingTransactionIsMined) {
                        transactionOutputHasBeenSpent = true;
                    }
                    else {
                        final Boolean spendingTransactionIsMinedOnMainChain = (blockHeaderDatabaseManager.isBlockConnectedToChain(spendingBlockId, headBlockchainSegmentId, BlockRelationship.ANY));
                        transactionOutputHasBeenSpent = spendingTransactionIsMinedOnMainChain;
                    }

                    if (transactionOutputHasBeenSpent) {
                        spendableTransactionOutput._spentByTransactionInputId = spendingTransactionInputId;
                        break;
                    }
                }
            }

            spendableTransactionOutputs.add(spendableTransactionOutput);
        }

        return spendableTransactionOutputs;
    }

    public AddressDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public AddressId storeScriptAddress(final LockingScript lockingScript) throws DatabaseException {
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

        final AddressId cachedAddressId = _databaseManagerCache.getCachedAddressId(addressString);
        if (cachedAddressId != null) { return cachedAddressId; }

        {
            final AddressId addressId = AddressId.wrap(_databaseConnection.executeSql(
                new Query("INSERT IGNORE INTO addresses (address) VALUES (?)")
                    .setParameter(addressString)
            ));

            if ( (addressId != null) && (addressId.longValue() > 0) ) {
                _databaseManagerCache.cacheAddressId(addressString, addressId);
                return addressId;
            }
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        final Row row = rows.get(0);
        final AddressId addressId = AddressId.wrap(row.getLong("id"));

        _databaseManagerCache.cacheAddressId(addressString, addressId);

        return addressId;
    }

    /**
     * ScriptWrapper is a wrapper around LockingScript so that hashCode and equals uses simple checks instead of
     *  the more complicated Script implementations.
     */
    static class ScriptWrapper {
        public final LockingScript lockingScript;

        public ScriptWrapper(final LockingScript lockingScript) {
            this.lockingScript = lockingScript;
        }

        @Override
        public int hashCode() {
            return this.lockingScript.simpleHashCode();
        }

        @Override
        public boolean equals(final Object object) {
            return this.lockingScript.simpleEquals(object);
        }
    }

    public List<AddressId> storeScriptAddresses(final List<LockingScript> lockingScripts) throws DatabaseException {
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
            _databaseConnection.executeSql(batchedInsertQuery);
        }

        final java.util.List<Row> rows = _databaseConnection.query(
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
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, transaction_output_id, address_id FROM locking_scripts WHERE transaction_output_id = ?")
                .setParameter(transactionOutputId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return AddressId.wrap(row.getLong("address_id"));
    }

    public AddressId getAddressId(final String addressString) throws DatabaseException {
        final AddressId cachedAddressId = _databaseManagerCache.getCachedAddressId(addressString);
        if (cachedAddressId != null) {
            return cachedAddressId;
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final AddressId addressId = AddressId.wrap(row.getLong("id"));

        _databaseManagerCache.cacheAddressId(addressString, addressId);

        return addressId;
    }

    public List<SpendableTransactionOutput> getSpendableTransactionOutputs(final AddressId addressId) throws DatabaseException {
        return _getAddressOutputs(addressId);
    }

    public BigInteger getAddressBalance(final AddressId addressId) throws DatabaseException {
        final List<SpendableTransactionOutput> spendableTransactionOutputs = _getAddressOutputs(addressId);

        BigInteger amount = BigInteger.ZERO;
        for (final SpendableTransactionOutput spendableTransactionOutput : spendableTransactionOutputs) {
            if (! spendableTransactionOutput.wasSpent()) {
                amount = amount.add(BigInteger.valueOf(spendableTransactionOutput.getAmount()));
            }
        }

        return amount;
    }
}

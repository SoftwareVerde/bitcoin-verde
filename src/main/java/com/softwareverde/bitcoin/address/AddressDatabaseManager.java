package com.softwareverde.bitcoin.address;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockRelationship;
import com.softwareverde.bitcoin.server.database.cache.AddressIdCache;
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
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

public class AddressDatabaseManager {
    public static final AddressIdCache ADDRESS_CACHE = new AddressIdCache();

    protected final MysqlDatabaseConnection _databaseConnection;

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

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
        final BlockChainSegmentId headBlockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(headBlockId);

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
                final Boolean transactionWasMinedOnMainChain = blockDatabaseManager.isBlockConnectedToChain(blockId, headBlockChainSegmentId, BlockRelationship.ANY);

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
                        final Boolean spendingTransactionIsMinedOnMainChain = (blockDatabaseManager.isBlockConnectedToChain(spendingBlockId, headBlockChainSegmentId, BlockRelationship.ANY));
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

    public AddressDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public AddressId storeScriptAddress(final LockingScript lockingScript) throws DatabaseException {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        if (scriptType == ScriptType.UNKNOWN) {
            return null;
        }

        final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
        if (address == null) {
            Logger.log("Error determining address.");
            return null;
        }

        final String addressString = address.toBase58CheckEncoded();

        final AddressId cachedAddressId = ADDRESS_CACHE.getCachedAddressId(addressString);
        if (cachedAddressId != null) {
            return cachedAddressId;
        }

        {
            final AddressId addressId = AddressId.wrap(_databaseConnection.executeSql(
                new Query("INSERT IGNORE INTO addresses (address) VALUES (?)")
                    .setParameter(addressString)
            ));

            if ( (addressId != null) && (addressId.longValue() > 0) ) {
                ADDRESS_CACHE.cacheAddressId(addressId, addressString);
                return addressId;
            }
        }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        final Row row = rows.get(0);
        final AddressId addressId = AddressId.wrap(row.getLong("id"));

        ADDRESS_CACHE.cacheAddressId(addressId, addressString);

        return addressId;
    }

    static class DuplicateAddress extends AddressId {
        protected DuplicateAddress(final String addressString) {
            super(-2L);

            this.addressString = addressString;
        }

        public final String addressString;
    }

    public List<AddressId> storeScriptAddresses(final List<LockingScript> lockingScripts) throws DatabaseException {
        final MutableList<AddressId> addressIds = new MutableList<AddressId>(lockingScripts.getSize());
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final AddressId INVALID = AddressId.wrap(-1L);
        final AddressId DUPLICATE = AddressId.wrap(-2L);

        final Query batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO addresses (address) VALUES (?)");

        final Set<String> newAddresses = new HashSet<String>(lockingScripts.getSize());

        int insertCount = 0;
        for (final LockingScript lockingScript : lockingScripts) {
            final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
            if (scriptType == ScriptType.UNKNOWN) {
                addressIds.add(INVALID);
                continue;
            }

            final Address address = scriptPatternMatcher.extractAddress(scriptType, lockingScript);
            if (address == null) {
                Logger.log("Error determining address.");
                return null;
            }

            final String addressString = address.toBase58CheckEncoded();

            final AddressId cachedAddressId = ADDRESS_CACHE.getCachedAddressId(addressString);
            if (cachedAddressId != null) {
                addressIds.add(cachedAddressId);
                continue;
            }
            else {
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id FROM addresses WHERE address = ?")
                        .setParameter(addressString)
                );

                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    final AddressId addressId = AddressId.wrap(row.getLong("id"));
                    addressIds.add(addressId);

                    ADDRESS_CACHE.cacheAddressId(addressId, addressString);

                    continue;
                }
            }

            if (newAddresses.contains(addressString)) {
                addressIds.add(new DuplicateAddress(addressString));
                continue;
            }

            batchedInsertQuery.setParameter(addressString);
            addressIds.add(null);
            insertCount += 1;

            newAddresses.add(addressString);
        }

        final Long firstAddressId;
        if (insertCount > 0) {
            firstAddressId = _databaseConnection.executeSql(batchedInsertQuery);
            if (firstAddressId == null) { return null; }
        }
        else {
            firstAddressId = null;
        }

        Long nextAddressId = firstAddressId;
        for (int i = 0; i < addressIds.getSize(); ++i) {
            final AddressId addressId = addressIds.get(i);

            if (addressId == INVALID) {
                addressIds.set(i, null);
            }
            else if (Util.areEqual(addressId, DUPLICATE)) {
                final DuplicateAddress duplicateAddress = (DuplicateAddress) addressId;

                final AddressId cachedAddressId = ADDRESS_CACHE.getCachedAddressId(duplicateAddress.addressString);
                if (cachedAddressId != null) {
                    addressIds.set(i, cachedAddressId);
                }
                else {
                    final java.util.List<Row> rows = _databaseConnection.query(
                        new Query("SELECT id FROM addresses WHERE address = ?")
                            .setParameter(duplicateAddress.addressString)
                    );

                    final Row row = rows.get(0);
                    final AddressId duplicateAddressId = AddressId.wrap(row.getLong("id"));
                    addressIds.set(i, duplicateAddressId);

                    ADDRESS_CACHE.cacheAddressId(duplicateAddressId, duplicateAddress.addressString);
                }
            }
            else if (addressId == null) {
                addressIds.set(i, AddressId.wrap(nextAddressId));
                nextAddressId += 1L;
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
        final AddressId cachedAddressId = ADDRESS_CACHE.getCachedAddressId(addressString);
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

        ADDRESS_CACHE.cacheAddressId(addressId, addressString);

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

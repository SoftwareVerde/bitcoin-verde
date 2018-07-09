package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.script.Script;
import com.softwareverde.bitcoin.transaction.script.ScriptPatternMatcher;
import com.softwareverde.bitcoin.transaction.script.ScriptType;
import com.softwareverde.bitcoin.transaction.script.locking.LockingScript;
import com.softwareverde.bitcoin.transaction.script.locking.MutableLockingScript;
import com.softwareverde.bitcoin.type.address.Address;
import com.softwareverde.bitcoin.type.address.AddressId;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.util.List;

public class TransactionOutputDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected TransactionOutputId _findTransactionOutput(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM transaction_outputs WHERE transaction_id = ? AND `index` = ?")
                .setParameter(transactionId)
                .setParameter(transactionOutputIndex)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return TransactionOutputId.wrap(row.getLong("id"));
    }

    protected TransactionOutputId _insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        final LockingScript lockingScript = transactionOutput.getLockingScript();

        final Long transactionOutputIdLong = _databaseConnection.executeSql(
            new Query("INSERT INTO transaction_outputs (transaction_id, `index`, amount) VALUES (?, ?, ?)")
                .setParameter(transactionId)
                .setParameter(transactionOutput.getIndex())
                .setParameter(transactionOutput.getAmount())
        );

        final TransactionOutputId transactionOutputId = TransactionOutputId.wrap(transactionOutputIdLong);
        if (transactionOutputId == null) { return null; }

        _insertLockingScript(transactionOutputId, lockingScript);

        return transactionOutputId;
    }

    public void _insertLockingScript(final TransactionOutputId transactionOutputId, final LockingScript lockingScript) throws DatabaseException {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();
        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        final AddressId addressId = (scriptType != ScriptType.UNKNOWN ? _storeScriptAddress(lockingScript) : null);

        _databaseConnection.executeSql(
            new Query("INSERT INTO locking_scripts (type, transaction_output_id, script, address_id) VALUES (?, ?, ?, ?)")
                .setParameter(scriptType)
                .setParameter(transactionOutputId)
                .setParameter(lockingScript.getBytes().getBytes())
                .setParameter(addressId)
        );
    }

    protected TransactionOutput _getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        final Row transactionOutputRow;
        {
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT * FROM transaction_outputs WHERE id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            transactionOutputRow = rows.get(0);
        }

        final LockingScript lockingScript;
        {
            final List<Row> rows = _databaseConnection.query(
                new Query("SELECT id, script FROM locking_scripts WHERE transaction_output_id = ?")
                    .setParameter(transactionOutputId)
            );
            if (rows.isEmpty()) { return null; }

            final Row row = rows.get(0);
            lockingScript = new MutableLockingScript(row.getBytes("script"));
        }

        final MutableTransactionOutput mutableTransactionOutput = new MutableTransactionOutput();
        mutableTransactionOutput.setIndex(transactionOutputRow.getInteger("index"));
        mutableTransactionOutput.setAmount(transactionOutputRow.getLong("amount"));
        mutableTransactionOutput.setLockingScript(lockingScript);
        return mutableTransactionOutput;
    }

    public AddressId _storeScriptAddress(final Script lockingScript) throws DatabaseException {
        final ScriptPatternMatcher scriptPatternMatcher = new ScriptPatternMatcher();

        final ScriptType scriptType = scriptPatternMatcher.getScriptType(lockingScript);
        if (scriptType == ScriptType.UNKNOWN) {
            return null;
        }

        final Address address;
        {
            switch (scriptType) {
                case PAY_TO_PUBLIC_KEY_HASH: {
                    address = scriptPatternMatcher.extractAddressFromPayToPublicKeyHash(lockingScript);
                } break;

                case PAY_TO_SCRIPT_HASH: {
                    address = scriptPatternMatcher.extractAddressFromPayToScriptHash(lockingScript);
                } break;

                default: {
                    address = null;
                } break;
            }
        }

        if (address == null) {
            Logger.log("Error determining address.");
            return null;
        }

        final String addressString = address.toBase58CheckEncoded();

        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM addresses WHERE address = ?")
                .setParameter(addressString)
        );

        if (! rows.isEmpty()) {
            final Row row = rows.get(0);
            return AddressId.wrap(row.getLong("id"));
        }

        return AddressId.wrap(_databaseConnection.executeSql(
            new Query("INSERT INTO addresses (address) VALUES (?)")
                .setParameter(addressString)
        ));
    }

    public TransactionOutputDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public TransactionOutputId insertTransactionOutput(final TransactionId transactionId, final TransactionOutput transactionOutput) throws DatabaseException {
        return _insertTransactionOutput(transactionId, transactionOutput);
    }

    public TransactionOutputId findTransactionOutput(final TransactionId transactionId, final Integer transactionOutputIndex) throws DatabaseException {
        return _findTransactionOutput(transactionId, transactionOutputIndex);
    }

    public TransactionOutput getTransactionOutput(final TransactionOutputId transactionOutputId) throws DatabaseException {
        return _getTransactionOutput(transactionOutputId);
    }
}

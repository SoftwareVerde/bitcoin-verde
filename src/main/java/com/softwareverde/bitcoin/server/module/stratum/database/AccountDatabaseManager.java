package com.softwareverde.bitcoin.server.module.stratum.database;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.ParsedAddress;
import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.pbkdf2.Pbkdf2Key;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.Util;

public class AccountDatabaseManager {
    protected final DatabaseConnection _databaseConnection;

    protected Boolean _authenticateAccount(final AccountId accountId, final String password) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, password, salt, iterations FROM accounts WHERE id = ?")
                .setParameter(accountId)
        );
        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final ByteArray passwordKey = ByteArray.fromHexString(row.getString("password"));
        final ByteArray salt = ByteArray.fromHexString(row.getString("salt"));
        final Integer iterations = row.getInteger("iterations");
        final Integer keyBitCount = (passwordKey.getByteCount() * 8);

        final Pbkdf2Key providedPbkdf2Key = new Pbkdf2Key(password, iterations, salt, keyBitCount);
        final ByteArray providedKey = providedPbkdf2Key.getKey();

        if (! Util.areEqual(passwordKey, providedKey)) { return false; }

        return true;
    }

    public AccountDatabaseManager(final DatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public AccountId getAccountId(final String email) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM accounts WHERE email = ?")
                .setParameter(email)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return AccountId.wrap(row.getLong("id"));
    }

    public AccountId createAccount(final String email, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password);
        final ByteArray keyByteArray = pbkdf2Key.getKey();
        final ByteArray saltByteArray = pbkdf2Key.getSalt();

        final Long accountId = _databaseConnection.executeSql(
            new Query("INSERT INTO accounts (email, password, salt, iterations) VALUES (?, ?, ?, ?)")
                .setParameter(email)
                .setParameter(keyByteArray.toString())
                .setParameter(saltByteArray.toString())
                .setParameter(pbkdf2Key.getIterations())
        );

        return AccountId.wrap(accountId);
    }

    public void setAccountPassword(final AccountId accountId, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password);
        final ByteArray keyByteArray = pbkdf2Key.getKey();
        final ByteArray saltByteArray = pbkdf2Key.getSalt();

        _databaseConnection.executeSql(
            new Query("UPDATE accounts SET password = ?, salt = ?, iterations = ? WHERE id = ?")
                .setParameter(keyByteArray.toString())
                .setParameter(saltByteArray.toString())
                .setParameter(pbkdf2Key.getIterations())
                .setParameter(accountId)
        );
    }

    public Boolean authenticateAccount(final AccountId accountId, final String password) throws DatabaseException {
        return _authenticateAccount(accountId, password);
    }

    public AccountId authenticateAccount(final String email, final String password) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM accounts WHERE email = ?")
                .setParameter(email)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final AccountId accountId = AccountId.wrap(row.getLong("id"));

        final Boolean isAuthenticated = (_authenticateAccount(accountId, password));
        return (isAuthenticated ? accountId : null);
    }

    public ParsedAddress getPayoutAddress(final AccountId accountId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, payout_address FROM accounts WHERE id = ?")
                .setParameter(accountId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final String addressString = row.getString("payout_address");
        final AddressInflater addressInflater = new AddressInflater();
        return addressInflater.fromBase58Check(addressString);
    }

    public void setPayoutAddress(final AccountId accountId, final ParsedAddress address) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE accounts SET payout_address = ? WHERE id = ?")
                .setParameter(address != null ? address.toString() : null)
                .setParameter(accountId)
        );
    }
}

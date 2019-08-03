package com.softwareverde.bitcoin.server.module.stratum.database;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.miner.pool.AccountId;
import com.softwareverde.bitcoin.miner.pool.WorkerId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.security.pbkdf2.Pbkdf2Key;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class AccountDatabaseManager {
    protected static final Integer WORKER_PASSWORD_ITERATIONS = 8; // Authenticating workers happens much more frequently than account authentications...

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

    public Address getPayoutAddress(final AccountId accountId) throws DatabaseException {
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

    public void setPayoutAddress(final AccountId accountId, final Address address) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("UPDATE accounts SET payout_address = ? WHERE id = ?")
                .setParameter((address != null ? address.toBase58CheckEncoded() : null))
                .setParameter(accountId)
        );
    }

    public WorkerId createWorker(final AccountId accountId, final String username, final String password) throws DatabaseException {
        final Pbkdf2Key pbkdf2Key = new Pbkdf2Key(password, WORKER_PASSWORD_ITERATIONS, Pbkdf2Key.generateRandomSalt(), Pbkdf2Key.DEFAULT_KEY_BIT_COUNT);
        final ByteArray keyByteArray = pbkdf2Key.getKey();
        final ByteArray saltByteArray = pbkdf2Key.getSalt();

        final Long workerId = _databaseConnection.executeSql(
            new Query("INSERT INTO workers (account_id, username, password, salt, iterations) VALUES (?, ?, ?, ?, ?)")
                .setParameter(accountId)
                .setParameter(username)
                .setParameter(keyByteArray.toString())
                .setParameter(saltByteArray.toString())
                .setParameter(pbkdf2Key.getIterations())
        );

        return WorkerId.wrap(workerId);
    }

    public WorkerId getWorkerId(final String username) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM workers WHERE username = ?")
                .setParameter(username)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long workerId = row.getLong("id");
        return WorkerId.wrap(workerId);
    }

    public AccountId getWorkerAccountId(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, account_id FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long accountId = row.getLong("account_id");
        return AccountId.wrap(accountId);
    }

    public void deleteWorker(final WorkerId workerId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
    }

    public List<WorkerId> getWorkerIds(final AccountId accountId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM workers WHERE account_id = ?")
                .setParameter(accountId)
        );

        final ImmutableListBuilder<WorkerId> workerIds = new ImmutableListBuilder<WorkerId>(rows.size());
        for (final Row row : rows) {
            final WorkerId workerId = WorkerId.wrap(row.getLong("id"));
            if (workerId == null) { continue; }
            workerIds.add(workerId);
        }
        return workerIds.build();
    }

    public String getWorkerUsername(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, username FROM workers WHERE id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getString("username");
    }

    public WorkerId authenticateWorker(final String username, final String password) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, password, salt, iterations FROM workers WHERE username = ?")
                .setParameter(username)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final Long workerId = row.getLong("id");
        final ByteArray passwordKey = ByteArray.fromHexString(row.getString("password"));
        final ByteArray salt = ByteArray.fromHexString(row.getString("salt"));
        final Integer iterations = row.getInteger("iterations");
        final Integer keyBitCount = (passwordKey.getByteCount() * 8);

        final Pbkdf2Key providedPbkdf2Key = new Pbkdf2Key(password, iterations, salt, keyBitCount);
        final ByteArray providedKey = providedPbkdf2Key.getKey();

        if (! Util.areEqual(passwordKey, providedKey)) { return null; }

        return WorkerId.wrap(workerId);
    }

    public void addWorkerShare(final WorkerId workerId, final Integer difficulty) throws DatabaseException {
        final SystemTime systemTime = new SystemTime();

        _databaseConnection.executeSql(
            new Query("INSERT INTO worker_shares (worker_id, difficulty, timestamp) VALUES (?, ?, ?)")
                .setParameter(workerId)
                .setParameter(difficulty)
                .setParameter(systemTime.getCurrentTimeInSeconds())
        );
    }

    public Long getWorkerSharesCount(final WorkerId workerId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT COUNT(*) AS shares_count FROM worker_shares WHERE worker_id = ?")
                .setParameter(workerId)
        );
        if (rows.isEmpty()) { return 0L; }

        final Row row = rows.get(0);
        return row.getLong("shares_count");
    }
}

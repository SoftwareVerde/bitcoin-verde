package com.softwareverde.bitcoin.server.module.stratum.api;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.json.Json;
import com.softwareverde.json.Jsonable;
import com.softwareverde.util.DateUtil;
import com.softwareverde.util.HashUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Account implements Jsonable {
    private static final Map<Long, Account> _cachedAccounts = new ConcurrentHashMap<>();

    public static String hashPassword(final String rawPassword) {
        return HashUtil.sha256(HashUtil.sha256(rawPassword));
    }

    /**
     * Retrieves the Account from the Database or the cache if already loaded.
     * @param id                    - The id of the Account. Returns null if the id is null or equal to zero.
     * @param databaseConnection    - May be null if the id exists in the cache.
     * @return
     */
    synchronized public static Account loadAccount(final Long id, final MysqlDatabaseConnection databaseConnection) {
        if ((id == null) || (id == 0)) { return null; }

        if (_cachedAccounts.containsKey(id)) {
            return _cachedAccounts.get(id);
        }
        else if (databaseConnection == null) { return null; }

        final List<Row> rows;
        try {
            rows = databaseConnection.query("SELECT * FROM accounts WHERE id = ?", new String[]{id.toString()});
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
        if (rows.isEmpty()) { return null; }

        final Account account = new Account(id);
        _cachedAccounts.put(id, account); // NOTE: Append Account to cache before loading values to avoid circular dependencies.

        final Row accountRow = rows.get(0);
        // account._id = Util.parseLong(accountRow.getValue("id"));
        account._email = accountRow.getString("email");
        account._password = accountRow.getString("password");
        account._isEnabled = accountRow.getBoolean("is_enabled");
        account._lastActivity = DateUtil.datetimeToTimestamp(accountRow.getString("last_activity"));
        account._authorizationToken = accountRow.getString("authorization_token");

        return account;
    }

    /**
     * Retrieves the Account from the Database with the provided email and password.
     * @param email                 - The email of the Account.
     * @param password              - The password for the Account.
     * @param databaseConnection    - The database to retrieve the account from.
     * @return                      - Returns null if an account/password combination is not found.
     */
    public static Account loadAccount(final String email, final String password, final MysqlDatabaseConnection databaseConnection) {
        final List<Row> rows;
        try {
            rows = databaseConnection.query("SELECT id FROM accounts WHERE email = ? AND password = ?", new String[]{email, Account.hashPassword(password)});
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Account.loadAccount(row.getLong("id"), databaseConnection);
    }

    /**
     * Retrieves the Account from the Database with the provided email.
     * @param email                 - The email of the Account.
     * @param databaseConnection    - The database to retrieve the account from.
     * @return                      - Returns null if an account is not found.
     */
    public static Account loadAccount(final String email, final MysqlDatabaseConnection databaseConnection) {
        final List<Row> rows;
        try {
            rows = databaseConnection.query("SELECT id FROM accounts WHERE email = ?", new String[]{email});
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Account.loadAccount(row.getLong("id"), databaseConnection);
    }

    /**
     * Retrieves the Account from the Database with the provided authorizationToken.
     * @param authorizationToken    - The authorizationToken currently assigned to the Account.
     * @param databaseConnection    - The database to retrieve the account from.
     * @return                      - Returns null if an account is not found.
     */
    public static Account loadAccountFromAuthorizationToken(final String authorizationToken, final MysqlDatabaseConnection databaseConnection) {
        final List<Row> rows;
        try {
            rows = databaseConnection.query("SELECT id FROM accounts WHERE authorization_token = ?", new String[]{authorizationToken});
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return Account.loadAccount(row.getLong("id"), databaseConnection);
    }

    /**
     * Creates an Account with the provided email and password.
     * Returns null if the email already has an account.
     * @param email                 - The email of the Account.
     * @param password              - The database to retrieve the account from.
     * @param databaseConnection    - The database to retrieve the account from.
     * @return                      - Returns null if an account is not found.
     */
    public static Account createAccount(final String email, final String password, final MysqlDatabaseConnection databaseConnection) {
        if (Account.loadAccount(email, databaseConnection) != null) { return null; }

        final Long accountId;
        try {
            accountId = databaseConnection.executeSql(
                "INSERT INTO accounts (email, password) VALUES (?, ?)",
                new String[]{email, Account.hashPassword(password)}
            );
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return null;
        }

        return Account.loadAccount(accountId, databaseConnection);
    }

    /**
     * Clears the cache loaded from static load function.
     */
    public static void clearCache() {
        _cachedAccounts.clear();
    }

    protected final Long _id;
    protected String _email;
    protected String _password;
    volatile protected Boolean _isEnabled;
    volatile protected Long _lastActivity;

    protected String _authorizationToken;

    protected Account(final Long id) {
        _id = id;
    }

    public Long getId() { return _id; }
    public String getEmail() { return _email; }
    public String getPassword() { return _password; }
    public Boolean getIsEnabled() { return _isEnabled; }
    public Long getLastActivity() { return _lastActivity; }
    public String getAuthorizationToken() { return _authorizationToken; }

    public void setLastActivity(final Long lastActivity, final MysqlDatabaseConnection databaseConnection) {
        try {
            databaseConnection.executeSql(
                "UPDATE accounts SET last_activity = ? WHERE id = ?",
                new String[]{
                    (lastActivity != null ? lastActivity.toString() : null),
                    _id.toString()
                }
            );
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
        _lastActivity = lastActivity;
    }

    public void setAuthorizationToken(final String authorizationToken, final MysqlDatabaseConnection databaseConnection) {
        try {
            databaseConnection.executeSql(
                "UPDATE accounts SET authorization_token = ? WHERE id = ?",
                new String[]{
                    authorizationToken,
                    _id.toString()
                }
            );
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
        _authorizationToken = authorizationToken;
    }

    /**
     * Thread-safe.
     */
    @Override
    public Json toJson() {
        final Json json = new Json();

        json.put("id", _id);
        // json.put("email", _email);
        json.put("isEnabled", (_isEnabled ? 1 : 0));
        json.put("lastActivity", _lastActivity);

        return json;
    }

    @Override
    public int hashCode() {
        return Account.class.getSimpleName().hashCode() + _id.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) { return false; }
        if (! (obj instanceof Account)) { return false; }

        final Account accountObject = (Account) obj;
        return _id.equals(accountObject._id);
    }
}
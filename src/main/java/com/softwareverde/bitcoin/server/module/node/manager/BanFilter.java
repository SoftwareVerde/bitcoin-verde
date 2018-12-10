package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public class BanFilter {
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    private MysqlDatabaseConnection _cachedDatabaseConnection;

    protected MysqlDatabaseConnection _getCachedDatabaseConnection() throws DatabaseException {
        if (_cachedDatabaseConnection == null) {
            _cachedDatabaseConnection = _databaseConnectionFactory.newConnection();
        }

        return _cachedDatabaseConnection;
    }

    protected void _closeDatabaseConnection() {
        final MysqlDatabaseConnection databaseConnection = _cachedDatabaseConnection;
        _cachedDatabaseConnection = null;

        if (databaseConnection != null) {
            try { databaseConnection.close(); } catch (final DatabaseException exception) { }
        }
    }

    public BanFilter(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public Boolean isHostBanned(final String host) {
        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Boolean isBanned = nodeDatabaseManager.isBanned(host);
            return isBanned;
        }
        catch (final DatabaseException exception) {
            _closeDatabaseConnection();

            Logger.log(exception);
            return false;
        }
    }

    public Boolean shouldBanHost(final String host) {
        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Integer failedConnectionCount = nodeDatabaseManager.getFailedConnectionCountForHost(host);
            return (failedConnectionCount >= BitcoinNodeManager.BanCriteria.FAILED_CONNECTION_ATTEMPT_COUNT);
        }
        catch (final DatabaseException exception) {
            _closeDatabaseConnection();

            Logger.log(exception);
        }

        return false;
    }

    public void banHost(final String host) {
        Logger.log("Banning Node: " + host);

        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.setIsBanned(host, true);
        }
        catch (final DatabaseException exception) {
            _closeDatabaseConnection();
            Logger.log(exception);
        }
    }

    public void close() {
        _closeDatabaseConnection();
    }
}

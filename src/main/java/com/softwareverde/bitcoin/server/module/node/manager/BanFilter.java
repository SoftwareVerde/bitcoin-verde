package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;

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

    public Boolean isIpBanned(final Ip ip) {
        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Boolean isBanned = nodeDatabaseManager.isBanned(ip);
            return isBanned;
        }
        catch (final DatabaseException exception) {
            _closeDatabaseConnection();

            Logger.log(exception);
            return false;
        }
    }

    public Boolean shouldBanIp(final Ip ip) {
        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Integer failedConnectionCount = nodeDatabaseManager.getFailedConnectionCountForIp(ip);
            return (failedConnectionCount >= BitcoinNodeManager.BanCriteria.FAILED_CONNECTION_ATTEMPT_COUNT);
        }
        catch (final DatabaseException exception) {
            _closeDatabaseConnection();

            Logger.log(exception);
        }

        return false;
    }

    public void banIp(final Ip ip) {
        Logger.log("Banning Node: " + ip);

        try {
            final MysqlDatabaseConnection databaseConnection = _getCachedDatabaseConnection();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.setIsBanned(ip, true);
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

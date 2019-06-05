package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;

public class BanFilter {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public BanFilter(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public Boolean isIpBanned(final Ip ip) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Boolean isBanned = nodeDatabaseManager.isBanned(ip);
            return isBanned;
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    public Boolean shouldBanIp(final Ip ip) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Integer failedConnectionCount = nodeDatabaseManager.getFailedConnectionCountForIp(ip);
            return (failedConnectionCount >= BitcoinNodeManager.BanCriteria.FAILED_CONNECTION_ATTEMPT_COUNT);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    public void banIp(final Ip ip) {
        Logger.log("Banning Node: " + ip);

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.setIsBanned(ip, true);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    public void unbanNode(final Ip ip) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.setIsBanned(ip, false);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }
}

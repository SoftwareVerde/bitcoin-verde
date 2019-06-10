package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;

public class BanFilter {
    protected final DatabaseManagerFactory _databaseManagerFactory;

    public BanFilter(final DatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    public Boolean isIpBanned(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            return nodeDatabaseManager.isBanned(ip);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            return false;
        }
    }

    public Boolean shouldBanIp(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

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

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            nodeDatabaseManager.setIsBanned(ip, true);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    public void unbanNode(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            nodeDatabaseManager.setIsBanned(ip, false);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }
}

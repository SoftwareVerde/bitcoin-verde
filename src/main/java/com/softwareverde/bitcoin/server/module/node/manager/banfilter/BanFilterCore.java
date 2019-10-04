package com.softwareverde.bitcoin.server.module.node.manager.banfilter;

import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashSet;

public class BanFilterCore implements BanFilter {
    public static class BanCriteria {
        public static final Integer FAILED_CONNECTION_ATTEMPT_COUNT = 10;
        public static final Long FAILED_CONNECTION_ATTEMPT_MILLISECOND_SPAN = 5000L;
    }

    protected static final Long MAX_BAN_DURATION = (60L * 60L); // 1 Hour (in seconds)...

    protected final SystemTime _systemTime = new SystemTime();
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final HashSet<Ip> _whitelist = new HashSet<Ip>();

    protected void _unbanIp(final Ip ip, final DatabaseManager databaseManager) throws DatabaseException {
        final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
        nodeDatabaseManager.setIsBanned(ip, false);

        Logger.debug("Unbanned " + ip);
    }

    protected void _banIp(final Ip ip, final DatabaseManager databaseManager) throws DatabaseException {
        final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
        nodeDatabaseManager.setIsBanned(ip, true);
        Logger.debug("Banned " + ip);
    }

    protected Boolean _shouldBanIp(final Ip ip, final DatabaseManager databaseManager) throws DatabaseException {
        final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
        final Long sinceTimestamp = (_systemTime.getCurrentTimeInSeconds() - BanCriteria.FAILED_CONNECTION_ATTEMPT_MILLISECOND_SPAN);
        final Integer failedConnectionCount = nodeDatabaseManager.getFailedConnectionCountForIp(ip, sinceTimestamp);
        final boolean shouldBanIp = (failedConnectionCount >= BanCriteria.FAILED_CONNECTION_ATTEMPT_COUNT);

        if (shouldBanIp) {
            Logger.debug("Ip (" + ip + ") failed to connect " + failedConnectionCount + " times within " + BanCriteria.FAILED_CONNECTION_ATTEMPT_MILLISECOND_SPAN + "ms.");
        }

        return shouldBanIp;
    }

    public BanFilterCore(final DatabaseManagerFactory databaseManagerFactory) {
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public Boolean isIpBanned(final Ip ip) {
        if (_whitelist.contains(ip)) {
            Logger.debug("IP is Whitelisted: " + ip);
            return false;
        }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final Long sinceTimestamp = (_systemTime.getCurrentTimeInSeconds() - MAX_BAN_DURATION);
            return nodeDatabaseManager.isBanned(ip, sinceTimestamp);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }
    }

    @Override
    public void banIp(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _banIp(ip, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    public void unbanIp(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _unbanIp(ip, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    public void onNodeConnected(final Ip ip) {
        // Nothing.
    }

    @Override
    public void onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
        // NOTE: The BitcoinNodeManager updates the handshake before the banFilter is invoked, therefore this code is disabled in order to avoid duplicate unnecessary updates.
        // try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
        //     final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
        //
        //     nodeDatabaseManager.updateLastHandshake(bitcoinNode);
        // }
        // catch (final DatabaseException databaseException) {
        //     Logger.warn(databaseException);
        // }
    }

    @Override
    public void onNodeDisconnected(final Ip ip) {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            if (_shouldBanIp(ip, databaseManager)) {
                _banIp(ip, databaseManager);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    @Override
    public void addIpToWhitelist(final Ip ip) {
        _whitelist.add(ip);

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _unbanIp(ip, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        Logger.debug("Added ip to Whitelist: " + ip);
    }

    @Override
    public void removeIpFromWhitelist(final Ip ip) {
        _whitelist.remove(ip);

        Logger.debug("Removed ip from Whitelist: " + ip);
    }
}

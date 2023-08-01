//package com.softwareverde.bitcoin.server.database.pool;
//
//import com.softwareverde.bitcoin.server.database.DatabaseConnection;
//import com.softwareverde.bitcoin.server.database.DatabaseConnectionCore;
//import com.softwareverde.bitcoin.util.ByteUtil;
//import com.softwareverde.database.DatabaseException;
//import com.softwareverde.database.mysql.MysqlDatabaseConnection;
//import com.softwareverde.database.properties.DatabaseProperties;
//import com.softwareverde.logging.Logger;
//import com.softwareverde.util.Util;
//import org.mariadb.jdbc.Configuration;
//import org.mariadb.jdbc.MariaDbPoolDataSource;
//
//import javax.management.MBeanServer;
//import javax.management.ObjectName;
//import java.lang.management.ManagementFactory;
//import java.net.URLEncoder;
//import java.nio.charset.StandardCharsets;
//import java.sql.Connection;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//public class MariaDbConnectionPool implements DatabaseConnectionPool {
//    protected static final String POOL_NAME = "MariaDbConnectionPool";
//
//    protected static class ConnectionWrapper extends DatabaseConnectionCore {
//        public ConnectionWrapper(final MysqlDatabaseConnection core) {
//            super(core);
//        }
//
//        @Override
//        public Integer getRowsAffectedCount() {
//            return ((MysqlDatabaseConnection) _core).getRowsAffectedCount();
//        }
//    }
//
//    protected final Integer _maxDatabaseConnectionCount;
//    protected final MariaDbPoolDataSource _dataSource;
//    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
//
//    protected MariaDbPoolDataSource _initDataSource(final DatabaseProperties databaseProperties) throws Exception {
//        final String hostname = databaseProperties.getHostname();
//        final Integer port = databaseProperties.getPort();
//        final String schema = databaseProperties.getSchema();
//        final String username = databaseProperties.getUsername();
//        final String password = databaseProperties.getPassword();
//
//        final Configuration.Builder builder = new Configuration.Builder();
//        builder.poolName(POOL_NAME);
//        builder.maxPoolSize(_maxDatabaseConnectionCount);
//        builder.maxAllowedPacket((int) (32L * ByteUtil.Unit.Binary.MEBIBYTES));
//        builder.maxIdleTime((int) TimeUnit.MINUTES.toSeconds(20));
//        builder.connectTimeout((int) TimeUnit.MINUTES.toSeconds(20));
//        builder.useResetConnection(false);
//        builder.autocommit(true);
//
//        builder.tcpKeepAlive(true);
//        builder.tcpAbortiveClose(true);
//        // builder.tcpKeepCount(0);
//        // builder.tcpKeepIdle(0);
//        // builder.tcpKeepInterval(0);
//
//        builder.addHost(hostname, port);
//        builder.database(schema);
//
//        builder.user(username);
//        // builder.password(password); // Intentionally not set.
//
//        // NOTE: To enable, the server must become more intelligent about which statements to cache, otherwise too many statements will be created.
//        // builder.cachePrepStmts(true);
//        // builder.useServerPrepStmts(true);
//        // builder.prepStmtCacheSize(256);
//
//        builder.useCompression(true);
//        builder.useReadAheadInput(true);
//        builder.minPoolSize(0);
//
//        final Configuration configuration = builder.build();
//
//        // NOTE: The password is set here manually because Configuration::toString obfuscates the password.
//        //  Additionally, setting the username/password directly on the DataSource causes a new connection attempt each time.
//        return new MariaDbPoolDataSource(configuration + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8));
//    }
//
//    public MariaDbConnectionPool(final DatabaseProperties databaseProperties, final Integer maxDatabaseConnectionCount) {
//        _maxDatabaseConnectionCount = maxDatabaseConnectionCount;
//
//        MariaDbPoolDataSource dataSource = null;
//        try {
//            dataSource = _initDataSource(databaseProperties);
//        }
//        catch (final Exception exception) {
//            Logger.warn("Unable to initialize database.", exception);
//            _isShutdown.set(true);
//        }
//        _dataSource = dataSource;
//    }
//
//    @Override
//    public DatabaseConnection newConnection() throws DatabaseException {
//        if (_isShutdown.get()) {
//            throw new DatabaseException("newConnection after shutdown");
//        }
//
//        try {
//            final Connection connection = _dataSource.getConnection();
//            return new ConnectionWrapper(new MysqlDatabaseConnection(connection));
//        }
//        catch (final Exception exception) {
//            throw new DatabaseException(exception);
//        }
//    }
//
//    @Override
//    public void close() {
//        if (_isShutdown.compareAndSet(false, true)) {
//            try {
//                _dataSource.close();
//            }
//            catch (final Exception exception) {
//                Logger.debug("Unable to close all idle connections", exception);
//            }
//        }
//    }
//
//    @Override
//    public Integer getInUseConnectionCount() {
//        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//        try {
//            final ObjectName filter = new ObjectName("org.mariadb.jdbc.pool:type=" + POOL_NAME + "*");
//            final java.util.Set<ObjectName> objectNames = server.queryNames(filter, null);
//            final ObjectName name = objectNames.iterator().next();
//
//            return Util.parseInt("" + server.getAttribute(name, "ActiveConnections"));
//        }
//        catch (final Exception exception) {
//            return null;
//        }
//    }
//
//    @Override
//    public Integer getAliveConnectionCount() {
//        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//        try {
//            final ObjectName filter = new ObjectName("org.mariadb.jdbc.pool:type=" + POOL_NAME + "*");
//            final java.util.Set<ObjectName> objectNames = server.queryNames(filter, null);
//            final ObjectName name = objectNames.iterator().next();
//
//            return Util.parseInt("" + server.getAttribute(name, "IdleConnections"));
//        }
//        catch (final Exception exception) {
//            return null;
//        }
//    }
//
//    @Override
//    public Integer getCurrentPoolSize() {
//        final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
//        try {
//            final ObjectName filter = new ObjectName("org.mariadb.jdbc.pool:type=" + POOL_NAME + "*");
//            final java.util.Set<ObjectName> objectNames = server.queryNames(filter, null);
//            final ObjectName name = objectNames.iterator().next();
//
//            final Integer activeConnections = Util.parseInt("" + server.getAttribute(name, "ActiveConnections"));
//            final Integer idleConnections = Util.parseInt("" + server.getAttribute(name, "IdleConnections"));
//            return (activeConnections + idleConnections);
//        }
//        catch (final Exception exception) {
//            return null;
//        }
//    }
//}

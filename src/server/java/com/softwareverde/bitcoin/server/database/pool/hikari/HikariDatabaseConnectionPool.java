package com.softwareverde.bitcoin.server.database.pool.hikari;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionCore;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.main.BitcoinVerdeDatabase;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Special thanks to Xavier Kr&agrave;l for his contributions to developing this code. (2019-09-23)
 */

public class HikariDatabaseConnectionPool implements DatabaseConnectionPool {
    protected static class HikariConnectionWrapper extends DatabaseConnectionCore {
        public HikariConnectionWrapper(final MysqlDatabaseConnection core) {
            super(core);
        }

        @Override
        public Integer getRowsAffectedCount() {
            return ((MysqlDatabaseConnection) _core).getRowsAffectedCount();
        }
    }

    protected final HikariDataSource _dataSource = new HikariDataSource();
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
    protected final LoggerInstance _logger = Logger.getInstance(this.getClass());

    protected void _initHikariDataSource(final DatabaseProperties databaseProperties) {
        // NOTE: Using the MariaDB driver causes an unbounded memory leak within MySQL 5.7 & 8 (and Percona 8).
        _dataSource.setDriverClassName(com.mysql.jdbc.Driver.class.getName());

        _dataSource.setConnectionInitSql("SET NAMES 'utf8mb4'");
        _dataSource.setConnectionTimeout(TimeUnit.SECONDS.toMillis(15));
        _dataSource.setMaxLifetime(TimeUnit.HOURS.toMillis(1)); // NOTE: MySQL has a default max of 8 hours.
        _dataSource.setMaximumPoolSize(BitcoinVerdeDatabase.MAX_DATABASE_CONNECTION_COUNT); // NOTE: MySQL default is 151.
        _dataSource.setMinimumIdle(4);
        _dataSource.setAutoCommit(true);
        _dataSource.setLeakDetectionThreshold(5L * 60 * 1000L); // 5 Minutes, which should cover the longest single-connection subprocess.

        final String hostname = databaseProperties.getHostname();
        final Integer port = databaseProperties.getPort();
        final String schema = databaseProperties.getSchema();
        final String username = databaseProperties.getUsername();
        final String password = databaseProperties.getPassword();

        final String connectionString = MysqlDatabaseConnectionFactory.createConnectionString(hostname, port, schema);
        _dataSource.setJdbcUrl(connectionString);
        _dataSource.setUsername(username);
        _dataSource.setPassword(password);

        // NOTE: Caching prepared statements are likely not the cause of a memory leak within MySQL, however it wasn't
        //  conclusively absolved, and since the performance benefit was not measured the caching is disabled.
//        { // Enable prepared statement caching...
//            final Properties config = new Properties();
//            config.setProperty("useServerPrepStmts", "true");
//            config.setProperty("rewriteBatchedStatements", "true");
//            config.setProperty("cachePrepStmts", "true");
//            config.setProperty("prepStmtCacheSize", "250");
//            config.setProperty("prepStmtCacheSqlLimit", "2048");
//            _dataSource.setDataSourceProperties(config);
//        }

        try {
            _dataSource.setLogWriter(new PrintWriter(new Writer() {
                @Override
                public void write(final char[] characterBuffer, final int readOffset, final int readByteCount) {
                    final StringBuilder stringBuilder = new StringBuilder();
                    for (int i = 0; i < readByteCount; ++i) {
                        stringBuilder.append(characterBuffer[readOffset + i]);
                    }
                    _logger.debug(stringBuilder.toString());
                }

                @Override
                public void flush() { }

                @Override
                public void close() { }
            }));
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public HikariDatabaseConnectionPool(final DatabaseProperties databaseProperties) {
        _initHikariDataSource(databaseProperties);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        if (_isShutdown.get()) {
            throw new DatabaseException("newConnection after shutdown");
        }

        try {
            final Connection connection = _dataSource.getConnection();
            return new HikariConnectionWrapper(new MysqlDatabaseConnection(connection));
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void close() {
        if (_isShutdown.compareAndSet(false, true)) {
            _dataSource.close();
        }
    }

    @Override
    public Integer getInUseConnectionCount() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getActiveConnections();
    }

    @Override
    public Integer getAliveConnectionCount() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getIdleConnections();
    }

    @Override
    public Integer getCurrentPoolSize() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getTotalConnections();
    }
}

package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionCore;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.LoggerInstance;
import org.apache.commons.dbcp2.BasicDataSource;

import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApacheCommonsDatabaseConnectionPool implements DatabaseConnectionPool {
    protected static class ConnectionWrapper extends DatabaseConnectionCore {
        public ConnectionWrapper(final MysqlDatabaseConnection core) {
            super(core);
        }

        @Override
        public Integer getRowsAffectedCount() {
            return ((MysqlDatabaseConnection) _core).getRowsAffectedCount();
        }

    }

    protected Integer _maxDatabaseConnectionCount;
    protected final BasicDataSource _dataSource = new BasicDataSource();
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
    protected final LoggerInstance _logger = Logger.getInstance(this.getClass());

    protected void _initDataSource(final DatabaseProperties databaseProperties) {
        // NOTE: Using the MariaDB driver causes an unbounded memory leak within MySQL 5.7 & 8 (and Percona 8).
        _dataSource.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());

        _dataSource.setConnectionInitSqls(Collections.singleton("SET NAMES 'utf8mb4'"));
        _dataSource.setMaxWaitMillis(TimeUnit.SECONDS.toMillis(15));
        _dataSource.setMaxConnLifetimeMillis(TimeUnit.HOURS.toMillis(1)); // NOTE: MySQL has a default max of 8 hours.
        _dataSource.setMaxTotal(_maxDatabaseConnectionCount); // NOTE: MySQL default is 151.
        _dataSource.setMinIdle(4);
        _dataSource.setDefaultAutoCommit(true);

        final String hostname = databaseProperties.getHostname();
        final Integer port = databaseProperties.getPort();
        final String schema = databaseProperties.getSchema();
        final String username = databaseProperties.getUsername();
        final String password = databaseProperties.getPassword();

        final String connectionString = MysqlDatabaseConnectionFactory.createConnectionString(hostname, port, schema);
        _dataSource.setUrl(connectionString);
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

        // Experimental features to mitigate server hang on com.mysql.jdbc.util.ReadAheadInputStream.fill...
        //  https://bugs.mysql.com/bug.php?id=31353
        //  https://bugs.mysql.com/bug.php?id=56411
        //  https://bugs.mysql.com/bug.php?id=9515
        _dataSource.setConnectionProperties("useUnbufferedInput=true;useReadAheadInput=false;socketTimeout=30000");

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

    public ApacheCommonsDatabaseConnectionPool(final DatabaseProperties databaseProperties, final Integer maxDatabaseConnectionCount) {
        _maxDatabaseConnectionCount = maxDatabaseConnectionCount;
        _initDataSource(databaseProperties);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        if (_isShutdown.get()) {
            throw new DatabaseException("newConnection after shutdown");
        }

        try {
            final Connection connection = _dataSource.getConnection();
            return new ConnectionWrapper(new MysqlDatabaseConnection(connection));
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    @Override
    public void close() {
        if (_isShutdown.compareAndSet(false, true)) {
            try {
                _dataSource.close();
            }
            catch (final SQLException exception) {
                Logger.debug("Unable to close all idle connections", exception);
            }
        }
    }

    @Override
    public Integer getInUseConnectionCount() {
        return _dataSource.getNumActive();
    }

    @Override
    public Integer getAliveConnectionCount() {
        return _dataSource.getNumIdle();
    }

    @Override
    public Integer getCurrentPoolSize() {
        return _dataSource.getNumActive() + _dataSource.getNumIdle();
    }
}
package com.softwareverde.bitcoin.server.database.pool.hikari;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionCore;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.properties.DatabaseProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Special thanks to Xavier Kràl for his contributions to developing this code. (2019-09-23)
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

    protected void _initHikariDataSource(final DatabaseProperties databaseProperties) {
        _dataSource.setDriverClassName(org.mariadb.jdbc.Driver.class.getName());
        _dataSource.setConnectionTestQuery("SELECT 1");
        _dataSource.setConnectionInitSql("SET NAMES 'utf8mb4'");
        _dataSource.setConnectionTimeout(TimeUnit.SECONDS.toMillis(5));
        _dataSource.setMaxLifetime(TimeUnit.MINUTES.toMillis(15));
        _dataSource.setMaximumPoolSize(128); // NOTE: MySQL Default is 151.
        _dataSource.setAutoCommit(true);

        final String hostname = databaseProperties.getHostname();
        final Integer port = databaseProperties.getPort();
        final String schema = databaseProperties.getSchema();
        final String username = databaseProperties.getUsername();
        final String password = databaseProperties.getPassword();

        _dataSource.setJdbcUrl("jdbc:mariadb://" + hostname + ":" + port + "/" + schema);
        _dataSource.setUsername(username);
        _dataSource.setPassword(password);
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

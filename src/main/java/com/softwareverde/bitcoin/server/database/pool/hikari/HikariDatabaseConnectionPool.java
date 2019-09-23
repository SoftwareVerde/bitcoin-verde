package com.softwareverde.bitcoin.server.database.pool.hikari;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.pool.CachedDatabaseConnection;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.logging.Logger;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class HikariDatabaseConnectionPool extends DatabaseConnectionPool {
    protected static class ConnectionWrapper extends DatabaseConnection {
        public ConnectionWrapper(final MysqlDatabaseConnection core) {
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
        _dataSource.setAutoCommit(true);
        // _dataSource.setTransactionIsolation("TRANSACTION_READ_UNCOMMITTED");

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
        super(null, 0, 0L);
        _initHikariDataSource(databaseProperties);
    }

    @Override
    public void returnToPool(final CachedDatabaseConnection cachedDatabaseConnection) {
        try {
            cachedDatabaseConnection.close();
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
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
            _dataSource.close();
        }
    }

    /**
     * Returns the number of connections that have not yet been returned to the pool.
     */
    @Override
    public Integer getInUseConnectionCount() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getActiveConnections();
    }

    /**
     * Returns the number of connections that have been created but have not yet been closed.
     *  This number does not necessarily account for pooled connections that have died.
     */
    @Override
    public Integer getAliveConnectionCount() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getIdleConnections();
    }

    /**
     * Returns the number of connections currently waiting and available within the pool.
     *  This number does not account for pooled connections that have died.
     *  This number should be equal to ::getAliveConnectionsCount + ::getInUseConnectionsCount
     */
    @Override
    public Integer getCurrentPoolSize() {
        final HikariPoolMXBean hikariPoolMXBean = _dataSource.getHikariPoolMXBean();
        return hikariPoolMXBean.getTotalConnections();
    }
}

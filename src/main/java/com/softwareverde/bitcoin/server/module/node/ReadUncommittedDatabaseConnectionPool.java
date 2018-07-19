package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;

import java.sql.Connection;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReadUncommittedDatabaseConnectionPool extends ReadUncommittedDatabaseConnectionFactory {
    private static final class CachedMysqlDatabaseConnection extends MysqlDatabaseConnection {
        final Collection<CachedMysqlDatabaseConnection> _connectionPool;

        public CachedMysqlDatabaseConnection(final Connection rawConnection, final Collection<CachedMysqlDatabaseConnection> connectionPool) {
            super(rawConnection);
            _connectionPool = connectionPool;
        }

        public void superClose() throws DatabaseException {
            super.close();
        }

        @Override
        public void close() {
            try {
                final Boolean isClosed = this.getRawConnection().isClosed();
                if (isClosed) { return; }
            }
            catch (final Exception exception) {
                try { this.close(); } catch (final Exception exception2) { }
                return;
            }

            _connectionPool.add(this);
        }
    }

    private Boolean _isShutdown = false;
    private final Object _mutex = new Object();
    private final ConcurrentLinkedQueue<CachedMysqlDatabaseConnection> _mysqlDatabaseConnections = new ConcurrentLinkedQueue<CachedMysqlDatabaseConnection>();

    public ReadUncommittedDatabaseConnectionPool(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        super(mysqlDatabaseConnectionFactory);
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        if (_isShutdown) { throw new DatabaseException("Connection pool has been shutdown."); }

        final MysqlDatabaseConnection cachedDatabaseConnection = _mysqlDatabaseConnections.poll();
        if (cachedDatabaseConnection != null) {
            return cachedDatabaseConnection;
        }

        final MysqlDatabaseConnection mysqlDatabaseConnection = super.newConnection();
        return new CachedMysqlDatabaseConnection(mysqlDatabaseConnection.getRawConnection(), _mysqlDatabaseConnections);
    }

    public void shutdown() {
        _isShutdown = true;

        CachedMysqlDatabaseConnection cachedConnection;
        while ((cachedConnection = _mysqlDatabaseConnections.poll()) != null) {
            try { cachedConnection.superClose(); } catch (final Exception exception) { }
        }
    }
}
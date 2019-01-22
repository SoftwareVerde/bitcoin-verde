package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MysqlDatabaseConnectionPool extends MysqlDatabaseConnectionFactory implements AutoCloseable {
    public static final Long DEFAULT_DEADLOCK_TIMEOUT = 30000L; // The number of milliseconds the pool will wait before allowing the maximum pool count to be exceeded.

    protected final class CachedMysqlDatabaseConnection extends MysqlDatabaseConnection {

        public CachedMysqlDatabaseConnection(final Connection rawConnection) {
            super(rawConnection);
        }

        public void superClose() throws DatabaseException {
            super.close();
        }

        @Override
        public void close() {
            Boolean connectionShouldBeCached = true;
            try {
                final Boolean isClosed = this.getRawConnection().isClosed();
                if (isClosed) {
                    connectionShouldBeCached = false;
                }
            }
            catch (final SQLException exception) {
                connectionShouldBeCached = false;
            }

            final Boolean queueIsFull = (_mysqlDatabaseConnections.size() >= _maxConnectionCount);

            if (connectionShouldBeCached && (! queueIsFull)) {
                _mysqlDatabaseConnections.add(this);
            }
            else {
                synchronized (_mutex) {
                    _connectionCount -= 1;
                }
                try { this.superClose(); } catch (final DatabaseException exception) { }
            }

            synchronized (_mutex) {
                _mutex.notify();
            }
        }
    }

    protected final Object _mutex = new Object();
    protected final Integer _maxConnectionCount;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final Long _deadlockTimeout;
    protected Integer _connectionCount = 0;
    protected Boolean _isShutdown = false;
    protected final ConcurrentLinkedQueue<CachedMysqlDatabaseConnection> _mysqlDatabaseConnections = new ConcurrentLinkedQueue<CachedMysqlDatabaseConnection>();

    public MysqlDatabaseConnectionPool(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory, final Integer maxConnectionCount) {
        super(null, null, null, null, null);

        _databaseConnectionFactory = mysqlDatabaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = DEFAULT_DEADLOCK_TIMEOUT;
    }

    public MysqlDatabaseConnectionPool(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory, final Integer maxConnectionCount, final Long deadlockTimeout) {
        super(null, null, null, null, null);

        _databaseConnectionFactory = mysqlDatabaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = deadlockTimeout;
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        if (_isShutdown) { throw new DatabaseException("Connection pool has been shutdown."); }

        { // Check if there is an available cached connection...
            final MysqlDatabaseConnection cachedDatabaseConnection = _mysqlDatabaseConnections.poll();
            if (cachedDatabaseConnection != null) {
                return cachedDatabaseConnection;
            }
        }

        synchronized (_mutex) {
            final Boolean isAtMaxCapacity = (_connectionCount >= _maxConnectionCount);
            if (isAtMaxCapacity) {
                try {
                    _mutex.wait(_deadlockTimeout);

                    final Boolean stillIsAtMaxCapacity = (_connectionCount >= _maxConnectionCount);
                    if (stillIsAtMaxCapacity) {
                        // If the connectionCount is still at max capacity, then this is likely either a deadlock scenario or the database is under high contention.
                        //  In this scenario, we will allow the pool to exceed the maximum connection count.
                        Logger.log("NOTICE: MysqlDatabaseConnectionPool exceeding capacity to mitigate deadlock.");
                        Logger.log(new Exception());
                        return _databaseConnectionFactory.newConnection();
                    }
                }
                catch (final InterruptedException exception) { throw new DatabaseException(exception); }
            }
        }

        { // Check again if there is now an available cached connection made available by close...
            final MysqlDatabaseConnection cachedDatabaseConnection = _mysqlDatabaseConnections.poll();
            if (cachedDatabaseConnection != null) {
                return cachedDatabaseConnection;
            }
        }

        final MysqlDatabaseConnection mysqlDatabaseConnection = _databaseConnectionFactory.newConnection();
        synchronized (_mutex) {
            _connectionCount += 1;
        }

        return new CachedMysqlDatabaseConnection(mysqlDatabaseConnection.getRawConnection());
    }

    @Override
    public void close() {
        _isShutdown = true;

        synchronized (_mutex) {
            CachedMysqlDatabaseConnection cachedConnection;
            while ((cachedConnection = _mysqlDatabaseConnections.poll()) != null) {
                try {
                    cachedConnection.superClose();
                    cachedConnection.close();
                } catch (final Exception exception) { }
            }
        }
    }
}
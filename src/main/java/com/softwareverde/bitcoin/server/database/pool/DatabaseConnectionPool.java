package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseConnectionPool extends DatabaseConnectionFactory implements AutoCloseable {
    public static final Long DEFAULT_DEADLOCK_TIMEOUT = 30000L; // The number of milliseconds the pool will wait before allowing the maximum pool count to be exceeded.

    protected static boolean isConnectionAlive(final MysqlDatabaseConnection databaseConnection) {
        try {
            databaseConnection.query("SELECT 1", null);
            return true;
        }
        catch (final Exception exception) {
            return false;
        }
    }

    protected static void resetConnectionState(final Connection rawConnection) throws SQLException {
        if (! rawConnection.getAutoCommit()) {
            rawConnection.rollback();
            rawConnection.setAutoCommit(true);
        }
    }

    protected final class CachedDatabaseConnection extends MysqlDatabaseConnection {

        public CachedDatabaseConnection(final Connection rawConnection) {
            super(rawConnection);
        }

        public void superClose() throws DatabaseException {
            super.close();
        }

        @Override
        public void close() {
            final Connection rawConnection = this.getRawConnection();
            boolean connectionShouldBeCached;
            try {
                final boolean isClosed = rawConnection.isClosed();
                if (isClosed) {
                    connectionShouldBeCached = false;
                }
                else {
                    // Reset connection...
                    try {
                        DatabaseConnectionPool.resetConnectionState(rawConnection);
                        connectionShouldBeCached = DatabaseConnectionPool.isConnectionAlive(this);
                    }
                    catch (Exception exception) {
                        Logger.log("NOTICE: Unable to resetting database connection.");
                        Logger.log(exception);
                        connectionShouldBeCached = false;
                    }
                }
            }
            catch (final Exception exception) {
                connectionShouldBeCached = false;
            }

            final boolean queueIsFull = (_mysqlDatabaseConnections.size() >= _maxConnectionCount);

            if (connectionShouldBeCached && (! queueIsFull)) {
                _mysqlDatabaseConnections.add(this);
            }
            else {
                _connectionCount.addAndGet(-1);
                try { this.superClose(); } catch (final DatabaseException exception) { }
            }

            synchronized (_mutex) {
                _mutex.notify();
            }
        }
    }

    protected final Object _mutex = new Object();
    protected final Integer _maxConnectionCount;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Long _deadlockTimeout;
    protected AtomicInteger _connectionCount = new AtomicInteger(0);
    protected Boolean _isShutdown = false;
    protected final ConcurrentLinkedQueue<CachedDatabaseConnection> _mysqlDatabaseConnections = new ConcurrentLinkedQueue<CachedDatabaseConnection>();

    public DatabaseConnectionPool(final DatabaseConnectionFactory mysqlDatabaseConnectionFactory, final Integer maxConnectionCount) {
        super(mysqlDatabaseConnectionFactory);

        _databaseConnectionFactory = mysqlDatabaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = DEFAULT_DEADLOCK_TIMEOUT;
    }

    public DatabaseConnectionPool(final DatabaseConnectionFactory mysqlDatabaseConnectionFactory, final Integer maxConnectionCount, final Long deadlockTimeout) {
        super(mysqlDatabaseConnectionFactory);

        _databaseConnectionFactory = mysqlDatabaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = deadlockTimeout;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        if (_isShutdown) { throw new DatabaseException("Connection pool has been shutdown."); }

        while (true) { // Check if there is an available cached connection...
            final MysqlDatabaseConnection cachedDatabaseConnection = _mysqlDatabaseConnections.poll();
            if (cachedDatabaseConnection == null) { break; }

            if (DatabaseConnectionPool.isConnectionAlive(cachedDatabaseConnection)) {
                return new DatabaseConnectionWrapper(cachedDatabaseConnection);
            }
        }

        synchronized (_mutex) {
            final Boolean isAtMaxCapacity = (_connectionCount.get() >= _maxConnectionCount);
            if (isAtMaxCapacity) {
                try {
                    _mutex.wait(_deadlockTimeout);

                    final Boolean stillIsAtMaxCapacity = (_connectionCount.get() >= _maxConnectionCount);
                    if (stillIsAtMaxCapacity) {
                        // If the connectionCount is still at max capacity, then this is likely either a deadlock scenario or the database is under high contention.
                        //  In this scenario, we will allow the pool to exceed the maximum connection count.
                        Logger.log("NOTICE: DatabaseConnectionPool exceeding capacity to mitigate deadlock.");
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
                return new DatabaseConnectionWrapper(cachedDatabaseConnection);
            }
        }

        final DatabaseConnection mysqlDatabaseConnection = _databaseConnectionFactory.newConnection();
        _connectionCount.incrementAndGet();

        return new DatabaseConnectionWrapper(new CachedDatabaseConnection(mysqlDatabaseConnection.getRawConnection()));
    }

    @Override
    public void close() {
        _isShutdown = true;

        synchronized (_mutex) {
            CachedDatabaseConnection cachedConnection;
            while ((cachedConnection = _mysqlDatabaseConnections.poll()) != null) {
                try {
                    cachedConnection.superClose();
                    cachedConnection.close();
                }
                catch (final Exception exception) { }
            }
        }
    }
}
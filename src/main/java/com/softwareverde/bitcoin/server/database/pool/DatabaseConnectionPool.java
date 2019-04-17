package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseConnectionPool extends DatabaseConnectionFactory implements AutoCloseable {
    public static final AtomicInteger instanceCount = new AtomicInteger(0);
    public static final AtomicInteger discardCount = new AtomicInteger(0);
    public static final AtomicInteger issueCount = new AtomicInteger(0);
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
            instanceCount.incrementAndGet();
        }

        public void superClose() throws DatabaseException {
            super.close();
        }

        @Override
        public void close() {
            issueCount.decrementAndGet();
            if (_isAtMaxCapacity()) {
                _discardCachedConnection(this);
                return;
            }

            _returnCachedConnection(this);
        }
    }

    protected final Object _mutex = new Object();
    protected final Integer _maxConnectionCount;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Long _deadlockTimeout;
    public final AtomicInteger _aliveConnectionCount = new AtomicInteger(0);
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
    public final ConcurrentLinkedQueue<CachedDatabaseConnection> _mysqlDatabaseConnections = new ConcurrentLinkedQueue<CachedDatabaseConnection>();

    protected boolean _isAtMaxCapacity() {
        return (_aliveConnectionCount.get() >= _maxConnectionCount);
    }

    protected CachedDatabaseConnection _createNewCachedConnection() throws DatabaseException {
        final CachedDatabaseConnection newConnection;
        try {
            final DatabaseConnection newUnwrappedDatabaseConnection = _databaseConnectionFactory.newConnection();
            final CachedDatabaseConnection newCachedDatabaseConnection = new CachedDatabaseConnection(newUnwrappedDatabaseConnection.getRawConnection());
            newConnection = newCachedDatabaseConnection;
        }
        catch (final Exception exception) {
            if (exception instanceof DatabaseException) { throw exception; }
            throw new DatabaseException(exception);
        }

        _aliveConnectionCount.incrementAndGet();
        return newConnection;
    }

    protected void _returnCachedConnection(final CachedDatabaseConnection cachedDatabaseConnection) {
        try {
            DatabaseConnectionPool.resetConnectionState(cachedDatabaseConnection.getRawConnection());
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return;
        }

        _mysqlDatabaseConnections.add(cachedDatabaseConnection);
        synchronized (_mutex) {
            _mutex.notify();
        }
    }

    protected void _discardCachedConnection(final CachedDatabaseConnection cachedDatabaseConnection) {
        _aliveConnectionCount.decrementAndGet();
        try { cachedDatabaseConnection.superClose(); } catch (final Exception exception) { }
        discardCount.incrementAndGet();
    }

    protected CachedDatabaseConnection _getCachedConnection() {
        while (true) {
            final CachedDatabaseConnection cachedDatabaseConnection = _mysqlDatabaseConnections.poll();
            if (cachedDatabaseConnection == null) { return null; }

            if (! DatabaseConnectionPool.isConnectionAlive(cachedDatabaseConnection)) {
                _discardCachedConnection(cachedDatabaseConnection);
                continue;
            }

            try {
                DatabaseConnectionPool.resetConnectionState(cachedDatabaseConnection.getRawConnection());
            }
            catch (final Exception exception) {
                _discardCachedConnection(cachedDatabaseConnection);
                continue;
            }

            return cachedDatabaseConnection;
        }
    }

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
        int waitDuration = 0;
        while (true) {
            if (_isShutdown.get()) { throw new DatabaseException("Connection pool has been shutdown."); }

            final CachedDatabaseConnection cachedDatabaseConnection = _getCachedConnection();
            if (cachedDatabaseConnection != null) { issueCount.incrementAndGet(); return new DatabaseConnectionWrapper(cachedDatabaseConnection); }

            synchronized (_mutex) {
                if (! _isAtMaxCapacity()) {
                    issueCount.incrementAndGet();
                    return new DatabaseConnectionWrapper(_createNewCachedConnection());
                }

                if (waitDuration >= _deadlockTimeout) {
                    Logger.log("NOTICE: DatabaseConnectionPool exceeding capacity to mitigate deadlock.");
                    Logger.log(new Exception());
                    issueCount.incrementAndGet();
                    return new DatabaseConnectionWrapper(_createNewCachedConnection());
                }

                final MilliTimer waitTimer = new MilliTimer();
                waitTimer.start();
                { // Time the duration actually waited.  Early notifies can happen organically or if another thread (that hasn't waited) is fighting over ownership of the next item...
                    try {
                        _mutex.wait(_deadlockTimeout);
                    }
                    catch (final InterruptedException exception) { throw new DatabaseException(exception); }
                }
                waitTimer.stop();
                waitDuration += waitTimer.getMillisecondsElapsed();
            }
        }
    }

    @Override
    public void close() {
        _isShutdown.set(true);

        synchronized (_mutex) {
            CachedDatabaseConnection cachedConnection;
            while ((cachedConnection = _mysqlDatabaseConnections.poll()) != null) {
                try {
                    _discardCachedConnection(cachedConnection);
                }
                catch (final Exception exception) { }
            }
        }
    }
}
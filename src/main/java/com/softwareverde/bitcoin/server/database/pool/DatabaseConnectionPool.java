package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseConnectionPool extends DatabaseConnectionFactory implements AutoCloseable {
    public static final Long DEFAULT_DEADLOCK_TIMEOUT = 30000L; // The number of milliseconds the pool will wait before allowing the maximum pool count to be exceeded.

    protected static boolean isConnectionAlive(final DatabaseConnection databaseConnection) {
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
        rawConnection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); // "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ"
    }

    protected final Object _mutex = new Object();
    protected final Integer _maxConnectionCount;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Long _deadlockTimeout;
    protected final AtomicInteger _inUseConnectionCount = new AtomicInteger(0);
    protected final AtomicInteger _aliveConnectionCount = new AtomicInteger(0);
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
    public final ConcurrentLinkedQueue<CachedDatabaseConnection> _pooledDatabaseConnections = new ConcurrentLinkedQueue<CachedDatabaseConnection>();

    protected CachedDatabaseConnection _createNewCachedConnection() throws DatabaseException {
        final DatabaseConnection newUnwrappedDatabaseConnection = _databaseConnectionFactory.newConnection();
        final CachedDatabaseConnection cachedDatabaseConnection = CachedDatabaseConnection.assignToPool(this, newUnwrappedDatabaseConnection);

        _aliveConnectionCount.incrementAndGet();
        cachedDatabaseConnection.enable();
        return cachedDatabaseConnection;
    }

    protected void _returnCachedConnection(final CachedDatabaseConnection cachedDatabaseConnection) {
        try {
            DatabaseConnectionPool.resetConnectionState(cachedDatabaseConnection.getRawConnection());
        }
        catch (final Exception exception) {
            Logger.log(exception);
            return;
        }

        cachedDatabaseConnection.disable();
        _pooledDatabaseConnections.add(cachedDatabaseConnection);
        synchronized (_mutex) {
            _mutex.notify();
        }
    }

    protected void _discardCachedConnection(final CachedDatabaseConnection cachedDatabaseConnection) {
        _aliveConnectionCount.decrementAndGet();

        try {
            cachedDatabaseConnection.superClose();
        }
        catch (final Exception exception) { }
    }

    protected CachedDatabaseConnection _getCachedConnection() {
        while (true) {
            final CachedDatabaseConnection cachedDatabaseConnection = _pooledDatabaseConnections.poll();
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

            cachedDatabaseConnection.enable();
            return cachedDatabaseConnection;
        }
    }

    protected void returnToPool(final CachedDatabaseConnection cachedDatabaseConnection) {
        _inUseConnectionCount.decrementAndGet();

        if (_aliveConnectionCount.get() > _maxConnectionCount) {
            _discardCachedConnection(cachedDatabaseConnection);
            return;
        }

        _returnCachedConnection(cachedDatabaseConnection);
    }

    public DatabaseConnectionPool(final DatabaseConnectionFactory mysqlDatabaseConnectionFactory, final Integer maxConnectionCount) {
        super(mysqlDatabaseConnectionFactory);

        _databaseConnectionFactory = mysqlDatabaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = DEFAULT_DEADLOCK_TIMEOUT;
    }

    public DatabaseConnectionPool(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxConnectionCount, final Long deadlockTimeout) {
        super(databaseConnectionFactory);

        _databaseConnectionFactory = databaseConnectionFactory;
        _maxConnectionCount = maxConnectionCount;
        _deadlockTimeout = deadlockTimeout;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        int waitDuration = 0;
        while (true) {
            if (_isShutdown.get()) { throw new DatabaseException("Connection pool has been shutdown."); }

            final CachedDatabaseConnection cachedDatabaseConnection = _getCachedConnection();
            if (cachedDatabaseConnection != null) {
                _inUseConnectionCount.incrementAndGet();
                return CachedDatabaseConnection.assignToPool(this, cachedDatabaseConnection);
            }

            if (_aliveConnectionCount.get() < _maxConnectionCount) { // TODO: Currently does not use a lock, which may allow an extra database connection to be created...
                _inUseConnectionCount.incrementAndGet();
                return _createNewCachedConnection();
            }

            if (waitDuration >= _deadlockTimeout) {
                Logger.log("NOTICE: DatabaseConnectionPool exceeding capacity to mitigate deadlock.");
                _inUseConnectionCount.incrementAndGet();
                return _createNewCachedConnection();
            }

            final MilliTimer waitTimer = new MilliTimer();
            waitTimer.start();
            synchronized (_mutex) { // Time the duration actually waited.  Early notifies can happen organically or if another thread (that hasn't waited) is fighting over ownership of the next item...
                try {
                    _mutex.wait(_deadlockTimeout);
                }
                catch (final InterruptedException exception) { throw new DatabaseException(exception); }
            }
            waitTimer.stop();
            waitDuration += waitTimer.getMillisecondsElapsed();
        }
    }

    @Override
    public void close() {
        _isShutdown.set(true);

        synchronized (_mutex) {
            CachedDatabaseConnection cachedConnection;
            while ((cachedConnection = _pooledDatabaseConnections.poll()) != null) {
                try {
                    _discardCachedConnection(cachedConnection);
                }
                catch (final Exception exception) { }
            }
        }
    }

    /**
     * Returns the number of connections that have not yet been returned to the pool.
     */
    public Integer getInUseConnectionCount() {
        return _inUseConnectionCount.get();
    }

    /**
     * Returns the number of connections that have been created but have not yet been closed.
     *  This number does not necessarily account for pooled connections that have died.
     */
    public Integer getAliveConnectionCount() {
        return _aliveConnectionCount.get();
    }

    /**
     * Returns the desired maximum number of connections this pool will create.
     *  This value can be surpassed if a thread waits for a connection longer than deadlockTimeout (ms).
     */
    public Integer getMaxConnectionCount() {
        return _maxConnectionCount;
    }

    /**
     * Returns the number of connections currently waiting and available within the pool.
     *  This number does not account for pooled connections that have died.
     *  This number should be equal to ::getAliveConnectionsCount + ::getInUseConnectionsCount
     */
    public Integer getCurrentPoolSize() {
        return _pooledDatabaseConnections.size();
    }

    /**
     * Returns the maximum milliseconds a thread is allowed to wait before maxConnectionCount is surpassed.
     */
    public Long getDeadlockTimeout() {
        return _deadlockTimeout;
    }
}

class CachedDatabaseConnection extends DatabaseConnection {
    protected static final AtomicInteger ALIVE_CONNECTIONS_COUNT = new AtomicInteger(0);

    public static CachedDatabaseConnection assignToPool(final DatabaseConnectionPool databaseConnectionPool, final DatabaseConnection coreConnection) {
        if (coreConnection instanceof CachedDatabaseConnection) {
            return (CachedDatabaseConnection) coreConnection;
        }

        return new CachedDatabaseConnection(databaseConnectionPool, coreConnection);
    }

    protected final DatabaseConnectionPool _databaseConnectionPool;

    /**
     * DatabaseConnections are disabled while parked within the pool in order to reduce the chance that a thread is attempting to reuse a connection that has been closed.
     *  This is erroneous state is still possible despite the mitigation.
     *  If the DatabaseConnection is removed from the pool before the original owner executes a query, then it will not know it had been closed.
     */
    protected Boolean _isEnabled = true;

    /**
     * DatabaseConnections are not thread-safe, and in order to prevent multiple threads from obtaining the same DatabaseConnections from the pool the owning ThreadId is validated at query time.
     */
    protected Long _ownerThreadId = null;

    protected void _assertConnectionIsEnabled() throws DatabaseException {
        if (! _isEnabled) {
            throw new DatabaseException("Connection closed.");
        }
    }

    protected void _assertThreadOwner() throws DatabaseException {
        final Thread currentThread = Thread.currentThread();
        final Long currentThreadId = currentThread.getId();
        if (! Util.areEqual(_ownerThreadId, currentThreadId)) {
            if (_ownerThreadId != null) { throw new DatabaseException("Attempted to use DatabaseConnection across multiple threads."); }
            _ownerThreadId = currentThreadId;
        }
    }

    private CachedDatabaseConnection(final DatabaseConnectionPool databaseConnectionPool, final DatabaseConnection coreConnection) {
        super(coreConnection);
        _databaseConnectionPool = databaseConnectionPool;
        ALIVE_CONNECTIONS_COUNT.incrementAndGet();
    }

    public void superClose() throws DatabaseException {
        super.close();
        ALIVE_CONNECTIONS_COUNT.decrementAndGet();
    }

    public void disable() {
        _isEnabled = false;
        _ownerThreadId = null;
    }

    public void enable() {
        _isEnabled = true;
        _ownerThreadId = null;
    }

    @Override
    public void executeDdl(final String queryString) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        super.executeDdl(queryString);
    }

    @Override
    public void executeDdl(final Query query) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        super.executeDdl(query);
    }

    @Override
    public Long executeSql(final String queryString, final String[] parameters) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        return super.executeSql(queryString, parameters);
    }

    @Override
    public Long executeSql(final Query query) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        return super.executeSql(query);
    }

    @Override
    public List<Row> query(final String queryString, final String[] parameters) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        return super.query(queryString, parameters);
    }

    @Override
    public List<Row> query(final Query query) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        return super.query(query);
    }

    @Override
    public Integer getRowsAffectedCount() {
        return ((DatabaseConnection) _core).getRowsAffectedCount();
    }

    @Override
    public void close() throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        _databaseConnectionPool.returnToPool(this);
    }
}
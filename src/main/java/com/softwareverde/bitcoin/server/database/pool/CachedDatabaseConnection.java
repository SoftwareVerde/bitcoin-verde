package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.util.Util;

import java.util.concurrent.atomic.AtomicInteger;

public class CachedDatabaseConnection extends DatabaseConnection {
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
    public java.util.List<Row> query(final String queryString, final String[] parameters) throws DatabaseException {
        _assertConnectionIsEnabled();
        _assertThreadOwner();
        return super.query(queryString, parameters);
    }

    @Override
    public java.util.List<Row> query(final Query query) throws DatabaseException {
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

    public DatabaseConnection unwrap() {
        return (DatabaseConnection) _core;
    }
}
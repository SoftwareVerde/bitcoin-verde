package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;

public interface DatabaseConnectionPool extends DatabaseConnectionFactory, AutoCloseable {

    /**
     * Returns the number of connections that have not yet been returned to the pool.
     * Returns null if the operation is unsupported.
     */
    default Integer getInUseConnectionCount() { return null; }

    /**
     * Returns the number of connections that have been created but have not yet been closed.
     *  This number does not necessarily account for pooled connections that have died.
     *  Returns null if the operation is unsupported.
     */
    default Integer getAliveConnectionCount() { return null; }

    /**
     * Returns the desired maximum number of connections this pool will create.
     *  This value can be surpassed if a thread waits for a connection longer than deadlockTimeout (ms).
     *  Returns null if the operation is unsupported.
     */
    default Integer getMaxConnectionCount() { return null; }

    /**
     * Returns the number of connections currently waiting and available within the pool.
     *  This number does not account for pooled connections that have died.
     *  This number should be equal to ::getAliveConnectionsCount + ::getInUseConnectionsCount
     *  Returns null if the operation is unsupported.
     */
    default Integer getCurrentPoolSize() { return null; }
}
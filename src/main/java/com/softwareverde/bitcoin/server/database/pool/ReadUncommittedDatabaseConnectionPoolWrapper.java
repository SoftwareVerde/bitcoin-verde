package com.softwareverde.bitcoin.server.database.pool;

import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionConfigurer;
import com.softwareverde.bitcoin.server.database.ReadUncommittedDatabaseConnectionFactoryWrapper;

public class ReadUncommittedDatabaseConnectionPoolWrapper extends ReadUncommittedDatabaseConnectionFactoryWrapper implements DatabaseConnectionPool {

    public ReadUncommittedDatabaseConnectionPoolWrapper(final DatabaseConnectionPool core) {
        super(core);
    }

    public ReadUncommittedDatabaseConnectionPoolWrapper(final DatabaseConnectionPool core, final ReadUncommittedDatabaseConnectionConfigurer readUncommittedDatabaseConnectionConfigurer) {
        super(core, readUncommittedDatabaseConnectionConfigurer);
    }

    @Override
    public Integer getInUseConnectionCount() {
        return ((DatabaseConnectionPool) _core).getInUseConnectionCount();
    }

    @Override
    public Integer getAliveConnectionCount() {
        return ((DatabaseConnectionPool) _core).getAliveConnectionCount();
    }

    @Override
    public Integer getMaxConnectionCount() {
        return ((DatabaseConnectionPool) _core).getMaxConnectionCount();
    }

    @Override
    public Integer getCurrentPoolSize() {
        return ((DatabaseConnectionPool) _core).getCurrentPoolSize();
    }
}

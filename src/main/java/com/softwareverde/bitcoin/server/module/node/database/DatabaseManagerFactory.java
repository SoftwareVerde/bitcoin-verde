package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManagerFactory {
    DatabaseManager newDatabaseManager() throws DatabaseException;
    DatabaseManager newDatabaseManager(DatabaseManagerCache databaseManagerCache) throws DatabaseException;
    DatabaseConnectionFactory getDatabaseConnectionFactory();
    DatabaseManagerCache getDatabaseManagerCache();

    /**
     * Allows for construction of a similarly typed DatabaseManagerFactory from an existing instance.
     * @param databaseConnectionPool
     * @param databaseManagerCache
     * @return
     */
    DatabaseManagerFactory newDatabaseManagerFactory(DatabaseConnectionPool databaseConnectionPool, DatabaseManagerCache databaseManagerCache);
}

package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManagerFactory {
    DatabaseManager newDatabaseManager() throws DatabaseException;
    DatabaseManager newDatabaseManager(DatabaseManagerCache databaseManagerCache) throws DatabaseException;
    DatabaseConnectionFactory getDatabaseConnectionFactory();
    DatabaseManagerCache getDatabaseManagerCache();

    /**
     * Allows for construction of a similarly typed DatabaseManagerFactory from an existing instance.
     */
    DatabaseManagerFactory newDatabaseManagerFactory(DatabaseConnectionPool databaseConnectionPool, DatabaseManagerCache databaseManagerCache);
}

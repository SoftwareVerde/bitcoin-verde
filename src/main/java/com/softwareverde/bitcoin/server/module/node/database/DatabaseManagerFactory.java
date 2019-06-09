package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManagerFactory {
    DatabaseManager newDatabaseManager() throws DatabaseException;
    DatabaseConnectionFactory getDatabaseConnectionFactory();
    DatabaseManagerCache getDatabaseManagerCache();
}

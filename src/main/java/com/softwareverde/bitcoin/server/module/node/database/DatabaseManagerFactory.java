package com.softwareverde.bitcoin.server.module.node.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

public interface DatabaseManagerFactory {
    DatabaseManager newDatabaseManager() throws DatabaseException;
    DatabaseConnectionFactory getDatabaseConnectionFactory();

    /**
     * Allows for construction of a similarly typed DatabaseManagerFactory from an existing instance.
     */
    DatabaseManagerFactory newDatabaseManagerFactory(DatabaseConnectionFactory databaseConnectionFactory);

    Integer getMaxQueryBatchSize();
}

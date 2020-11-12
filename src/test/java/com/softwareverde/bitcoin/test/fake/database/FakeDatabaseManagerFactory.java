package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public interface FakeDatabaseManagerFactory extends DatabaseManagerFactory {

    @Override
    default DatabaseManager newDatabaseManager() throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default DatabaseConnectionFactory getDatabaseConnectionFactory() { throw new UnsupportedOperationException(); }

    @Override
    default DatabaseManagerFactory newDatabaseManagerFactory(DatabaseConnectionFactory databaseConnectionFactory) { throw new UnsupportedOperationException(); }

    @Override
    default Integer getMaxQueryBatchSize() { throw new UnsupportedOperationException(); }
}

package com.softwareverde.bitcoin.server.module.node.database.spv;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;

public class SpvDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Integer _maxQueryBatchSize;

    public SpvDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _maxQueryBatchSize = maxQueryBatchSize;
    }

    @Override
    public SpvDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new SpvDatabaseManager(databaseConnection, _maxQueryBatchSize);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public DatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        return new SpvDatabaseManagerFactory(databaseConnectionFactory, _maxQueryBatchSize);
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }
}

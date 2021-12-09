package com.softwareverde.bitcoin.server.module.node.database.spv;

import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.properties.PropertiesStore;
import com.softwareverde.database.DatabaseException;

public class SpvDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final Integer _maxQueryBatchSize;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final PropertiesStore _propertiesStore;

    public SpvDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final Integer maxQueryBatchSize, final PropertiesStore propertiesStore, final CheckpointConfiguration checkpointConfiguration) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _propertiesStore = propertiesStore;
        _maxQueryBatchSize = maxQueryBatchSize;
        _checkpointConfiguration = checkpointConfiguration;
    }

    @Override
    public SpvDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new SpvDatabaseManager(databaseConnection, _maxQueryBatchSize, _propertiesStore, _checkpointConfiguration);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public DatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        return new SpvDatabaseManagerFactory(databaseConnectionFactory, _maxQueryBatchSize, _propertiesStore, _checkpointConfiguration);
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return _maxQueryBatchSize;
    }
}

package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.BlockCache;
import com.softwareverde.database.DatabaseException;

public class NonIndexingFullNodeDatabaseManagerFactory extends FullNodeDatabaseManagerFactory {
    protected final BlockCache _blockCache;
    protected final MasterInflater _masterInflater;

    public NonIndexingFullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final BlockCache blockCache, final MasterInflater masterInflater) {
        super(databaseConnectionFactory);
        _blockCache = blockCache;
        _masterInflater = masterInflater;
    }

    public NonIndexingFullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final BlockCache blockCache, final MasterInflater masterInflater) {
        super(databaseConnectionFactory, databaseManagerCache);
        _blockCache = blockCache;
        _masterInflater = masterInflater;
    }

    @Override
    public NonIndexingFullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new NonIndexingFullNodeDatabaseManager(databaseConnection, _databaseManagerCache, _blockCache, _masterInflater);
    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager(final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new NonIndexingFullNodeDatabaseManager(databaseConnection, databaseManagerCache, _blockCache, _masterInflater);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return super.getDatabaseConnectionFactory();
    }

    @Override
    public DatabaseManagerCache getDatabaseManagerCache() {
        return super.getDatabaseManagerCache();
    }

    @Override
    public FullNodeDatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        return new NonIndexingFullNodeDatabaseManagerFactory(databaseConnectionFactory, databaseManagerCache, _blockCache, _masterInflater);
    }
}

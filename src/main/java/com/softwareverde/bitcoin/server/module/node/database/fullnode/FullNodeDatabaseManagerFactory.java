package com.softwareverde.bitcoin.server.module.node.database.fullnode;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

public class FullNodeDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        this(databaseConnectionFactory, new DisabledDatabaseManagerCache());
    }

    public FullNodeDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = Util.coalesce(databaseManagerCache, new DisabledDatabaseManagerCache());
    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new FullNodeDatabaseManager(databaseConnection, _databaseManagerCache);
    }

    @Override
    public FullNodeDatabaseManager newDatabaseManager(final DatabaseManagerCache databaseManagerCache) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new FullNodeDatabaseManager(databaseConnection, databaseManagerCache);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public DatabaseManagerCache getDatabaseManagerCache() {
        return _databaseManagerCache;
    }

    @Override
    public FullNodeDatabaseManagerFactory newDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        return new FullNodeDatabaseManagerFactory(databaseConnectionFactory, databaseManagerCache);
    }
}

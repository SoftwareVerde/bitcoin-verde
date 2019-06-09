package com.softwareverde.bitcoin.server.module.node.database.core;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.DisabledDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.Util;

public class CoreDatabaseManagerFactory implements DatabaseManagerFactory {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    public CoreDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = new DisabledDatabaseManagerCache();
    }

    public CoreDatabaseManagerFactory(final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = Util.coalesce(databaseManagerCache, new DisabledDatabaseManagerCache());
    }

    @Override
    public CoreDatabaseManager newDatabaseManager() throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection();
        return new CoreDatabaseManager(databaseConnection, _databaseManagerCache);
    }

    @Override
    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    @Override
    public DatabaseManagerCache getDatabaseManagerCache() {
        return _databaseManagerCache;
    }
}

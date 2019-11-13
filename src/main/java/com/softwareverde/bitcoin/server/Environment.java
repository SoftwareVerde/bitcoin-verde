package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;

public class Environment {
    protected final Database _database;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;
    protected final DatabaseConnectionPool _databaseConnectionPool;

    public Environment(final Database database, final DatabaseConnectionPool databaseConnectionPool, final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _database = database;
        _databaseConnectionPool = databaseConnectionPool;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    public Database getDatabase() {
        return _database;
    }

    public MasterDatabaseManagerCache getMasterDatabaseManagerCache() {
        return _masterDatabaseManagerCache;
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return _databaseConnectionPool;
    }
}
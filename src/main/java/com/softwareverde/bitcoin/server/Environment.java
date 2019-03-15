package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;

public class Environment {
    protected final Database _database;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    public Environment(final Database database, final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _database = database;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    public Database getDatabase() {
        return _database;
    }

    public MasterDatabaseManagerCache getMasterDatabaseManagerCache() {
        return _masterDatabaseManagerCache;
    }
}
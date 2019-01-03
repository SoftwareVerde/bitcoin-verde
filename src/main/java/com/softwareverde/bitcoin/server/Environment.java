package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;

public class Environment {
    protected final EmbeddedMysqlDatabase _database;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    public Environment(final EmbeddedMysqlDatabase database, final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _database = database;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    public EmbeddedMysqlDatabase getDatabase() {
        return _database;
    }

    public MasterDatabaseManagerCache getMasterDatabaseManagerCache() {
        return _masterDatabaseManagerCache;
    }
}
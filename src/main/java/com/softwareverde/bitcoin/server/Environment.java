package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.database.mysql.MysqlDatabase;

public class Environment {
    protected final MysqlDatabase _database;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    public Environment(final MysqlDatabase database, final MasterDatabaseManagerCache masterDatabaseManagerCache) {
        _database = database;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;
    }

    public MysqlDatabase getDatabase() {
        return _database;
    }

    public MasterDatabaseManagerCache getMasterDatabaseManagerCache() {
        return _masterDatabaseManagerCache;
    }
}
package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;

public class Environment {
    protected final Database _database;
    protected final DatabaseConnectionPool _databaseConnectionPool;

    public Environment(final Database database, final DatabaseConnectionPool databaseConnectionPool) {
        _database = database;
        _databaseConnectionPool = databaseConnectionPool;
    }

    public Database getDatabase() {
        return _database;
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return _databaseConnectionPool;
    }
}
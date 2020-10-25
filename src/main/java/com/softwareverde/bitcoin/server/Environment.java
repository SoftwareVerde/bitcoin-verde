package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;

public class Environment {
    protected final Database _database;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public Environment(final Database database, final DatabaseConnectionFactory databaseConnectionFactory) {
        _database = database;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public Database getDatabase() {
        return _database;
    }

    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }
}
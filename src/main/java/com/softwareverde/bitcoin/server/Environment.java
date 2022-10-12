package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactoryFactory;

public class Environment {
    protected final Database _database;
    protected final DatabaseConnectionFactoryFactory _databaseConnectionFactoryFactory;
    protected DatabaseConnectionFactory _databaseConnectionFactory;

    public Environment(final Database database, final DatabaseConnectionFactoryFactory databaseConnectionFactoryFactory) {
        _database = database;
        _databaseConnectionFactoryFactory = databaseConnectionFactoryFactory;
    }

    public Database getDatabase() {
        return _database;
    }

    public synchronized DatabaseConnectionFactory getDatabaseConnectionFactory() {
        if (_databaseConnectionFactoryFactory == null) {
            _databaseConnectionFactory = _databaseConnectionFactoryFactory.newDatabaseConnectionFactory();
        }
        return _databaseConnectionFactory;
    }

    public DatabaseConnectionFactory newDatabaseConnectionFactory() {
        return _databaseConnectionFactoryFactory.newDatabaseConnectionFactory();
    }
}
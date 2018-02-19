package com.softwareverde.bitcoin.server;

import ch.vorburger.mariadb4j.DB;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class Environment {
    private final DatabaseConnectionFactory _databaseConnectionFactory;
    private final DB _database;

    public Environment(final DB database, final DatabaseConnectionFactory databaseConnectionFactory) {
        _database = database;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public MysqlDatabaseConnection newDatabaseConnection() throws DatabaseException {
        return _databaseConnectionFactory.newConnection();
    }
}
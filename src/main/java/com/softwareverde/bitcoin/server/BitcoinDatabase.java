package com.softwareverde.bitcoin.server;

import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.Database;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.sql.Connection;

public class BitcoinDatabase implements Database<Connection> {
    protected final DatabaseConnectionFactory _databaseConnectionFactory;

    public BitcoinDatabase(final DatabaseConnectionFactory connectionFactory) {
        _databaseConnectionFactory = connectionFactory;
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        return _databaseConnectionFactory.newConnection();
    }
}

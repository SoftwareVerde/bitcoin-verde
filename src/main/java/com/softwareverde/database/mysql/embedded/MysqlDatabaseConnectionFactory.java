package com.softwareverde.database.mysql.embedded;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.factory.DatabaseConnectionFactory;

import java.sql.Connection;
import java.sql.DriverManager;

public class MysqlDatabaseConnectionFactory implements DatabaseConnectionFactory {
    private final String _connectionString;
    private final String _username;
    private final String _password;

    public MysqlDatabaseConnectionFactory(final String connectionString, final String username, final String password) {
        _connectionString = connectionString;
        _username = username;
        _password = password;
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        try {
            final Connection connection = DriverManager.getConnection(_connectionString, _username, _password);
            return new MysqlDatabaseConnection(connection);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }
}

package com.softwareverde.database.mysql.embedded.factory;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

public class ReadUncommittedDatabaseConnectionFactory extends DatabaseConnectionFactory {
    protected final DatabaseConnectionFactory _mysqlDatabaseConnectionFactory;

    public ReadUncommittedDatabaseConnectionFactory(final DatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        super(mysqlDatabaseConnectionFactory);

        _mysqlDatabaseConnectionFactory = mysqlDatabaseConnectionFactory;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        final DatabaseConnection databaseConnection = _mysqlDatabaseConnectionFactory.newConnection();
        databaseConnection.executeSql("SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED", null);
        return databaseConnection;
    }
}

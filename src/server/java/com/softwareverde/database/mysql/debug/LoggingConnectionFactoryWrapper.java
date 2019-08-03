package com.softwareverde.database.mysql.debug;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

public class LoggingConnectionFactoryWrapper extends DatabaseConnectionFactory {

    public LoggingConnectionFactoryWrapper(final DatabaseConnectionFactory databaseConnectionFactory) {
        super(databaseConnectionFactory);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        final DatabaseConnectionFactory databaseConnectionFactory = (DatabaseConnectionFactory) _core;
        return new LoggingConnectionWrapper(databaseConnectionFactory.newConnection());
    }
}

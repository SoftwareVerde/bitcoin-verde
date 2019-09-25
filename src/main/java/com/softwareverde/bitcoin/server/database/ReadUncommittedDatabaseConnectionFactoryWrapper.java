package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;

public class ReadUncommittedDatabaseConnectionFactoryWrapper implements DatabaseConnectionFactory, ReadUncommittedDatabaseConnectionFactory {
    protected final DatabaseConnectionFactory _core;
    protected final ReadUncommittedDatabaseConnectionConfigurer _readUncommittedDatabaseConnectionConfigurer;

    public ReadUncommittedDatabaseConnectionFactoryWrapper(final DatabaseConnectionFactory core) {
        _core = core;
        _readUncommittedDatabaseConnectionConfigurer = new ReadUncommittedRawConnectionConfigurer();
    }

    public ReadUncommittedDatabaseConnectionFactoryWrapper(final DatabaseConnectionFactory core, final ReadUncommittedDatabaseConnectionConfigurer readUncommittedDatabaseConnectionConfigurer) {
        _core = core;
        _readUncommittedDatabaseConnectionConfigurer = readUncommittedDatabaseConnectionConfigurer;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        final DatabaseConnection databaseConnection = _core.newConnection();
        _readUncommittedDatabaseConnectionConfigurer.setReadUncommittedTransactionIsolationLevel(databaseConnection);
        return databaseConnection;
    }

    @Override
    public void close() throws DatabaseException {
        _core.close();
    }
}

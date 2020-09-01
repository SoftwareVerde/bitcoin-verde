package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

public class FakeDatabaseConnectionFactoryStub implements DatabaseConnectionFactory {
    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws DatabaseException { }
}
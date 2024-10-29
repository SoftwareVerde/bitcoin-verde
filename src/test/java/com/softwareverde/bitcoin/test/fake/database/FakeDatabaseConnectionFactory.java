package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

public interface FakeDatabaseConnectionFactory extends DatabaseConnectionFactory {

    @Override
    default DatabaseConnection newConnection() throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void close() throws DatabaseException { }
}
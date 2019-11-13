package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public interface DatabaseConnectionFactory extends com.softwareverde.database.DatabaseConnectionFactory<Connection>, AutoCloseable {
    @Override
    DatabaseConnection newConnection() throws DatabaseException;

    @Override
    void close() throws DatabaseException;
}

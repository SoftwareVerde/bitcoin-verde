package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public interface Database extends DatabaseConnectionFactory, com.softwareverde.database.Database<Connection>, AutoCloseable {
    @Override
    DatabaseConnection newConnection() throws DatabaseException;

    DatabaseConnectionFactory newConnectionFactory();

    @Override
    void close() throws DatabaseException;
}

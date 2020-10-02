package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public interface Database extends DatabaseConnectionFactory, com.softwareverde.database.Database<Connection>, AutoCloseable {
    @Override
    DatabaseConnection newConnection() throws DatabaseException;

    DatabaseConnection getMaintenanceConnection() throws DatabaseException;

    DatabaseConnectionFactory newConnectionFactory();

    Integer getMaxQueryBatchSize();

    @Override
    void close() throws DatabaseException;
}

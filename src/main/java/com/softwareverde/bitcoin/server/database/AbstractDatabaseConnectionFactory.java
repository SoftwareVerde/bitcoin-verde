package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public abstract class AbstractDatabaseConnectionFactory implements DatabaseConnectionFactory {
    protected final com.softwareverde.database.DatabaseConnectionFactory<Connection> _core;

    public AbstractDatabaseConnectionFactory(final com.softwareverde.database.DatabaseConnectionFactory<Connection> core) {
        _core = core;
    }

    @Override
    public abstract DatabaseConnection newConnection() throws DatabaseException;

    @Override
    public void close() { }
}

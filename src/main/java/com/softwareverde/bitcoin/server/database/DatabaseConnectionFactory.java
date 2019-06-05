package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.io.Closeable;
import java.sql.Connection;

public abstract class DatabaseConnectionFactory implements com.softwareverde.database.DatabaseConnectionFactory<Connection>, Closeable {
    protected final com.softwareverde.database.DatabaseConnectionFactory<Connection> _core;

    public DatabaseConnectionFactory(final com.softwareverde.database.DatabaseConnectionFactory<Connection> core) {
        _core = core;
    }

    @Override
    public abstract DatabaseConnection newConnection() throws DatabaseException;

    @Override
    public void close() { }
}

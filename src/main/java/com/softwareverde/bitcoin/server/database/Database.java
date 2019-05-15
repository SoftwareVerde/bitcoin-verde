package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public abstract class Database implements com.softwareverde.database.Database<Connection> {
    protected final com.softwareverde.database.Database _core;

    protected Database(final com.softwareverde.database.Database core) {
        _core = core;
    }

    @Override
    public abstract DatabaseConnection newConnection() throws DatabaseException;

    public abstract DatabaseConnectionFactory newConnectionFactory();

}

package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public abstract class AbstractDatabase extends AbstractDatabaseConnectionFactory implements com.softwareverde.database.Database<Connection>, AutoCloseable {
    protected AbstractDatabase(final com.softwareverde.database.Database<Connection> core) {
        super(core);
    }

    @Override
    public abstract DatabaseConnection newConnection() throws DatabaseException;

    public abstract DatabaseConnectionFactory newConnectionFactory();

}

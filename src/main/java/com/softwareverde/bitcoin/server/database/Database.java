package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.io.Closeable;
import java.sql.Connection;

public abstract class Database extends DatabaseConnectionFactory implements com.softwareverde.database.Database<Connection>, Closeable {
    protected Database(final com.softwareverde.database.Database<Connection> core) {
        super(core);
    }

    @Override
    public abstract DatabaseConnection newConnection() throws DatabaseException;

    public abstract DatabaseConnectionFactory newConnectionFactory();

}

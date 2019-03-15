package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;

import java.sql.Connection;
import java.util.List;

public abstract class DatabaseConnection implements com.softwareverde.database.DatabaseConnection<Connection> {
    protected final com.softwareverde.database.DatabaseConnection<Connection> _core;

    public DatabaseConnection(final com.softwareverde.database.DatabaseConnection<Connection> core) {
        _core = core;
    }

    public abstract Integer getRowsAffectedCount();

    @Override
    public void executeDdl(final String queryString) throws DatabaseException {
        _core.executeDdl(queryString);
    }

    @Override
    public void executeDdl(final Query query) throws DatabaseException {
        _core.executeDdl(query);
    }

    @Override
    public Long executeSql(final String queryString, final String[] parameters) throws DatabaseException {
        return _core.executeSql(queryString, parameters);
    }

    @Override
    public Long executeSql(final Query query) throws DatabaseException {
        return _core.executeSql(query);
    }

    @Override
    public List<Row> query(final String queryString, final String[] parameters) throws DatabaseException {
        return _core.query(queryString, parameters);
    }

    @Override
    public List<Row> query(final Query query) throws DatabaseException {
        return _core.query(query);
    }

    @Override
    public void close() throws DatabaseException {
        _core.close();
    }

    @Override
    public Connection getRawConnection() {
        return _core.getRawConnection();
    }
}

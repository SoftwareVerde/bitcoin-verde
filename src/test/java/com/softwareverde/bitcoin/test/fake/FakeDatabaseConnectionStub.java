package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.sql.Connection;

public class FakeDatabaseConnectionStub implements DatabaseConnection {
    @Override
    public Integer getRowsAffectedCount() { throw new UnsupportedOperationException(); }

    @Override
    public void executeDdl(final String query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public void executeDdl(final com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public Long executeSql(final String query, final String[] parameters) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public Long executeSql(final com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public java.util.List<Row> query(final String query, final String[] parameters) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public java.util.List<Row> query(final com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    public void close() throws DatabaseException { }

    @Override
    public Connection getRawConnection() { throw new UnsupportedOperationException(); }
}
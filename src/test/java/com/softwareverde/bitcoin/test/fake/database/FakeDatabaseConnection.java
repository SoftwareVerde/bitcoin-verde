package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;

import java.sql.Connection;

public interface FakeDatabaseConnection extends DatabaseConnection {
    @Override
    default Integer getRowsAffectedCount() { throw new UnsupportedOperationException(); }

    @Override
    default void executeDdl(String query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void executeDdl(com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Long executeSql(String query, String[] parameters) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Long executeSql(com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default java.util.List<Row> query(String query, String[] parameters) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default java.util.List<Row> query(com.softwareverde.database.query.Query query) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void close() throws DatabaseException { }

    @Override
    default Connection getRawConnection() { throw new UnsupportedOperationException(); }
}
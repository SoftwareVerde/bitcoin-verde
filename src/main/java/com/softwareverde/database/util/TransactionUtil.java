package com.softwareverde.database.util;

import com.softwareverde.database.DatabaseConnection;
import com.softwareverde.database.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;

public class TransactionUtil {
    public static void startTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            databaseConnection.getRawConnection().setAutoCommit(false);
        }
        catch (final SQLException exception) {
            throw new DatabaseException(exception);
        }
    }

    public static void commitTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            databaseConnection.getRawConnection().commit();
        }
        catch (final SQLException exception) {
            throw new DatabaseException(exception);
        }
    }

    public static void rollbackTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            databaseConnection.getRawConnection().rollback();
        }
        catch (final SQLException exception) {
            throw new DatabaseException(exception);
        }
    }
}

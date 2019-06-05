package com.softwareverde.database.util;

import com.softwareverde.database.DatabaseConnection;
import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public class TransactionUtil {
    public static void startTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            final Connection rawConnection = databaseConnection.getRawConnection();
            rawConnection.setAutoCommit(false);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    public static void commitTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            final Connection rawConnection = databaseConnection.getRawConnection();
            rawConnection.commit();
            rawConnection.setAutoCommit(true);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }

    public static void rollbackTransaction(final DatabaseConnection<Connection> databaseConnection) throws DatabaseException {
        try {
            final Connection rawConnection = databaseConnection.getRawConnection();
            rawConnection.rollback();
            rawConnection.setAutoCommit(true);
        }
        catch (final Exception exception) {
            throw new DatabaseException(exception);
        }
    }
}

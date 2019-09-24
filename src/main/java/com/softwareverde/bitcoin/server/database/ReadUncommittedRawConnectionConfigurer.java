package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

import java.sql.Connection;
import java.sql.SQLException;

public class ReadUncommittedRawConnectionConfigurer implements ReadUncommittedDatabaseConnectionConfigurer {
    @Override
    public void setReadUncommittedTransactionIsolationLevel(final DatabaseConnection databaseConnection) throws DatabaseException {
        // databaseConnection.executeSql("SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED", null);

        try {
            final Connection rawConnection = databaseConnection.getRawConnection();
            rawConnection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        }
        catch (final SQLException exception) {
            throw new DatabaseException(exception);
        }
    }
}

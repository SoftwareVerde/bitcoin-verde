package com.softwareverde.database.mysql.embedded.factory;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;

import java.sql.Connection;

public class ReadUncommittedDatabaseConnectionFactory extends DatabaseConnectionFactory {
    protected final DatabaseConnectionFactory _mysqlDatabaseConnectionFactory;

    public ReadUncommittedDatabaseConnectionFactory(final DatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        super(mysqlDatabaseConnectionFactory);

        _mysqlDatabaseConnectionFactory = mysqlDatabaseConnectionFactory;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        DatabaseConnection databaseConnection = null;

        try {
            databaseConnection = _mysqlDatabaseConnectionFactory.newConnection();
            final Connection rawConnection = databaseConnection.getRawConnection();
            rawConnection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED); // "SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED"
            return databaseConnection;
        }
        catch (final Exception exception) {
            try {
                if (databaseConnection != null) {
                    databaseConnection.close();
                }
            }
            catch (final Exception closeException) { }

            if (exception instanceof DatabaseException) {
                throw (DatabaseException) exception;
            }
            else {
                throw new DatabaseException(exception);
            }
        }
    }
}

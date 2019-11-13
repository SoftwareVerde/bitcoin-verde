package com.softwareverde.bitcoin.server.database;

import com.softwareverde.database.DatabaseException;

public interface ReadUncommittedDatabaseConnectionConfigurer {
    void setReadUncommittedTransactionIsolationLevel(final DatabaseConnection databaseConnection) throws DatabaseException;
}

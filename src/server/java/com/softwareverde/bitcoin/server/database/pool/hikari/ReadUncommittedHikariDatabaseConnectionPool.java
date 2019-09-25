package com.softwareverde.bitcoin.server.database.pool.hikari;

import com.softwareverde.database.mysql.connection.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.properties.DatabaseProperties;

public class ReadUncommittedHikariDatabaseConnectionPool extends HikariDatabaseConnectionPool implements ReadUncommittedDatabaseConnectionFactory {

    @Override
    protected void _initHikariDataSource(final DatabaseProperties databaseProperties) {
        super._initHikariDataSource(databaseProperties);

        _dataSource.setTransactionIsolation("TRANSACTION_READ_UNCOMMITTED");
    }

    public ReadUncommittedHikariDatabaseConnectionPool(final DatabaseProperties databaseProperties) {
        super(databaseProperties);
    }
}

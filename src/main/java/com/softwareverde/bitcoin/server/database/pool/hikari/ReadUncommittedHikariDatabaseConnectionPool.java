package com.softwareverde.bitcoin.server.database.pool.hikari;

import com.softwareverde.database.properties.DatabaseProperties;

public class ReadUncommittedHikariDatabaseConnectionPool extends HikariDatabaseConnectionPool {

    @Override
    protected void _initHikariDataSource(final DatabaseProperties databaseProperties) {
        super._initHikariDataSource(databaseProperties);
        _dataSource.setT     
    
    }

    public ReadUncommittedHikariDatabaseConnectionPool(final DatabaseProperties databaseProperties) {
        super(databaseProperties);
    }
}

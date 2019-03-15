package com.softwareverde.bitcoin.server.database.impl;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class DatabaseConnectionFactoryImpl extends DatabaseConnectionFactory {

    public DatabaseConnectionFactoryImpl(final MysqlDatabaseConnectionFactory core) {
        super(core);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new DatabaseConnectionImpl(((MysqlDatabaseConnectionFactory) _core).newConnection());
    }
}

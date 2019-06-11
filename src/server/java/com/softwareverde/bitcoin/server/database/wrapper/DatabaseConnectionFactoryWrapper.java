package com.softwareverde.bitcoin.server.database.wrapper;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class DatabaseConnectionFactoryWrapper extends DatabaseConnectionFactory {

    public DatabaseConnectionFactoryWrapper(final MysqlDatabaseConnectionFactory core) {
        super(core);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new DatabaseConnectionWrapper(((MysqlDatabaseConnectionFactory) _core).newConnection());
    }

    @Override
    public void close() { }
}

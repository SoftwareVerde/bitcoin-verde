package com.softwareverde.bitcoin.server.database.wrapper;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class MysqlDatabaseConnectionFactoryWrapper implements DatabaseConnectionFactory {

    protected final MysqlDatabaseConnectionFactory _core;

    public MysqlDatabaseConnectionFactoryWrapper(final MysqlDatabaseConnectionFactory core) {
        _core = core;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new MysqlDatabaseConnectionWrapper(_core.newConnection());
    }

    @Override
    public void close() { }
}

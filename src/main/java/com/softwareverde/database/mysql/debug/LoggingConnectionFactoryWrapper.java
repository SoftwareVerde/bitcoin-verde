package com.softwareverde.database.mysql.debug;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;

public class LoggingConnectionFactoryWrapper extends MysqlDatabaseConnectionFactory {
    protected final MysqlDatabaseConnectionFactory _mysqlDatabaseConnectionFactory;

    public LoggingConnectionFactoryWrapper(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        super("", "", "");

        _mysqlDatabaseConnectionFactory = mysqlDatabaseConnectionFactory;
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        return new LoggingConnectionWrapper(_mysqlDatabaseConnectionFactory.newConnection());
    }
}

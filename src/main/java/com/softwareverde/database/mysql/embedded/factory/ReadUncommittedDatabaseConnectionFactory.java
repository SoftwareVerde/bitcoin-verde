package com.softwareverde.database.mysql.embedded.factory;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.MysqlDatabaseConnectionFactory;

public class ReadUncommittedDatabaseConnectionFactory implements DatabaseConnectionFactory {
    protected final MysqlDatabaseConnectionFactory _mysqlDatabaseConnectionFactory;

    public ReadUncommittedDatabaseConnectionFactory(final MysqlDatabaseConnectionFactory mysqlDatabaseConnectionFactory) {
        _mysqlDatabaseConnectionFactory = mysqlDatabaseConnectionFactory;
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        final MysqlDatabaseConnection mysqlDatabaseConnection = _mysqlDatabaseConnectionFactory.newConnection();
        mysqlDatabaseConnection.executeSql("SET SESSION TRANSACTION ISOLATION LEVEL READ UNCOMMITTED", null);
        return mysqlDatabaseConnection;
    }
}

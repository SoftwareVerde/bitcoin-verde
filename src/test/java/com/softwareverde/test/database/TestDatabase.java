package com.softwareverde.test.database;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.hikari.HikariDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.database.properties.MutableDatabaseProperties;

public class TestDatabase implements Database {
    protected MysqlTestDatabase _core;

    public TestDatabase(final MysqlTestDatabase core) {
        _core = core;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new MysqlDatabaseConnectionWrapper(_core.newConnection());
    }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new MysqlDatabaseConnectionFactoryWrapper(_core.newConnectionFactory());
    }

    public void reset() throws DatabaseException {
        _core.reset();
    }

    public DB getDatabaseInstance() {
        return _core.getDatabaseInstance();
    }

    public DatabaseCredentials getCredentials() {
        return _core.getCredentials();
    }

    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return new MysqlDatabaseConnectionFactoryWrapper(_core.getDatabaseConnectionFactory());
    }

    public MysqlDatabaseConnectionFactory getMysqlDatabaseConnectionFactory() {
        return _core.getDatabaseConnectionFactory();
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return _core.getDatabaseConnectionPool();
    }

    @Override
    public void close() { }
}

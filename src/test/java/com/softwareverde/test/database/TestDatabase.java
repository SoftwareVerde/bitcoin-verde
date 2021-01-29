package com.softwareverde.test.database;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.util.Util;

public class TestDatabase implements Database {
    protected final MysqlTestDatabase _core;
    protected DatabaseConnectionPool _databaseConnectionPool;
    protected MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    public TestDatabase(final MysqlTestDatabase core) {
        _core = core;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new MysqlDatabaseConnectionWrapper(_core.newConnection());
    }

    @Override
    public DatabaseConnection getMaintenanceConnection() throws DatabaseException {
        return this.newConnection();
    }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new MysqlDatabaseConnectionFactoryWrapper(Util.coalesce(_databaseConnectionFactory, _core.newConnectionFactory()));
    }

    @Override
    public Integer getMaxQueryBatchSize() {
        return 1024;
    }

    public void reset() throws DatabaseException {
        _core.reset();
    }

    public DatabaseCredentials getCredentials() {
        return _core.getCredentials();
    }

    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return new MysqlDatabaseConnectionFactoryWrapper(Util.coalesce(_databaseConnectionFactory, _core.getDatabaseConnectionFactory()));
    }

    public MysqlDatabaseConnectionFactory getMysqlDatabaseConnectionFactory() {
        return Util.coalesce(_databaseConnectionFactory, _core.getDatabaseConnectionFactory());
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return Util.coalesce(_databaseConnectionPool, _core.getDatabaseConnectionPool());
    }

    public MysqlTestDatabase getCore() {
        return _core;
    }

    public void setDatabaseConnectionPool(final DatabaseConnectionPool databaseConnectionPool) {
        _databaseConnectionPool = databaseConnectionPool;
    }

    public void setDatabaseConnectionFactory(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    @Override
    public void close() { }
}

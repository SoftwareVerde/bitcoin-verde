package com.softwareverde.test.database;

import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.properties.Credentials;

public class TestDatabase extends Database {
    public TestDatabase(final MysqlTestDatabase core) {
        super(core);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new DatabaseConnectionWrapper(((MysqlTestDatabase) _core).newConnection());
    }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new DatabaseConnectionFactoryWrapper(((MysqlTestDatabase) _core).newConnectionFactory());
    }

    public void reset() throws DatabaseException {
        ((MysqlTestDatabase) _core).reset();
    }

    public DB getDatabaseInstance() {
        return ((MysqlTestDatabase) _core).getDatabaseInstance();
    }

    public Credentials getCredentials() {
        return ((MysqlTestDatabase) _core).getCredentials();
    }

    public DatabaseConnectionFactory getDatabaseConnectionFactory() {
        return new DatabaseConnectionFactoryWrapper(((MysqlTestDatabase) _core).getDatabaseConnectionFactory());
    }

    public MysqlDatabaseConnectionFactory getMysqlDatabaseConnectionFactory() {
        return ((MysqlTestDatabase) _core).getDatabaseConnectionFactory();
    }

    @Override
    public void close() { }
}

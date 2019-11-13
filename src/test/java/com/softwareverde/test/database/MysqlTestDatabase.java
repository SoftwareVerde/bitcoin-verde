package com.softwareverde.test.database;

import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.hikari.HikariDatabaseConnectionPool;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfiguration;
import com.softwareverde.database.mysql.embedded.vorburger.DatabaseConfigurationBuilder;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.properties.DatabaseProperties;
import com.softwareverde.database.properties.MutableDatabaseProperties;

public class MysqlTestDatabase extends MysqlDatabase {
    protected final DB _databaseInstance;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseCredentials _credentials;

    protected final String _host;
    protected final Integer _port;
    protected final String _rootUsername = "root";
    protected final String _rootPassword = "";
    protected final String _databaseSchema = "bitcoin_test";

    protected DatabaseProperties _getDatabaseProperties() {
        final MutableDatabaseProperties databaseProperties = new MutableDatabaseProperties();
        databaseProperties.setHostname(_host);
        databaseProperties.setPort(_port);
        databaseProperties.setSchema(_schema);
        databaseProperties.setPassword(_password);
        databaseProperties.setUsername(_username);
        return databaseProperties;
    }

    public MysqlTestDatabase() {
        super(null, null, null, null);
        final DBConfiguration dbConfiguration;
        {
            final DatabaseConfigurationBuilder configBuilder = DatabaseConfigurationBuilder.newBuilder();
            dbConfiguration = configBuilder.build();

            _host = "localhost";
            _port = configBuilder.getPort();

            _databaseConnectionFactory = new MysqlDatabaseConnectionFactory(_host, _port, _databaseSchema, _rootUsername, _rootPassword);
        }

        {
            try {
                _databaseInstance = DB.newEmbeddedDB(dbConfiguration);
                _databaseInstance.start();
                _databaseInstance.createDB(_databaseSchema);
            }
            catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        }

        _credentials = new DatabaseCredentials(_rootUsername, _rootPassword);
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        try {
            return _databaseConnectionFactory.newConnection();
        }
        catch (final Exception exception) {
            throw new DatabaseException("Unable to connect to database.", exception);
        }
    }

    public void reset() throws DatabaseException {
        _databaseInstance.run("DROP DATABASE "+ _databaseSchema, _rootUsername, _rootPassword);
        _databaseInstance.createDB(_databaseSchema);
    }

    public DB getDatabaseInstance() {
        return _databaseInstance;
    }

    public DatabaseCredentials getCredentials() {
        return _credentials;
    }

    public MysqlDatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return new HikariDatabaseConnectionPool(_getDatabaseProperties());
    }

    public DatabaseProperties getDatabaseProperties() {
        return _getDatabaseProperties();
    }
}

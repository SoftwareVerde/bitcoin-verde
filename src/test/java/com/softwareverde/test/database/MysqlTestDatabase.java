package com.softwareverde.test.database;

import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfiguration;
import com.softwareverde.database.mysql.embedded.vorburger.DatabaseConfigurationBuilder;
import com.softwareverde.database.properties.DatabaseCredentials;

public class MysqlTestDatabase extends MysqlDatabase {
    protected final DB _databaseInstance;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseCredentials _credentials;

    protected final String _rootUsername = "root";
    protected final String _rootPassword = "";
    protected final String _databaseSchema = "bitcoin_test";

    public MysqlTestDatabase() {
        super(null, null, null, null);
        final DBConfiguration dbConfiguration;
        {
            final DatabaseConfigurationBuilder configBuilder = DatabaseConfigurationBuilder.newBuilder();
            dbConfiguration = configBuilder.build();

            final String host = "localhost";
            final Integer port = configBuilder.getPort();

            _databaseConnectionFactory = new MysqlDatabaseConnectionFactory(host, port, _databaseSchema, _rootUsername, _rootPassword);
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
}

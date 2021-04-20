package com.softwareverde.test.database;

import com.softwareverde.bitcoin.server.database.pool.ApacheCommonsDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.pool.SimpleDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionFactoryWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.MysqlDatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.EmbeddedDatabaseProperties;
import com.softwareverde.database.mysql.embedded.properties.MutableEmbeddedDatabaseProperties;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.query.Query;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

public class MysqlTestDatabase extends MysqlDatabase {
    protected final EmbeddedMysqlDatabase _databaseInstance;
    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseCredentials _credentials;
    protected final EmbeddedDatabaseProperties _databaseProperties;

    protected final String _host;
    protected final Integer _port;
    protected final String _rootUsername = "root";
    protected final String _rootPassword = "password";
    protected final String _databaseSchema = "bitcoin_test";

    protected synchronized static Integer getAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    public MysqlTestDatabase() {
        super(null, null, null, null);

        try {
            _host = "localhost";
            _port = MysqlTestDatabase.getAvailablePort();

            _databaseConnectionFactory = new MysqlDatabaseConnectionFactory(_host, _port, _databaseSchema, _rootUsername, _rootPassword);

            try {
                final Path databaseInstallDirectory = Files.createTempDirectory("test_db_bin_");
                final File installationDirectory = databaseInstallDirectory.toFile();

                final Path databaseDataDirectory = Files.createTempDirectory("test_db_data_");
                final File dataDirectory = databaseDataDirectory.toFile();

                final MysqlDatabaseInitializer databaseInitializer = new MysqlDatabaseInitializer();
                final MutableEmbeddedDatabaseProperties databaseProperties = new MutableEmbeddedDatabaseProperties();
                databaseProperties.setHostname(_host);
                databaseProperties.setPort(_port);
                databaseProperties.setDataDirectory(dataDirectory);
                databaseProperties.setInstallationDirectory(installationDirectory);
                databaseProperties.setRootPassword(_rootPassword);
                databaseProperties.setSchema(_databaseSchema);
                databaseProperties.setUsername(_rootUsername);
                databaseProperties.setPassword(_rootPassword);

                _databaseProperties = databaseProperties;
                _databaseInstance = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer);
                _databaseInstance.install();
                _databaseInstance.start();
            }
            catch (final Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
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
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            databaseConnection.executeDdl(new Query("DROP DATABASE " + _databaseSchema));
            databaseConnection.executeDdl(new Query("CREATE DATABASE " + _databaseSchema));
        }
    }

    public DatabaseCredentials getCredentials() {
        return _credentials;
    }

    public MysqlDatabaseConnectionFactory getDatabaseConnectionFactory() {
        return _databaseConnectionFactory;
    }

    public DatabaseConnectionPool getDatabaseConnectionPool() {
        return new ApacheCommonsDatabaseConnectionPool(_databaseProperties, 32);
    }
}

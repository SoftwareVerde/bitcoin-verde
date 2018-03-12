package com.softwareverde.database.mysql.embedded;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.database.Database;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.vorburger.DB;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfiguration;
import com.softwareverde.database.mysql.embedded.vorburger.DBConfigurationBuilder;
import com.softwareverde.util.HashUtil;

import java.sql.Connection;

// TODO: Remove dependency on com.softwareverde.bitcoin.server.Configuration

public class EmbeddedMysqlDatabase implements Database<Connection> {
    protected DB _databaseInstance;
    protected DatabaseConnectionFactory _databaseConnectionFactory;

    protected void _loadDatabase(final Configuration.DatabaseProperties databaseProperties) throws DatabaseException {
        final String rootHost = "127.0.0.1";

        final Credentials defaultRootCredentials;
        final Credentials rootCredentials;
        final Credentials credentials;
        final Credentials maintenanceCredentials;
        {
            final String databaseSchema = databaseProperties.getSchema();
            final String rootUsername = "root";
            final String defaultRootPassword = "";
            final String newRootPassword = databaseProperties.getRootPassword();
            final String maintenanceUsername = (databaseSchema + "_maintenance");
            final String maintenancePassword = HashUtil.sha256(newRootPassword);

            defaultRootCredentials = new Credentials(rootUsername, defaultRootPassword, databaseSchema);
            rootCredentials = new Credentials(rootUsername, newRootPassword, databaseSchema);
            credentials = new Credentials(databaseProperties.getUsername(), databaseProperties.getPassword(), databaseSchema);
            maintenanceCredentials = new Credentials(maintenanceUsername, maintenancePassword, databaseSchema);
        }

        final DatabaseConnectionFactory defaultCredentialsDatabaseConnectionFactory;
        final DatabaseConnectionFactory databaseConnectionFactory;
        final DBConfiguration dbConfiguration;
        {
            final DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(databaseProperties.getPort());
            configBuilder.setDataDir(databaseProperties.getDataDirectory());
            configBuilder.setSecurityDisabled(false);
            dbConfiguration = configBuilder.build();

            final String connectionString = configBuilder.getURL(credentials.schema);
            databaseConnectionFactory = new DatabaseConnectionFactory(connectionString, credentials.username, credentials.password);

            final String defaultCredentialsConnectionString = configBuilder.getURL(""); // NOTE: Should empty string (cannot be null).
            defaultCredentialsDatabaseConnectionFactory = new DatabaseConnectionFactory(defaultCredentialsConnectionString, defaultRootCredentials.username, defaultRootCredentials.password);
        }

        final DB databaseInstance;
        {
            DB db = null;
            try {
                db = DB.newEmbeddedDB(dbConfiguration);
                db.start();
            }
            catch (final Exception exception) {
                throw new DatabaseException(exception);
            }
            databaseInstance = db;
        }

        { // Check for default username/password...
            Boolean databaseIsConfigured = false;
            DatabaseException databaseConfigurationFailureReason = null;
            try (final MysqlDatabaseConnection databaseConnection = defaultCredentialsDatabaseConnectionFactory.newConnection()) {
                try {
                    databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
                    databaseConnection.executeDdl("CREATE DATABASE IF NOT EXISTS `"+ credentials.schema +"`");

                    { // Restrict root to localhost and set root password...
                        databaseConnection.executeSql(
                            new Query("DELETE FROM mysql.user WHERE user != ? OR host != ?")
                                .setParameter(defaultRootCredentials.username)
                                .setParameter(rootHost)
                        );
                        databaseConnection.executeSql(
                            new Query("ALTER USER ?@? IDENTIFIED BY ?")
                                .setParameter(rootCredentials.username)
                                .setParameter(rootHost)
                                .setParameter(rootCredentials.password)
                        );
                    }

                    { // Create maintenance user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(maintenanceCredentials.username)
                                .setParameter(maintenanceCredentials.password)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT ALL PRIVILEGES ON `" + maintenanceCredentials.schema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(maintenanceCredentials.username)
                                .setParameter(maintenanceCredentials.password)
                        );
                    }

                    { // Create regular user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(credentials.username)
                                .setParameter(credentials.password)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT SELECT, INSERT, DELETE, UPDATE, EXECUTE ON `" + credentials.schema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(credentials.username)
                                .setParameter(credentials.password)
                        );
                    }

                    databaseConnection.executeSql("FLUSH PRIVILEGES", null);
                    databaseIsConfigured = true;
                }
                catch (final Exception exception) {
                    databaseIsConfigured = false;
                    databaseConfigurationFailureReason = new DatabaseException(exception);
                }
            }
            catch (final DatabaseException exception) {
                databaseIsConfigured = true;
            }

            if (! databaseIsConfigured) {
                throw databaseConfigurationFailureReason;
            }
        }

        final DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        databaseInitializer.initializeDatabase(databaseInstance, databaseConnectionFactory, maintenanceCredentials);

        _databaseInstance = databaseInstance;
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    public EmbeddedMysqlDatabase(final Configuration.DatabaseProperties databaseProperties) throws DatabaseException {
        _loadDatabase(databaseProperties);
    }

    @Override
    public MysqlDatabaseConnection newConnection() throws DatabaseException {
        return _databaseConnectionFactory.newConnection();
    }
}

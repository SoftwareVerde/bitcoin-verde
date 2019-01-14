package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.module.DatabaseConfigurer;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.properties.Credentials;
import com.softwareverde.io.Logger;

public class Database {
    public static MysqlDatabase newInstance(final Configuration configuration, final Runnable onShutdownCallback) {
        final Configuration.DatabaseProperties databaseProperties = configuration.getDatabaseProperties();
        final Configuration.ServerProperties serverProperties = configuration.getServerProperties();

        final DatabaseInitializer databaseInitializer = new DatabaseInitializer("/queries/init.sql", Constants.DATABASE_VERSION, new DatabaseInitializer.DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
        });

        try {
            if (databaseProperties.useEmbeddedDatabase()) {
                // Initialize the embedded database...
                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, serverProperties, databaseProperties);

                Logger.log("[Initializing Database]");
                final EmbeddedMysqlDatabase embeddedMysqlDatabase = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);

                if (onShutdownCallback != null) {
                    embeddedMysqlDatabase.setPreShutdownHook(onShutdownCallback);
                }

                return embeddedMysqlDatabase;
            }
            else {
                // Connect to the remote database...
                final Credentials credentials = databaseProperties.getCredentials();
                final Credentials rootCredentials = databaseProperties.getRootCredentials();
                final Credentials maintenanceCredentials = databaseInitializer.getMaintenanceCredentials(databaseProperties);

                final MysqlDatabaseConnectionFactory rootDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties.getHostname(), databaseProperties.getPort(), "", rootCredentials.username, rootCredentials.password);
                final MysqlDatabaseConnectionFactory maintenanceDatabaseConnectionFactory = new MysqlDatabaseConnectionFactory(databaseProperties, maintenanceCredentials);
                // final MysqlDatabaseConnectionFactory databaseConnectionFactory = new MysqlDatabaseConnectionFactory(connectionUrl, credentials.username, credentials.password);

                try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceDatabaseConnectionFactory.newConnection()) {
                    final Integer databaseVersion = databaseInitializer.getDatabaseVersionNumber(maintenanceDatabaseConnection);
                    if (databaseVersion < 0) {
                        try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                            databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
                        }
                    }
                }
                catch (final DatabaseException exception) {
                    try (final MysqlDatabaseConnection rootDatabaseConnection = rootDatabaseConnectionFactory.newConnection()) {
                        databaseInitializer.initializeSchema(rootDatabaseConnection, databaseProperties);
                    }
                }

                try (final MysqlDatabaseConnection maintenanceDatabaseConnection = maintenanceDatabaseConnectionFactory.newConnection()) {
                    databaseInitializer.initializeDatabase(maintenanceDatabaseConnection);
                }

                return new MysqlDatabase(databaseProperties, credentials);
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }

    protected Database() { }
}

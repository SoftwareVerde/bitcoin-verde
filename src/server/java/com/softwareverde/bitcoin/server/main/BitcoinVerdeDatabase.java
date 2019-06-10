package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.wrapper.DatabaseConnectionWrapper;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.DatabaseInitializer;
import com.softwareverde.database.mysql.MysqlDatabase;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.properties.Credentials;
import com.softwareverde.io.Logger;

public class BitcoinVerdeDatabase extends Database {
    public static class InitFile {
        public final String sqlInitFile;
        public final Integer databaseVersion;

        public InitFile(final String sqlInitFile, final Integer databaseVersion) {
            this.sqlInitFile = sqlInitFile;
            this.databaseVersion = databaseVersion;
        }
    }

    public static final InitFile BITCOIN = new InitFile("/queries/bitcoin_init.sql", 1);
    public static final InitFile STRATUM = new InitFile("/queries/stratum_init.sql", 1);

    public static Database newInstance(final InitFile initFile, final DatabaseProperties databaseProperties) {
        return newInstance(initFile, databaseProperties, new Runnable() {
            @Override
            public void run() {
                // Nothing.
            }
        });
    }

    public static Database newInstance(final InitFile sqlInitFile, final DatabaseProperties databaseProperties, final Runnable onShutdownCallback) {
        final DatabaseInitializer databaseInitializer = new DatabaseInitializer(sqlInitFile.sqlInitFile, sqlInitFile.databaseVersion, new DatabaseInitializer.DatabaseUpgradeHandler() {
            @Override
            public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
        });

        try {
            if (databaseProperties.useEmbeddedDatabase()) {
                // Initialize the embedded database...
                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                final Integer maxDatabaseThreadCount = 100000; // Maximum supported by MySql...
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties);

                Logger.log("[Initializing Database]");
                final EmbeddedMysqlDatabase embeddedMysqlDatabase = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);

                if (onShutdownCallback != null) {
                    embeddedMysqlDatabase.setPreShutdownHook(onShutdownCallback);
                }

                return new BitcoinVerdeDatabase(embeddedMysqlDatabase);
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

                return new BitcoinVerdeDatabase(new MysqlDatabase(databaseProperties, credentials));
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return null;
    }

    protected BitcoinVerdeDatabase(final MysqlDatabase core) {
        super(core);
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new DatabaseConnectionWrapper(((MysqlDatabase) _core).newConnection());
    }

    @Override
    public void close() { }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new DatabaseConnectionFactoryWrapper(((MysqlDatabase) _core).newConnectionFactory());
    }
}

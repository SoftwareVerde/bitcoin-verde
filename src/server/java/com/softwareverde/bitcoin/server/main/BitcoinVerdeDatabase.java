package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.configuration.DatabaseProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionFactoryWrapper;
import com.softwareverde.bitcoin.server.database.wrapper.MysqlDatabaseConnectionWrapper;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.DatabaseInitializer;
import com.softwareverde.database.mysql.*;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.properties.DatabaseCredentials;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.io.StringReader;
import java.sql.Connection;

public class BitcoinVerdeDatabase implements Database {
    public static class InitFile {
        public final String sqlInitFile;
        public final Integer databaseVersion;

        public InitFile(final String sqlInitFile, final Integer databaseVersion) {
            this.sqlInitFile = sqlInitFile;
            this.databaseVersion = databaseVersion;
        }
    }

    public static final InitFile BITCOIN = new InitFile("/queries/bitcoin_init.sql", BitcoinConstants.DATABASE_VERSION);
    public static final InitFile STRATUM = new InitFile("/queries/stratum_init.sql", BitcoinConstants.DATABASE_VERSION);

    public static Database newInstance(final InitFile initFile, final DatabaseProperties databaseProperties) {
        return BitcoinVerdeDatabase.newInstance(initFile, databaseProperties, new Runnable() {
            @Override
            public void run() {
                // Nothing.
            }
        });
    }

    public static final DatabaseInitializer.DatabaseUpgradeHandler<Connection> DATABASE_UPGRADE_HANDLER = new DatabaseInitializer.DatabaseUpgradeHandler<Connection>() {
        @Override
        public Boolean onUpgrade(final com.softwareverde.database.DatabaseConnection<Connection> maintenanceDatabaseConnection, final Integer currentVersion, final Integer requiredVersion) {
            if ( (currentVersion == 1) && (requiredVersion >= 2) ) {
                try {
                    final String upgradeScript = IoUtil.getResource("/queries/migration/v1_to_v2.sql");
                    if (Util.coalesce(upgradeScript).isEmpty()) { return false; }

                    TransactionUtil.startTransaction(maintenanceDatabaseConnection);
                    final SqlScriptRunner scriptRunner = new SqlScriptRunner(maintenanceDatabaseConnection.getRawConnection(), false, true);
                    scriptRunner.runScript(new StringReader(upgradeScript));
                    TransactionUtil.commitTransaction(maintenanceDatabaseConnection);

                    return true;
                }
                catch (final Exception exception) {
                    Logger.error("Unable to upgrade database.", exception);
                    return false;
                }
            }

            return false;
        }
    };

    public static Database newInstance(final InitFile sqlInitFile, final DatabaseProperties databaseProperties, final Runnable onShutdownCallback) {
        final DatabaseInitializer<Connection> databaseInitializer = new MysqlDatabaseInitializer(sqlInitFile.sqlInitFile, sqlInitFile.databaseVersion, BitcoinVerdeDatabase.DATABASE_UPGRADE_HANDLER);

        try {
            if (databaseProperties.useEmbeddedDatabase()) {
                // Initialize the embedded database...
                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                final Integer maxDatabaseThreadCount = 100000; // Maximum supported by MySql...
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, maxDatabaseThreadCount, databaseProperties);

                Logger.info("[Initializing Database]");
                final EmbeddedMysqlDatabase embeddedMysqlDatabase = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);

                if (onShutdownCallback != null) {
                    embeddedMysqlDatabase.setShutdownCallback(onShutdownCallback);
                }

                return new BitcoinVerdeDatabase(embeddedMysqlDatabase);
            }
            else {
                // Connect to the remote database...
                final DatabaseCredentials credentials = databaseProperties.getCredentials();
                final DatabaseCredentials rootCredentials = databaseProperties.getRootCredentials();
                final DatabaseCredentials maintenanceCredentials = databaseInitializer.getMaintenanceCredentials(databaseProperties);

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
            Logger.error(exception);
        }

        return null;
    }

    protected final MysqlDatabase _core;

    protected BitcoinVerdeDatabase(final MysqlDatabase core) {
        _core = core;
    }

    @Override
    public DatabaseConnection newConnection() throws DatabaseException {
        return new MysqlDatabaseConnectionWrapper(_core.newConnection());
    }

    @Override
    public void close() { }

    @Override
    public DatabaseConnectionFactory newConnectionFactory() {
        return new MysqlDatabaseConnectionFactoryWrapper(_core.newConnectionFactory());
    }
}

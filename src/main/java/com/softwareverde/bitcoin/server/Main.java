package com.softwareverde.bitcoin.server;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.Container;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static class Database {
        public final DB databaseInstance;
        public final DatabaseConnectionFactory databaseConnectionFactory;

        public Database(final DB database, final DatabaseConnectionFactory databaseConnectionFactory) {
            this.databaseInstance = database;
            this.databaseConnectionFactory = databaseConnectionFactory;
        }
    }

    protected final Configuration _configuration;
    protected final Environment _environment;

    protected void _exitFailure() {
        System.exit(1);
    }

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected void _printUsage() {
        _printError("Usage: java -jar " + System.getProperty("java.class.path") + " <configuration-file>");
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            _exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected Database _loadDatabase(final Configuration.DatabaseProperties databaseProperties) {
        final String rootUsername = "root";
        final String rootPassword = "";
        final String rootHost = "127.0.0.1";
        final String newRootPassword = databaseProperties.getRootPassword();

        final String databaseSchema     = databaseProperties.getSchema();
        final String databaseUsername   = databaseProperties.getUsername();
        final String databasePassword   = databaseProperties.getPassword();

        final String maintenanceUsername = (databaseUsername + "_maintenance");
        final String maintenancePassword = newRootPassword;

        final DatabaseConnectionFactory defaultCredentialsDatabaseConnectionFactory;
        final DatabaseConnectionFactory databaseConnectionFactory;
        final DBConfiguration dbConfiguration;
        {
            final DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(databaseProperties.getPort());
            configBuilder.setDataDir(databaseProperties.getDataDirectory());
            configBuilder.setSecurityDisabled(false);
            dbConfiguration = configBuilder.build();

            final String connectionString = configBuilder.getURL(databaseSchema);
            databaseConnectionFactory = new DatabaseConnectionFactory(connectionString, databaseUsername, databasePassword);

            final String defaultCredentialsConnectionString = configBuilder.getURL(""); // NOTE: Should not be null.
            defaultCredentialsDatabaseConnectionFactory = new DatabaseConnectionFactory(defaultCredentialsConnectionString, rootUsername, rootPassword);
        }

        final DB databaseInstance;
        {
            DB db = null;
            try {
                System.out.println("[Starting Database]");
                db = DB.newEmbeddedDB(dbConfiguration);
                db.start();
            }
            catch (final Exception exception) {
                exception.printStackTrace();
                _exitFailure();
            }
            databaseInstance = db;
        }

        { // Check for default username/password...
            try (final MysqlDatabaseConnection databaseConnection = defaultCredentialsDatabaseConnectionFactory.newConnection()) {
                try {
                    System.out.println("[Configuring Database]");
                    databaseConnection.executeDdl("DROP DATABASE IF EXISTS `test`");
                    databaseConnection.executeDdl("CREATE DATABASE IF NOT EXISTS `"+ databaseSchema +"`");

                    { // Restrict root to localhost and set root password...
                        databaseConnection.executeSql(
                            new Query("DELETE FROM mysql.user WHERE user != ? OR host != ?")
                                .setParameter(rootUsername)
                                .setParameter(rootHost)
                        );
                        databaseConnection.executeSql(
                            new Query("ALTER USER ?@? IDENTIFIED BY ?")
                                .setParameter(rootUsername)
                                .setParameter(rootHost)
                                .setParameter(newRootPassword)
                        );
                    }

                    { // Create maintenance user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(maintenanceUsername)
                                .setParameter(maintenancePassword)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT ALL PRIVILEGES ON `" + databaseSchema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(maintenanceUsername)
                                .setParameter(maintenancePassword)
                        );
                    }

                    { // Create regular user and permissions...
                        databaseConnection.executeSql(
                            new Query("CREATE USER ? IDENTIFIED BY ?")
                                .setParameter(databaseUsername)
                                .setParameter(databasePassword)
                        );
                        databaseConnection.executeSql(
                            new Query("GRANT SELECT, INSERT, DELETE, UPDATE, EXECUTE ON `" + databaseSchema + "`.* TO ? IDENTIFIED BY ?")
                                .setParameter(databaseUsername)
                                .setParameter(databasePassword)
                        );
                    }

                    databaseConnection.executeSql("FLUSH PRIVILEGES", null);
                }
                catch (final DatabaseException exception) {
                    exception.printStackTrace();
                    _exitFailure();
                }
            }
            catch (final DatabaseException exception) {
                // Failing to connect with default credentials indicates the server has already been configured...
            }
        }

        { // Initialize Database...
            final Integer databaseVersionNumber;
            {
                Integer versionNumber = 0;
                try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    final List<Row> rows = databaseConnection.query("SELECT version FROM metadata ORDER BY id DESC LIMIT 1", null);
                    if (! rows.isEmpty()) {
                        final Row row = rows.get(0);
                        versionNumber = row.getInteger("version");
                    }
                }
                catch (final Exception exception) { }
                databaseVersionNumber = versionNumber;
            }

            try {
                if (databaseVersionNumber < Constants.DATABASE_VERSION) {
                    System.out.println("[Upgrading Database]");
                    databaseInstance.source("queries/init.sql", maintenanceUsername, maintenancePassword, databaseSchema);
                }
            }
            catch (final Exception exception) {
                exception.printStackTrace();
                _exitFailure();
            }
        }

        return new Database(databaseInstance, databaseConnectionFactory);
    }

    protected void _downloadAllBlocks(final Node node) {
        final Container<Hash> lastBlockHash = new Container<Hash>(Block.GENESIS_BLOCK_HEADER_HASH);
        final Container<Node.QueryCallback> getBlocksHashesAfterCallback = new Container<Node.QueryCallback>();

        final List<Hash> availableBlockHashes = new ArrayList<Hash>();

        final Node.DownloadBlockCallback downloadBlockCallback = new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                System.out.println("DOWNLOADED BLOCK: "+ BitcoinUtil.toHexString(block.calculateSha256Hash()));

                if (! lastBlockHash.value.equals(block.getPreviousBlockHash())) { return; } // Ignore blocks sent out of order...

                try (final MysqlDatabaseConnection databaseConnection = _environment.newDatabaseConnection()) {
                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                    blockDatabaseManager.storeBlockHeader(block);
                }
                catch (final DatabaseException e) {
                    e.printStackTrace();
                }

                lastBlockHash.value = block.calculateSha256Hash();

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), this);
                }
                else {
                    node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
                }
            }
        };

        getBlocksHashesAfterCallback.value = new Node.QueryCallback() {
            @Override
            public void onResult(final List<Hash> blockHashes) {
                availableBlockHashes.addAll(blockHashes);

                if (! availableBlockHashes.isEmpty()) {
                    node.requestBlock(availableBlockHashes.remove(0), downloadBlockCallback);
                }
            }
        };

        node.getBlockHashesAfter(lastBlockHash.value, getBlocksHashesAfterCallback.value);
    }

    public Main(final String[] commandLineArguments) {
        if (commandLineArguments.length != 1) {
            _printUsage();
            _exitFailure();
        }

        final String configurationFilename = commandLineArguments[0];

        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();
        final Database database = _loadDatabase(databaseProperties);
        System.out.println("[Database Online]");

        _environment = new Environment(database.databaseInstance, database.databaseConnectionFactory);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
    }

    public void loop() {
        System.out.println("[Server Online]");

        final String host = "btc.softwareverde.com";
        final Integer port = 8333;

        final Node node = new Node(host, port);

        node.requestBlock(Block.GENESIS_BLOCK_HEADER_HASH, new Node.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                try (final MysqlDatabaseConnection databaseConnection = _environment.newDatabaseConnection()) {
                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
                    blockDatabaseManager.storeBlockHeader(block);
                }
                catch (final DatabaseException e) { }
            }
        });

        _downloadAllBlocks(node);

        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }
        }
    }

    public static void main(final String[] commandLineArguments) {
        final Main application = new Main(commandLineArguments);
        application.loop();
    }
}
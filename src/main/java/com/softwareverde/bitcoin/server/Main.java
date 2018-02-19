package com.softwareverde.bitcoin.server;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.node.Node;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.io.File;
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
        final String databaseName   = "bitcoin";
        final String username       = "root";
        final String password       = "";

        final DatabaseConnectionFactory databaseConnectionFactory;
        final DBConfiguration dbConfiguration;
        {
            final DBConfigurationBuilder configBuilder = DBConfigurationBuilder.newBuilder();
            configBuilder.setPort(databaseProperties.getPort());
            configBuilder.setDataDir(databaseProperties.getDataDirectory());
            configBuilder.setLibDir(databaseProperties.getTmpDirectory());
            dbConfiguration = configBuilder.build();

            final String connectionString = configBuilder.getURL(databaseName);
            databaseConnectionFactory = new DatabaseConnectionFactory(connectionString, username, password);
        }

        final DB databaseInstance;
        {
            DB db = null;
            try {
                System.out.println("[Starting Database]");
                db = DB.newEmbeddedDB(dbConfiguration);
                db.start();
                db.createDB(databaseName); // NOTE: Executes as "CREATE IF NOT EXISTS", and is necessary for the connection string.
            }
            catch (final Exception exception) {
                exception.printStackTrace();
                _exitFailure();
            }
            databaseInstance = db;
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
                    databaseInstance.run("DROP DATABASE IF EXISTS test;");
                    databaseInstance.source("queries/init.sql", null, null, "bitcoin");
                }
            }
            catch (final Exception exception) {
                exception.printStackTrace();
                _exitFailure();
            }
        }

        return new Database(databaseInstance, databaseConnectionFactory);
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
                System.out.println("BLOCK: "+ BitcoinUtil.toHexString(block.calculateSha256Hash()));
            }
        });

        while (true) {
            try { Thread.sleep(500); } catch (final Exception e) { }
        }
    }

    public static void main(final String[] commandLineArguments) {
        final Main application = new Main(commandLineArguments);
        application.loop();
    }
}
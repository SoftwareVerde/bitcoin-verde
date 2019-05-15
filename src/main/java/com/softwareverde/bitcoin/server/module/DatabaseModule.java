package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BitcoinVerdeDatabase;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.io.Logger;

import java.io.File;

public class DatabaseModule {

    protected final Configuration _configuration;
    protected final Environment _environment;

    protected void _printError(final String errorMessage) {
        System.err.println(errorMessage);
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            _printError("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    public DatabaseModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.BitcoinProperties bitcoinProperties = _configuration.getBitcoinProperties();
        final Configuration.DatabaseProperties databaseProperties = bitcoinProperties.getDatabaseProperties();

        final Database database = BitcoinVerdeDatabase.newInstance(BitcoinVerdeDatabase.BITCOIN, databaseProperties);
        if (database == null) {
            Logger.log("Error initializing database.");
            BitcoinUtil.exitFailure();
        }
        Logger.log("[Database Online]");

        _environment = new Environment(database, null);
    }

    public void loop() {
        while (true) {
            try { Thread.sleep(5000); } catch (final Exception e) { }
        }
    }

    public static void execute(final String configurationFileName) {
        final DatabaseModule databaseModule = new DatabaseModule(configurationFileName);
        databaseModule.loop();
    }
}

package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;

import java.io.File;

/**
 * Recalculates the the ChainWork for all blocks.
 *  NOTE: This module is not considerate of forks. Therefore, if there are multiple forks, not all Blocks' ChainWork may be recalculated.
 */
public class ChainWorkModule {
    public static void execute(final String configurationFileName) {
        final ChainWorkModule nodeModule = new ChainWorkModule(configurationFileName);
        nodeModule.loop();
    }

    protected final Configuration _configuration;
    protected final Environment _environment;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected ChainWorkModule(final String configurationFilename) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        final EmbeddedMysqlDatabase database;
        {
            EmbeddedMysqlDatabase databaseInstance = null;
            try {
                final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/init.sql", Constants.DATABASE_VERSION, new DatabaseInitializer.DatabaseUpgradeHandler() {
                    @Override
                    public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
                });

                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                {
                    commandLineArguments.setInnoDbBufferPoolByteCount(serverProperties.getMaxMemoryByteCount());
                    commandLineArguments.setInnoDbBufferPoolInstanceCount(1);
                    commandLineArguments.setInnoDbLogFileByteCount(64 * ByteUtil.Unit.MEGABYTES);
                    commandLineArguments.setInnoDbLogBufferByteCount(8 * ByteUtil.Unit.MEGABYTES);
                    commandLineArguments.setQueryCacheByteCount(0L);
                    commandLineArguments.setMaxAllowedPacketByteCount(32 * ByteUtil.Unit.MEGABYTES);
                }

                databaseInstance = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
            database = databaseInstance;

            if (database != null) {
                Logger.log("[Database Online]");
            }
            else {
                BitcoinUtil.exitFailure();
            }
        }

        _environment = new Environment(database);
    }

    public void loop() {
        long i = 0L;
        MutableChainWork chainWork = new MutableChainWork();
        try (final MysqlDatabaseConnection databaseConnection = _environment.getDatabase().newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            while (true) {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM blocks WHERE block_height = ?")
                        .setParameter(i)
                );
                if (rows.isEmpty()) { break; }

                final Row row = rows.get(0);
                final BlockId blockId = BlockId.wrap(row.getLong("id"));

                final BlockHeader blockHeader = blockDatabaseManager.getBlockHeader(blockId);
                final BlockWork proofOfWork = blockHeader.getDifficulty().calculateWork();
                chainWork.add(proofOfWork);

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET chain_work = ? WHERE block_height = ?")
                        .setParameter(chainWork)
                        .setParameter(i)
                );

                i += 1L;

                if (i % 2016 == 0) {
                    System.out.println("Block Height: " + (i - 1) + " Chain Work: " + chainWork);
                }
            }
        }
        catch (final Exception exception) {
            Logger.log(exception);
            BitcoinUtil.exitFailure();
        }

        System.exit(0);
    }
}

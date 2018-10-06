package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.StringUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.Util;

import java.io.File;

public class ChainValidationModule {
    public static void execute(final String configurationFileName, final String startingBlockHash) {
        final ChainValidationModule chainValidationModule = new ChainValidationModule(configurationFileName, startingBlockHash);
        chainValidationModule.run();
    }

    protected final Configuration _configuration;
    protected final Environment _environment;
    protected final Sha256Hash _startingBlockHash;

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected ChainValidationModule(final String configurationFilename, final String startingBlockHash) {
        _configuration = _loadConfigurationFile(configurationFilename);

        _startingBlockHash = Util.coalesce(Sha256Hash.fromHexString(startingBlockHash), BlockHeader.GENESIS_BLOCK_HASH);

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

        _environment = new Environment(database, new MasterDatabaseManagerCache());
    }

    public void run() {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();
        final DatabaseManagerCache databaseManagerCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();

        Sha256Hash nextBlockHash = _startingBlockHash;
        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseManagerCache);
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, databaseManagerCache);

            final ReadUncommittedDatabaseConnectionFactory databaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(database.getDatabaseConnectionFactory());
            final NetworkTime networkTime = new MutableNetworkTime();
            final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();

            final BlockValidator blockValidator = new BlockValidator(databaseConnectionFactory, databaseManagerCache, networkTime, medianBlockTime);
            blockValidator.setMaxThreadCount(serverProperties.getMaxThreadCount());
            blockValidator.setShouldLogValidBlocks(false);

            final BlockChainSegmentId headBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final Long maxBlockHeight = blockHeaderDatabaseManager.getBlockHeightForBlockId(headBlockId);

            Long validatedTransactionCount = 0L;
            final Long startTime = System.currentTimeMillis();
            while (true) {
                final Sha256Hash blockHash = nextBlockHash;

                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderIdFromHash(nextBlockHash);
                final Long blockHeight = blockHeaderDatabaseManager.getBlockHeightForBlockId(blockId);

                final Integer percentComplete = (int) ((blockHeight * 100) / maxBlockHeight.floatValue());

                if (blockHeight % (maxBlockHeight / 100) == 0) {
                    final Integer secondsElapsed;
                    final Float blocksPerSecond;
                    final Float transactionsPerSecond;
                    {
                        final Long now = System.currentTimeMillis();
                        final Integer seconds = (int) ((now - startTime) / 1000L);
                        final Long blockCount = blockHeight;
                        blocksPerSecond = (blockCount / (seconds.floatValue() + 1));
                        secondsElapsed = seconds;
                        transactionsPerSecond = (validatedTransactionCount / (seconds.floatValue() + 1));
                    }

                    Logger.log(percentComplete + "% complete. " + blockHeight + " of " + maxBlockHeight + " - " + blockHash + " ( "+ String.format("%.2f", blocksPerSecond) +" bps) (" + String.format("%.2f", transactionsPerSecond) + " tps) ("+ StringUtil.formatNumberString(secondsElapsed) +" seconds)");
                }

                validatedTransactionCount += transactionDatabaseManager.getTransactionCount(blockId);
                final Boolean blockIsValid = blockValidator.validateBlock(blockId, null);

                if (! blockIsValid) {
                    Logger.error("Invalid block found: " + blockHash);
                }

                nextBlockHash = null;
                final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockChainSegmentId, blockId);
                if (nextBlockId != null) {
                    final Boolean nextBlockHasTransactions = blockDatabaseManager.hasTransactions(nextBlockId);
                    if (nextBlockHasTransactions) {
                        nextBlockHash = blockHeaderDatabaseManager.getBlockHashFromId(nextBlockId);
                    }
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
            Logger.log("Last validated block: " + nextBlockHash);
            BitcoinUtil.exitFailure();
        }

        System.exit(0);
    }
}

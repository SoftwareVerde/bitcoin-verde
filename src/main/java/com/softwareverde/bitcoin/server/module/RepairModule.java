package com.softwareverde.bitcoin.server.module;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionInventoryMessageHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.concurrent.pool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.util.ByteUtil;

import java.io.File;

public class RepairModule {
    public static void execute(final String configurationFileName, final String[] blockHashes) {
        final RepairModule repairModule = new RepairModule(configurationFileName, blockHashes);
        repairModule.run();
    }

    protected final Configuration _configuration;
    protected final Environment _environment;
    protected final List<Sha256Hash> _blockHashes;

    protected final NodeInitializer _nodeInitializer;
    protected final MainThreadPool _threadPool = new MainThreadPool(256, 10000L);

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected RepairModule(final String configurationFilename, final String[] blockHashes) {
        _configuration = _loadConfigurationFile(configurationFilename);

        final ImmutableListBuilder<Sha256Hash> blockHashesBuilder = new ImmutableListBuilder<Sha256Hash>(blockHashes.length);
        for (final String blockHashString : blockHashes) {
            final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
            if (blockHash != null) {
                blockHashesBuilder.add(blockHash);
            }
        }
        _blockHashes = blockHashesBuilder.build();

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final Configuration.DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        final EmbeddedMysqlDatabase database;
        {
            EmbeddedMysqlDatabase databaseInstance = null;
            try {
                final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/init.sql", Constants.DATABASE_VERSION, new DatabaseInitializer.DatabaseUpgradeHandler() {
                    @Override
                    public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
                });

                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, serverProperties, databaseProperties);

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

        final Long maxUtxoCacheByteCount = serverProperties.getMaxUtxoCacheByteCount();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCache(maxUtxoCacheByteCount);
        _environment = new Environment(database, masterDatabaseManagerCache);

        final DatabaseManagerCache databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        { // Initialize NodeInitializer...
            final MysqlDatabaseConnectionFactory databaseConnectionFactory = database.getDatabaseConnectionFactory();
            final SynchronizationStatus synchronizationStatusHandler = new SynchronizationStatusHandler(databaseConnectionFactory, databaseManagerCache);
            final BitcoinNode.QueryBlocksCallback queryBlocksHandler = QueryBlocksHandler.IGNORE_REQUESTS_HANDLER;
            final BitcoinNode.QueryBlockHeadersCallback queryBlockHeadersHandler = QueryBlockHeadersHandler.IGNORES_REQUESTS_HANDLER;
            final BitcoinNode.RequestDataCallback requestDataHandler = RequestDataHandler.IGNORE_REQUESTS_HANDLER;
            final TransactionInventoryMessageHandlerFactory transactionInventoryMessageHandlerFactory = TransactionInventoryMessageHandlerFactory.IGNORE_NEW_TRANSACTIONS_HANDLER_FACTORY;
            final BitcoinNode.BlockInventoryMessageCallback inventoryMessageHandler = BlockInventoryMessageHandler.IGNORE_INVENTORY_HANDLER;

            final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
                @Override
                public ThreadPool newThreadPool() {
                    return new ThreadPoolThrottle(serverProperties.getMaxMessagesPerSecond(), _threadPool);
                }
            };

            _nodeInitializer = new NodeInitializer(synchronizationStatusHandler, inventoryMessageHandler, transactionInventoryMessageHandlerFactory, queryBlocksHandler, queryBlockHeadersHandler, requestDataHandler, threadPoolFactory);
        }
    }

    public void run() {
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();

        final DatabaseManagerCache databaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        final List<BitcoinNode> bitcoinNodes;
        {
            final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
            final ImmutableListBuilder<BitcoinNode> bitcoinNodeListBuilder = new ImmutableListBuilder<BitcoinNode>();
            for (final Configuration.SeedNodeProperties seedNodeProperties : serverProperties.getSeedNodeProperties()) {
                final String host = seedNodeProperties.getAddress();
                final Integer port = seedNodeProperties.getPort();

                final BitcoinNode bitcoinNode = _nodeInitializer.initializeNode(host, port);
                bitcoinNodeListBuilder.add(bitcoinNode);
            }
            bitcoinNodes = bitcoinNodeListBuilder.build();

            if (bitcoinNodes.isEmpty()) {
                Logger.log("ERROR: No trusted nodes set.");
                BitcoinUtil.exitFailure();
            }
        }

        final BitcoinNode bitcoinNode = bitcoinNodes.get(0);

        for (final Sha256Hash blockHash : _blockHashes) {
            final Object synchronizer = new Object();
            bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final Block block) {
                    try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, databaseManagerCache);
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, databaseManagerCache);

                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        if (blockId == null) {
                            Logger.log("Block not found: " + blockHash);
                            return;
                        }

                        TransactionUtil.startTransaction(databaseConnection);
                        blockDatabaseManager.repairBlock(block);
                        TransactionUtil.commitTransaction(databaseConnection);

                        Logger.log("Repaired block: " + blockHash);

                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                        Logger.log("Error repairing block: " + blockHash);
                    }

                    synchronizer.notifyAll();
                }
            });

            try { synchronizer.wait(); }
            catch (final InterruptedException exception) { break; }
        }

        _environment.getMasterDatabaseManagerCache().close();
        System.exit(0);
    }
}

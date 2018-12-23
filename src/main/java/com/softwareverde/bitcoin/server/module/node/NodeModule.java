package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.Configuration;
import com.softwareverde.bitcoin.server.Constants;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.database.*;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.NativeUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.pool.MysqlDatabaseConnectionPool;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.CacheWarmer;
import com.softwareverde.bitcoin.server.module.DatabaseConfigurer;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.MemoryPoolEnquirerHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionInventoryMessageHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.*;
import com.softwareverde.bitcoin.server.module.node.rpc.*;
import com.softwareverde.bitcoin.server.module.node.sync.*;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.concurrent.pool.ThreadPoolThrottle;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.embedded.DatabaseCommandLineArguments;
import com.softwareverde.database.mysql.embedded.DatabaseInitializer;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.database.mysql.embedded.properties.DatabaseProperties;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Util;

import java.io.File;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;

public class NodeModule {
    public static void execute(final String configurationFileName) {
        final NodeModule nodeModule = new NodeModule(configurationFileName);
        nodeModule.loop();
    }

    protected final Boolean _shouldWarmUpCache = true;

    protected final Configuration _configuration;
    protected final Environment _environment;

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final BinarySocketServer _socketServer;
    protected final JsonSocketServer _jsonRpcSocketServer;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final TransactionDownloader _transactionDownloader;
    protected final TransactionProcessor _transactionProcessor;
    protected final BlockchainBuilder _blockchainBuilder;
    protected final AddressProcessor _addressProcessor;

    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final String _transactionBloomFilterFilename;

    protected final MutableList<MysqlDatabaseConnectionPool> _openDatabaseConnectionPools = new MutableList<MysqlDatabaseConnectionPool>();

    protected final MainThreadPool _mainThreadPool;
    protected final MainThreadPool _rpcThreadPool;

    protected Boolean _isShuttingDown = false;
    protected final Object _shutdownPin = new Object();

    protected MysqlDatabaseConnectionPool _createDatabaseConnectionPool(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final Integer maxCount) {
        final MysqlDatabaseConnectionPool databaseConnectionPool = new MysqlDatabaseConnectionPool(databaseConnectionFactory, maxCount);
        _openDatabaseConnectionPools.add(databaseConnectionPool);
        return databaseConnectionPool;
    }

    protected Configuration _loadConfigurationFile(final String configurationFilename) {
        final File configurationFile =  new File(configurationFilename);
        if (! configurationFile.isFile()) {
            Logger.error("Invalid configuration file.");
            BitcoinUtil.exitFailure();
        }

        return new Configuration(configurationFile);
    }

    protected void _warmUpCache() {
        Logger.log("[Warming Cache]");
        final CacheWarmer cacheWarmer = new CacheWarmer();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();
        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        final MysqlDatabaseConnectionFactory databaseConnectionFactory = database.getDatabaseConnectionFactory();
        cacheWarmer.warmUpCache(masterDatabaseManagerCache, databaseConnectionFactory);
    }

    protected void _connectToAdditionalNodes() {
        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final Configuration.SeedNodeProperties[] seedNodes = serverProperties.getSeedNodeProperties();
        final HashSet<String> seedNodeHosts = new HashSet<String>(seedNodes.length);
        for (final Configuration.SeedNodeProperties seedNodeProperties : seedNodes) {
            final String host = seedNodeProperties.getAddress();
            final Integer port = seedNodeProperties.getPort();

            seedNodeHosts.add(host + port);
        }

        final EmbeddedMysqlDatabase database = _environment.getDatabase();
        final Integer maxPeerCount = serverProperties.getMaxPeerCount();
        if (maxPeerCount < 1) { return; }

        final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableList<NodeFeatures.Feature>();
        requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
        requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);

        try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final List<BitcoinNodeIpAddress> bitcoinNodeIpAddresses = nodeDatabaseManager.findNodes(requiredFeatures, maxPeerCount); // NOTE: Request the full maxPeerCount (not `maxPeerCount - seedNodes.length`) because some selected nodes will likely be seed nodes...

            int connectedNodeCount = seedNodeHosts.size();
            for (final BitcoinNodeIpAddress bitcoinNodeIpAddress : bitcoinNodeIpAddresses) {
                if (connectedNodeCount >= maxPeerCount) { break; }

                final Ip ip = bitcoinNodeIpAddress.getIp();
                if (ip == null) { continue; }

                final String host = ip.toString();
                final Integer port = bitcoinNodeIpAddress.getPort();

                if (seedNodeHosts.contains(host + port)) { continue; } // Exclude SeedNodes...

                final BitcoinNode node = _nodeInitializer.initializeNode(host, port);
                _bitcoinNodeManager.addNode(node);
                connectedNodeCount += 1;

                Logger.log("Connecting to former peer: " + host + ":" + port);

                try { Thread.sleep(500L); } catch (final Exception exception) { }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    protected void _shutdown() {
        synchronized (_shutdownPin) {
            final Boolean wasAlreadyShuttingDown = _isShuttingDown;
            _isShuttingDown = true;
            if (wasAlreadyShuttingDown) {
                try { _shutdownPin.wait(); } catch (final Exception exception) { }
                return;
            }
        }

        Logger.log("[Stopping Addresses Processor]");
        _addressProcessor.stop();

        Logger.log("[Stopping Transaction Processor]");
        _transactionProcessor.stop();

        Logger.log("[Stopping Transaction Downloader]");
        _transactionDownloader.stop();

        Logger.log("[Stopping Block Processor]");
        _blockchainBuilder.stop();

        Logger.log("[Stopping Block Downloader]");
        _blockDownloader.stop();

        Logger.log("[Stopping Header Downloader]");
        _blockHeaderDownloader.stop();

        Logger.log("[Stopping Node Manager]");
        _bitcoinNodeManager.shutdown();
        _bitcoinNodeManager.stopNodeMaintenanceThread();

        Logger.log("[Stopping Socket Server]");
        _socketServer.stop();

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        if (serverProperties.shouldUseTransactionBloomFilter()) {
            Logger.log("[Saving Tx Bloom Filter]");
            TransactionDatabaseManager.saveBloomFilter(_transactionBloomFilterFilename);
        }

        Logger.log("[Shutting Down Thread Server]");
        _mainThreadPool.stop();
        _rpcThreadPool.stop();

        Logger.log("[Shutting Down Database]");
        _banFilter.close();

        if (_jsonRpcSocketServer != null) {
            _jsonRpcSocketServer.stop();
        }

        while (! _openDatabaseConnectionPools.isEmpty()) {
            final MysqlDatabaseConnectionPool databaseConnectionPool = _openDatabaseConnectionPools.remove(0);
            databaseConnectionPool.close();
        }

        _environment.getMasterDatabaseManagerCache().close();

        Logger.shutdown();

        synchronized (_shutdownPin) {
            _shutdownPin.notifyAll();
        }
    }

    protected NodeModule(final String configurationFilename) {
        final Thread mainThread = Thread.currentThread();

        _configuration = _loadConfigurationFile(configurationFilename);

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();
        final DatabaseProperties databaseProperties = _configuration.getDatabaseProperties();

        final Integer maxPeerCount = (serverProperties.skipNetworking() ? 0 : serverProperties.getMaxPeerCount());
        _mainThreadPool = new MainThreadPool(Math.max(maxPeerCount * 8, 256), 10000L);
        _rpcThreadPool = new MainThreadPool(32, 15000L);

        _mainThreadPool.setShutdownCallback(new Runnable() {
            @Override
            public void run() {
                try {
                    _shutdown();
                } catch (final Throwable ignored) { }
            }
        });

        mainThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.log(throwable);
                    _shutdown();
                }
                catch (final Throwable ignored) { }
            }
        });

        final EmbeddedMysqlDatabase database;
        {
            EmbeddedMysqlDatabase databaseInstance = null;
            try {
                final DatabaseInitializer databaseInitializer = new DatabaseInitializer("queries/init.sql", Constants.DATABASE_VERSION, new DatabaseInitializer.DatabaseUpgradeHandler() {
                    @Override
                    public Boolean onUpgrade(final int currentVersion, final int requiredVersion) { return false; }
                });

                final DatabaseCommandLineArguments commandLineArguments = new DatabaseCommandLineArguments();
                DatabaseConfigurer.configureCommandLineArguments(commandLineArguments, serverProperties);

                // databaseInstance = new DebugEmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);
                Logger.log("[Initializing Database]");
                databaseInstance = new EmbeddedMysqlDatabase(databaseProperties, databaseInitializer, commandLineArguments);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
            database = databaseInstance;

            if (database == null) {
                BitcoinUtil.exitFailure();
            }

            database.setPreShutdownHook(new Runnable() {
                @Override
                public void run() {
                    _shutdown();
                }
            });
        }

        { // Initialize the NativeUnspentTransactionOutputCache...
            final Boolean nativeCacheIsEnabled = NativeUnspentTransactionOutputCache.isEnabled();
            if (nativeCacheIsEnabled) {
                NativeUnspentTransactionOutputCache.init();
            }
            else {
                Logger.log("NOTICE: NativeUtxoCache not enabled.");
            }
        }

        final Long maxUtxoCacheByteCount = serverProperties.getMaxUtxoCacheByteCount();
        final MasterDatabaseManagerCache masterDatabaseManagerCache = new MasterDatabaseManagerCache(maxUtxoCacheByteCount);
        final ReadOnlyLocalDatabaseManagerCache readOnlyDatabaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(masterDatabaseManagerCache);

        _environment = new Environment(database, masterDatabaseManagerCache);

        final MysqlDatabaseConnectionFactory databaseConnectionFactory = database.getDatabaseConnectionFactory();

        _banFilter = new BanFilter(databaseConnectionFactory);

        final MutableMedianBlockTime medianBlockTime;
        final MutableMedianBlockTime medianBlockHeaderTime;
        { // Initialize MedianBlockTime...
            {
                MutableMedianBlockTime newMedianBlockTime = null;
                MutableMedianBlockTime newMedianBlockHeaderTime = null;
                try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, readOnlyDatabaseManagerCache);
                    newMedianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();
                    newMedianBlockHeaderTime = blockHeaderDatabaseManager.initializeMedianBlockHeaderTime();
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    BitcoinUtil.exitFailure();
                }
                medianBlockTime = newMedianBlockTime;
                medianBlockHeaderTime = newMedianBlockHeaderTime;
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);
        final MemoryPoolEnquirer memoryPoolEnquirer = new MemoryPoolEnquirerHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);

        final BlockInventoryMessageHandler blockInventoryMessageHandler;
        {
            blockInventoryMessageHandler = new BlockInventoryMessageHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache, synchronizationStatusHandler);
        }

        final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache(readOnlyDatabaseManagerCache);

        final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                return new ThreadPoolThrottle(serverProperties.getMaxMessagesPerSecond(), _mainThreadPool);
            }
        };

        { // Initialize NodeInitializer...
            final Runnable newInventoryCallback = new Runnable() {
                @Override
                public void run() {
                    _transactionDownloader.wakeUp();
                }
            };

            final TransactionInventoryMessageHandlerFactory transactionsAnnouncementCallbackFactory = new TransactionInventoryMessageHandlerFactory(databaseConnectionFactory, readOnlyDatabaseManagerCache, newInventoryCallback);
            final QueryBlocksHandler queryBlocksHandler = new QueryBlocksHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);
            final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);
            final RequestDataHandler requestDataHandler = new RequestDataHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);
            _nodeInitializer = new NodeInitializer(synchronizationStatusHandler, blockInventoryMessageHandler, transactionsAnnouncementCallbackFactory, queryBlocksHandler, queryBlockHeadersHandler, requestDataHandler, threadPoolFactory);
        }

        { // Initialize NodeManager...
            _bitcoinNodeManager = new BitcoinNodeManager(maxPeerCount, databaseConnectionFactory, readOnlyDatabaseManagerCache, _mutableNetworkTime, _nodeInitializer, _banFilter, memoryPoolEnquirer, synchronizationStatusHandler, _mainThreadPool, threadPoolFactory);
        }

        { // Initialize the TransactionDownloader...
            _transactionDownloader = new TransactionDownloader(_bitcoinNodeManager, databaseConnectionFactory, readOnlyDatabaseManagerCache);
        }

        { // Initialize the TransactionProcessor...
            _transactionProcessor = new TransactionProcessor(databaseConnectionFactory, readOnlyDatabaseManagerCache, _mutableNetworkTime, medianBlockTime, _bitcoinNodeManager);
        }

        final BlockProcessor blockProcessor;
        { // Initialize BlockSynchronizer...
            blockProcessor = new BlockProcessor(databaseConnectionFactory, masterDatabaseManagerCache, _mutableNetworkTime, medianBlockTime, orphanedTransactionsCache);
            blockProcessor.setMaxThreadCount(serverProperties.getMaxThreadCount());
            blockProcessor.setTrustedBlockHeight(serverProperties.getTrustedBlockHeight());
        }

        { // Initialize the BlockDownloader...
            _blockDownloader = new BlockDownloader(_bitcoinNodeManager, databaseConnectionFactory, readOnlyDatabaseManagerCache);
        }

        final BlockDownloadRequester blockDownloadRequester = new BlockDownloadRequester(databaseConnectionFactory, _blockDownloader, _bitcoinNodeManager, readOnlyDatabaseManagerCache);

        { // Initialize BlockHeaderDownloader...
            _blockHeaderDownloader = new BlockHeaderDownloader(databaseConnectionFactory, readOnlyDatabaseManagerCache, _bitcoinNodeManager, medianBlockHeaderTime, blockDownloadRequester, _mainThreadPool);
        }

        { // Initialize BlockchainBuilder...
            _blockchainBuilder = new BlockchainBuilder(_bitcoinNodeManager, databaseConnectionFactory, readOnlyDatabaseManagerCache, blockProcessor, _blockDownloader.getStatusMonitor(), blockDownloadRequester, _mainThreadPool);
        }

        if (serverProperties.shouldTrimBlocks()) {
            _addressProcessor = new DisabledAddressProcessor();
        }
        else {
            _addressProcessor = new AddressProcessor(databaseConnectionFactory, readOnlyDatabaseManagerCache);
        }

        final LocalDatabaseManagerCache localDatabaseCache = new LocalDatabaseManagerCache(masterDatabaseManagerCache);
        final BlockTrimmer blockTrimmer = new BlockTrimmer(databaseConnectionFactory, localDatabaseCache);

        { // Set the synchronization elements to cascade to each component...
            _blockchainBuilder.setNewBlockProcessedCallback(new BlockchainBuilder.NewBlockProcessedCallback() {
                @Override
                public void onNewBlock(final Long blockHeight, final Block block) {
                    final Sha256Hash blockHash = block.getHash();

                    _addressProcessor.wakeUp();

                    if (serverProperties.shouldTrimBlocks()) {
                        final Integer keepBlockCount = 144; // NOTE: Keeping the last days of blocks protects any non-malicious chain re-organization from failing...
                        if (blockHeight > keepBlockCount) {
                            try {
                                blockTrimmer.trimBlock(blockHash, keepBlockCount);
                                masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseCache);
                            }
                            catch (final Exception exception) {
                                Logger.log("Error trimming Block: " + blockHash);
                                Logger.log(exception);
                            }
                        }
                    }

                    final Long blockHeaderDownloaderBlockHeight = _blockHeaderDownloader.getBlockHeight();
                    if (blockHeaderDownloaderBlockHeight <= blockHeight) {
                        _blockHeaderDownloader.wakeUp();
                    }

                    try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, localDatabaseCache);
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, localDatabaseCache);
                        final BlockId newBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                        final BlockchainSegmentId newBlockBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newBlockId);
                        final Boolean newBlockIsOnMainChain = blockchainDatabaseManager.areBlockchainSegmentsConnected(newBlockBlockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);

                        if (synchronizationStatusHandler.getState() != State.SHUTTING_DOWN) {
                            if (newBlockIsOnMainChain) {
                                if (blockHeight < blockHeaderDownloaderBlockHeight) {
                                    synchronizationStatusHandler.setState(State.SYNCHRONIZING);
                                }
                                else {
                                    synchronizationStatusHandler.setState(State.ONLINE);
                                }
                            }
                        }

                        { // Broadcast new Block...
                            if (synchronizationStatusHandler.getState() == State.ONLINE) {
                                final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);

                                final HashMap<NodeId, BitcoinNode> bitcoinNodeMap = new HashMap<NodeId, BitcoinNode>();
                                final List<NodeId> connectedNodeIds;
                                {
                                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                                    final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<NodeId>(connectedNodes.getSize());
                                    for (final BitcoinNode bitcoinNode : connectedNodes) {
                                        final NodeId nodeId = bitcoinNode.getId();
                                        nodeIdsBuilder.add(nodeId);
                                        bitcoinNodeMap.put(nodeId, bitcoinNode);
                                    }
                                    connectedNodeIds = nodeIdsBuilder.build();
                                }

                                final List<NodeId> nodeIdsWithoutBlocks = nodeDatabaseManager.filterNodesViaBlockInventory(connectedNodeIds, blockHash, FilterType.KEEP_NODES_WITHOUT_INVENTORY);
                                for (final NodeId nodeId : nodeIdsWithoutBlocks) {
                                    final BitcoinNode bitcoinNode = bitcoinNodeMap.get(nodeId);
                                    if (bitcoinNode == null) { continue; }

                                    _bitcoinNodeManager.transmitBlockHash(bitcoinNode, block);
                                }
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                    }
                }
            });

            _blockHeaderDownloader.setNewBlockHeaderAvailableCallback(new Runnable() {
                @Override
                public void run() {
                    _blockDownloader.wakeUp();

                    try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, localDatabaseCache);
                        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, localDatabaseCache);
                        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                        final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

                        if (synchronizationStatusHandler.getState() != State.SHUTTING_DOWN) {
                            if (_blockHeaderDownloader.getBlockHeight() > headBlockHeight) {
                                synchronizationStatusHandler.setState(State.SYNCHRONIZING);
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.log(exception);
                    }
                }
            });

            _blockDownloader.setNewBlockAvailableCallback(new Runnable() {
                @Override
                public void run() {
                    _blockchainBuilder.wakeUp();
                }
            });

            blockInventoryMessageHandler.setNewBlockHashReceivedCallback(new Runnable() {
                @Override
                public void run() {
                    _blockDownloader.wakeUp();
                }
            });

            blockInventoryMessageHandler.setNodeInventoryUpdatedCallback(new Runnable() {
                @Override
                public void run() {
                    _blockDownloader.wakeUp();
                }
            });

            _transactionDownloader.setNewTransactionAvailableCallback(new Runnable() {
                @Override
                public void run() {
                    _transactionProcessor.wakeUp();
                }
            });
        }

        _socketServer = new BinarySocketServer(serverProperties.getBitcoinPort(), BitcoinProtocolMessage.BINARY_PACKET_FORMAT, _mainThreadPool);
        _socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final String host = binarySocket.getHost();

                final Boolean isBanned = _banFilter.isHostBanned(host);
                if (isBanned) {
                    binarySocket.close();
                    return;
                }

                final Boolean shouldBan = _banFilter.shouldBanHost(host);
                if (shouldBan) {
                    _banFilter.banHost(host);
                    binarySocket.close();
                    return;
                }

                Logger.log("New Connection: " + binarySocket);
                final BitcoinNode node = _nodeInitializer.initializeNode(binarySocket);
                _bitcoinNodeManager.addNode(node);
            }
        });

        final Integer rpcPort = _configuration.getServerProperties().getBitcoinRpcPort();
        if (rpcPort > 0) {

            final JsonRpcSocketServerHandler.StatisticsContainer statisticsContainer = new JsonRpcSocketServerHandler.StatisticsContainer();
            { // Initialize statistics container...
                statisticsContainer.averageBlockHeadersPerSecond = _blockHeaderDownloader.getAverageBlockHeadersPerSecondContainer();
                statisticsContainer.averageBlocksPerSecond = blockProcessor.getAverageBlocksPerSecondContainer();
                statisticsContainer.averageTransactionsPerSecond = blockProcessor.getAverageTransactionsPerSecondContainer();
            }

            final ShutdownHandler shutdownHandler = new ShutdownHandler(mainThread, _blockHeaderDownloader, _blockDownloader, _blockchainBuilder, synchronizationStatusHandler);
            final NodeHandler nodeHandler = new NodeHandler(_bitcoinNodeManager, _nodeInitializer);
            final QueryAddressHandler queryAddressHandler = new QueryAddressHandler(databaseConnectionFactory, readOnlyDatabaseManagerCache);
            final ThreadPoolInquisitor threadPoolInquisitor = new ThreadPoolInquisitor(_mainThreadPool);

            final ServiceInquisitor serviceInquisitor = new ServiceInquisitor();
            for (final SleepyService sleepyService : new SleepyService[]{ _addressProcessor, _transactionProcessor, _transactionDownloader, _blockchainBuilder, _blockDownloader, _blockHeaderDownloader }) {
                serviceInquisitor.addService(sleepyService.getClass().getSimpleName(), sleepyService.getStatusMonitor());
            }

            final JsonSocketServer jsonRpcSocketServer = new JsonSocketServer(rpcPort, _rpcThreadPool);

            final JsonRpcSocketServerHandler rpcSocketServerHandler = new JsonRpcSocketServerHandler(_environment, synchronizationStatusHandler, statisticsContainer);
            rpcSocketServerHandler.setShutdownHandler(shutdownHandler);
            rpcSocketServerHandler.setNodeHandler(nodeHandler);
            rpcSocketServerHandler.setQueryAddressHandler(queryAddressHandler);
            rpcSocketServerHandler.setThreadPoolInquisitor(threadPoolInquisitor);
            rpcSocketServerHandler.setServiceInquisitor(serviceInquisitor);

            jsonRpcSocketServer.setSocketConnectedCallback(rpcSocketServerHandler);
            _jsonRpcSocketServer = jsonRpcSocketServer;
        }
        else {
            _jsonRpcSocketServer = null;
        }

        _transactionBloomFilterFilename = (databaseProperties.getDataDirectory() + "/transaction-bloom-filter.dat");

        try (final MysqlDatabaseConnection databaseConnection = databaseConnectionFactory.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, localDatabaseCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, localDatabaseCache);
            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final Long headBlockHeaderHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId));
            final Long headBlockHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockId));

            // TODO: Encountering an exception here (and perhaps other places within the constructor) causes the application to stall after _shutdown is called...
            if ( (headBlockHeaderHeight == 0L) || (headBlockHeaderHeight > headBlockHeight) ) {
                synchronizationStatusHandler.setState(State.SYNCHRONIZING);
            }
            else {
                synchronizationStatusHandler.setState(State.ONLINE);
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    public void loop() {
        final Runtime runtime = Runtime.getRuntime();

        if (_shouldWarmUpCache) {
            _warmUpCache();
        }

        final Configuration.ServerProperties serverProperties = _configuration.getServerProperties();

        if (serverProperties.shouldUseTransactionBloomFilter()) {
            Logger.log("[Loading Tx Bloom Filter]");
            final EmbeddedMysqlDatabase database = _environment.getDatabase();
            try (final MysqlDatabaseConnection databaseConnection = database.newConnection()) {
                TransactionDatabaseManager.initializeBloomFilter(_transactionBloomFilterFilename, databaseConnection);
            }
            catch (final DatabaseException exception) {
                Logger.log(exception);
            }
        }

        if (! serverProperties.skipNetworking()) {
            Logger.log("[Starting Node Manager]");
            _bitcoinNodeManager.startNodeMaintenanceThread();

            final Configuration.SeedNodeProperties[] seedNodes = serverProperties.getSeedNodeProperties();
            for (final Configuration.SeedNodeProperties seedNodeProperties : seedNodes) {
                final String host = seedNodeProperties.getAddress();
                final Integer port = seedNodeProperties.getPort();
                try {
                    final InetAddress ipAddress = InetAddress.getByName(host);
                    final String ipAddressString = ipAddress.getHostAddress();

                    final BitcoinNode node = _nodeInitializer.initializeNode(ipAddressString, port);
                    _bitcoinNodeManager.addNode(node);
                }
                catch (final Exception exception) {
                    Logger.log("Unable to determine host: " + host);
                }
            }
        }
        else {
            Logger.log("[Skipped Networking]");
        }

        if (_jsonRpcSocketServer != null) {
            Logger.log("[Starting RPC Server]");
            _jsonRpcSocketServer.start();
        }
        else {
            Logger.log("NOTICE: Bitcoin RPC Server not started.");
        }

        Logger.log("[Starting Socket Server]");
        _socketServer.start();

        if (! serverProperties.skipNetworking()) {
            Logger.log("[Starting Header Downloader]");
            _blockHeaderDownloader.start();

            Logger.log("[Starting Block Downloader]");
            _blockDownloader.start();

            Logger.log("[Starting Transaction Downloader]");
            _transactionDownloader.start();
        }

        Logger.log("[Starting Block Processor]");
        _blockchainBuilder.start();

        Logger.log("[Starting Transaction Processor]");
        _transactionProcessor.start();

        Logger.log("[Started Address Processor]");
        _addressProcessor.start();

        if (! serverProperties.skipNetworking()) {
            Logger.log("[Connecting To Peers]");
            _connectToAdditionalNodes();
        }

        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(60000); } catch (final Exception exception) { break; }

            Logger.log("Current Memory Usage: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes | MAX=" + runtime.maxMemory() + " TOTAL=" + runtime.totalMemory() + " FREE=" + runtime.freeMemory());
            Logger.log("Utxo Cache Hit: " + TransactionOutputDatabaseManager.cacheHit.get() + " vs " + TransactionOutputDatabaseManager.cacheMiss.get() + " (" + (TransactionOutputDatabaseManager.cacheHit.get() / ((float) TransactionOutputDatabaseManager.cacheHit.get() + TransactionOutputDatabaseManager.cacheMiss.get()) * 100F) + "%)");
            Logger.log("ThreadPool Queue: " + _mainThreadPool.getQueueCount() + " | Active Thread Count: " + _mainThreadPool.getActiveThreadCount());
        }

        System.exit(0);
    }
}

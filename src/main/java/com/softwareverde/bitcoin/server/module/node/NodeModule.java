package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactory;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactoryCore;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.SeedNodeProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseMaintainer;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.*;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestSpvBlockHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.QueryUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.RequestSlpTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionInventoryMessageHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.*;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.DisabledBanFilter;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.*;
import com.softwareverde.bitcoin.server.module.node.sync.*;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.HeadersBootstrapper;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorFactory;
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
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeModule {
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final BlockCache _blockCache;
    protected final MasterInflater _masterInflater;

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final BinarySocketServer _socketServer;
    protected final NodeRpcHandler _nodeRpcHandler;
    protected final JsonSocketServer _jsonRpcSocketServer;
    protected final BlockHeaderDownloader _blockHeaderDownloader;
    protected final BlockDownloader _blockDownloader;
    protected final TransactionDownloader _transactionDownloader;
    protected final TransactionProcessor _transactionProcessor;
    protected final TransactionRelay _transactionRelay;
    protected final BlockchainBuilder _blockchainBuilder;
    protected final AddressProcessor _addressProcessor;
    protected final SlpTransactionProcessor _slpTransactionProcessor;
    protected final RequestDataHandler _requestDataHandler;

    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final String _transactionBloomFilterFilename;

    protected final MainThreadPool _mainThreadPool;
    protected final MainThreadPool _rpcThreadPool;

    protected final MilliTimer _uptimeTimer = new MilliTimer();
    protected final Thread _databaseMaintenanceThread;

    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected void _connectToAdditionalNodes() {
        final SeedNodeProperties[] seedNodes = _bitcoinProperties.getSeedNodeProperties();
        final HashSet<String> seedNodeHosts = new HashSet<String>(seedNodes.length);
        for (final SeedNodeProperties seedNodeProperties : seedNodes) {
            final String host = seedNodeProperties.getAddress();
            final Integer port = seedNodeProperties.getPort();

            seedNodeHosts.add(host + port);
        }

        final Database database = _environment.getDatabase();
        final Integer maxPeerCount = _bitcoinProperties.getMaxPeerCount();
        if (maxPeerCount < 1) { return; }

        final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableList<NodeFeatures.Feature>();
        requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
        requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);

        try (final DatabaseConnection databaseConnection = database.newConnection()) {
            final DatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, _blockCache, _masterInflater);

            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
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

                Logger.info("Connecting to former peer: " + host + ":" + port);

                try { Thread.sleep(500L); } catch (final Exception exception) { }
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    protected void _shutdown() {
        synchronized (_isShuttingDown) {
            if (! _isShuttingDown.compareAndSet(false, true)) {
                Logger.info("[Awaiting Shutdown Completion]");
                try { _isShuttingDown.wait(30000); } catch (final Exception exception) { }
                return;
            }
        }

        Logger.info("[Stopping Request Handler]");
        _requestDataHandler.shutdown();

        Logger.info("[Stopping Database Maintenance Thread]");
        _databaseMaintenanceThread.interrupt();

        if (_slpTransactionProcessor != null) {
            Logger.info("[Stopping SlpTransaction Processor]");
            _slpTransactionProcessor.stop();
        }

        Logger.info("[Stopping Addresses Processor]");
        _addressProcessor.stop();

        Logger.info("[Stopping Transaction Processor]");
        _transactionProcessor.stop();

        Logger.info("[Stopping Transaction Downloader]");
        _transactionDownloader.stop();

        Logger.info("[Stopping Block Processor]");
        _blockchainBuilder.stop();

        Logger.info("[Stopping Block Downloader]");
        _blockDownloader.stop();

        Logger.info("[Stopping Header Downloader]");
        _blockHeaderDownloader.stop();

        Logger.info("[Stopping Node Manager]");
        _bitcoinNodeManager.shutdown();
        _bitcoinNodeManager.stopNodeMaintenanceThread();

        Logger.info("[Stopping Socket Server]");
        _socketServer.stop();

        Logger.info("[Shutting Down Thread Server]");
        _mainThreadPool.stop();
        _rpcThreadPool.stop();

        if (_jsonRpcSocketServer != null) {
            Logger.info("[Shutting Down RPC Server]");
            _jsonRpcSocketServer.stop();
        }

        Logger.info("[Shutting Down Database]");
        final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
        try {
            databaseConnectionPool.close();
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        try { _databaseMaintenanceThread.join(30000L); } catch (final InterruptedException exception) { }

        Logger.flush();

        synchronized (_isShuttingDown) {
            _isShuttingDown.notifyAll();
        }
    }

    public NodeModule(final BitcoinProperties bitcoinProperties, final Environment environment) {
        final Thread mainThread = Thread.currentThread();

        _masterInflater = new CoreInflater();

        { // Initialize the BlockCache...
            final String blockCacheDirectory = (bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME);
            _blockCache = new BlockCache(blockCacheDirectory, _masterInflater);
        }

        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        final Integer maxPeerCount = (bitcoinProperties.skipNetworking() ? 0 : bitcoinProperties.getMaxPeerCount());
        _mainThreadPool = new MainThreadPool(Math.max(maxPeerCount * 8, 256), 10000L);
        _rpcThreadPool = new MainThreadPool(32, 15000L);

        _mainThreadPool.setShutdownCallback(new Runnable() {
            @Override
            public void run() {
                try {
                    _shutdown();
                }
                catch (final Throwable ignored) { }
            }
        });

        mainThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.error(throwable);
                    _shutdown();
                }
                catch (final Throwable ignored) { }
            }
        });

        final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, _blockCache, _masterInflater);

        _banFilter = (bitcoinProperties.isBanFilterEnabled() ? new BanFilterCore(databaseManagerFactory) : new DisabledBanFilter());

        { // Ensure the data/cache directory exists...
            final String dataCacheDirectory = bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME;
            final File file = new File(dataCacheDirectory);
            if (! file.exists()) {
                final Boolean wasSuccessful = file.mkdirs();
                if (! wasSuccessful) {
                    Logger.warn("Unable to create data cache directory: " + dataCacheDirectory);
                }
            }
        }

        final MutableMedianBlockTime medianBlockTime;
        final MutableMedianBlockTime medianBlockHeaderTime;
        { // Initialize MedianBlockTime...
            {
                MutableMedianBlockTime newMedianBlockTime = null;
                MutableMedianBlockTime newMedianBlockHeaderTime = null;
                try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    newMedianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();
                    newMedianBlockHeaderTime = blockHeaderDatabaseManager.initializeMedianBlockHeaderTime();
                }
                catch (final DatabaseException exception) {
                    Logger.error(exception);
                    BitcoinUtil.exitFailure();
                }
                medianBlockTime = newMedianBlockTime;
                medianBlockHeaderTime = newMedianBlockHeaderTime;
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(databaseManagerFactory);
        final MemoryPoolEnquirer memoryPoolEnquirer = new MemoryPoolEnquirerHandler(databaseManagerFactory);

        final BlockInventoryMessageHandler blockInventoryMessageHandler;
        {
            blockInventoryMessageHandler = new BlockInventoryMessageHandler(databaseManagerFactory, synchronizationStatusHandler);
        }

        final OrphanedTransactionsCache orphanedTransactionsCache = new OrphanedTransactionsCache();

        final ThreadPoolFactory nodeThreadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                final ThreadPoolThrottle threadPoolThrottle = new ThreadPoolThrottle(bitcoinProperties.getMaxMessagesPerSecond(), _mainThreadPool);
                threadPoolThrottle.start();
                return threadPoolThrottle;
            }
        };

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                if (! bitcoinProperties.isTrimBlocksEnabled()) {
                    nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                }
                nodeFeatures.enableFeature(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_INDEX_ENABLED); // BitcoinVerde 2019-04-22
                nodeFeatures.enableFeature(NodeFeatures.Feature.SLP_INDEX_ENABLED); // BitcoinVerde 2019-10-24
                return nodeFeatures;
            }
        };

        _requestDataHandler = new RequestDataHandler(databaseManagerFactory, _blockCache);
        final RequestDataHandlerMonitor requestDataHandler = RequestDataHandlerMonitor.wrap(_requestDataHandler);
        { // Initialize the monitor with transactions from the memory pool...
            Logger.info("[Loading RequestDataHandlerMonitor]");
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                for (final TransactionId transactionId : transactionDatabaseManager.getUnconfirmedTransactionIds()) {
                    final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
                    requestDataHandler.addTransactionHash(transactionHash);
                }
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }

        { // Initialize NodeInitializer...
            final SpvUnconfirmedTransactionsHandler spvUnconfirmedTransactionsHandler = new SpvUnconfirmedTransactionsHandler(databaseManagerFactory);

            final NodeInitializer.Properties nodeInitializerProperties = new NodeInitializer.Properties();

            final Runnable newInventoryCallback = new Runnable() {
                @Override
                public void run() {
                    _transactionDownloader.wakeUp();
                }
            };

            nodeInitializerProperties.synchronizationStatus = synchronizationStatusHandler;
            nodeInitializerProperties.blockInventoryMessageHandler = blockInventoryMessageHandler;
            nodeInitializerProperties.threadPoolFactory = nodeThreadPoolFactory;
            nodeInitializerProperties.localNodeFeatures = localNodeFeatures;
            nodeInitializerProperties.transactionsAnnouncementCallbackFactory = new TransactionInventoryMessageHandlerFactory(databaseManagerFactory, newInventoryCallback);
            nodeInitializerProperties.queryBlocksCallback = new QueryBlocksHandler(databaseManagerFactory);
            nodeInitializerProperties.queryBlockHeadersCallback = new QueryBlockHeadersHandler(databaseManagerFactory);
            nodeInitializerProperties.requestDataCallback = requestDataHandler;
            nodeInitializerProperties.requestSpvBlocksCallback = new RequestSpvBlockHandler(databaseManagerFactory, spvUnconfirmedTransactionsHandler);
            nodeInitializerProperties.requestSlpTransactionsCallback = new RequestSlpTransactionsHandler(databaseManagerFactory);
            nodeInitializerProperties.queryUnconfirmedTransactionsCallback = new QueryUnconfirmedTransactionsHandler(databaseManagerFactory);

            nodeInitializerProperties.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<BitcoinNodeIpAddress>(connectedNodes.getCount());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            nodeInitializerProperties.binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;

            nodeInitializerProperties.onNewBloomFilterCallback = new BitcoinNode.OnNewBloomFilterCallback() {
                @Override
                public void run(final BitcoinNode bitcoinNode) {
                    spvUnconfirmedTransactionsHandler.broadcastUnconfirmedTransactions(bitcoinNode);
                }
            };

            _nodeInitializer = new NodeInitializer(nodeInitializerProperties);
        }

        { // Initialize NodeManager...
            final BitcoinNodeManager.Properties properties = new BitcoinNodeManager.Properties();
            {
                properties.databaseManagerFactory = databaseManagerFactory;
                properties.nodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, nodeThreadPoolFactory, localNodeFeatures);
                properties.maxNodeCount = maxPeerCount;
                properties.networkTime = _mutableNetworkTime;
                properties.nodeInitializer = _nodeInitializer;
                properties.banFilter = _banFilter;
                properties.memoryPoolEnquirer = memoryPoolEnquirer;
                properties.synchronizationStatusHandler = synchronizationStatusHandler;
                properties.threadPool = _mainThreadPool;
            }

            _bitcoinNodeManager = new BitcoinNodeManager(properties);
        }

        { // Initialize the TransactionDownloader...
            _transactionDownloader = new TransactionDownloader(databaseManagerFactory, _bitcoinNodeManager);
        }

        final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory();

        { // Initialize the TransactionProcessor...
            _transactionProcessor = new TransactionProcessor(databaseManagerFactory, transactionValidatorFactory, _mutableNetworkTime, medianBlockTime, _bitcoinNodeManager);
        }

        final BlockProcessor blockProcessor;
        { // Initialize BlockSynchronizer...
            blockProcessor = new BlockProcessor(databaseManagerFactory, _masterInflater, transactionValidatorFactory, _mutableNetworkTime, medianBlockTime, orphanedTransactionsCache, _blockCache);
            blockProcessor.setMaxThreadCount(bitcoinProperties.getMaxThreadCount());
            blockProcessor.setTrustedBlockHeight(bitcoinProperties.getTrustedBlockHeight());
        }

        { // Initialize the BlockDownloader...
            _blockDownloader = new BlockDownloader(databaseManagerFactory, _bitcoinNodeManager, _blockCache);
        }

        final BlockDownloadRequester blockDownloadRequester = new BlockDownloadRequesterCore(databaseManagerFactory, _blockDownloader, _bitcoinNodeManager);

        final BlockValidatorFactory blockValidatorFactory = new BlockValidatorFactoryCore();

        { // Initialize BlockHeaderDownloader...
            _blockHeaderDownloader = new BlockHeaderDownloader(databaseManagerFactory, _bitcoinNodeManager, blockValidatorFactory, medianBlockHeaderTime, blockDownloadRequester, _mainThreadPool);
        }

        { // Initialize BlockchainBuilder...
            _blockchainBuilder = new BlockchainBuilder(_bitcoinNodeManager, databaseManagerFactory, blockProcessor, _blockDownloader.getStatusMonitor(), blockDownloadRequester, _mainThreadPool);
        }

        if (true) {
            _slpTransactionProcessor = null;
            _addressProcessor = new DisabledAddressProcessor();
        }
        else {
            _slpTransactionProcessor = new SlpTransactionProcessor(databaseManagerFactory);

            _addressProcessor = new AddressProcessor(databaseManagerFactory);
            _addressProcessor.setOnSleepCallback(new Runnable() {
                @Override
                public void run() {
                    Logger.trace("AddressProcessor: Callback");
                    _slpTransactionProcessor.wakeUp();
                }
            });
        }

        { // Set the synchronization elements to cascade to each component...
            _blockchainBuilder.setNewBlockProcessedCallback(new BlockchainBuilder.NewBlockProcessedCallback() {
                @Override
                public void onNewBlock(final Long blockHeight, final Block block) {
                    final Sha256Hash blockHash = block.getHash();

                    if (_blockCache != null) {
                        _blockCache.cacheBlock(block, blockHeight);
                    }

                    _addressProcessor.wakeUp();

                    final Long blockHeaderDownloaderBlockHeight = _blockHeaderDownloader.getBlockHeight();
                    if (blockHeaderDownloaderBlockHeight <= blockHeight) {
                        _blockHeaderDownloader.wakeUp();
                    }

                    try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                        final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

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
                                final HashMap<NodeId, BitcoinNode> bitcoinNodeMap = new HashMap<NodeId, BitcoinNode>();
                                final List<NodeId> connectedNodeIds;
                                {
                                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                                    final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<NodeId>(connectedNodes.getCount());
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

                            final NodeRpcHandler nodeRpcHandler = _nodeRpcHandler;
                            if (nodeRpcHandler != null) {
                                nodeRpcHandler.onNewBlock(block);
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
                    }
                }
            });

            _blockHeaderDownloader.setNewBlockHeaderAvailableCallback(new Runnable() {
                @Override
                public void run() {
                    _blockDownloader.wakeUp();

                    try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                        final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                        final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);

                        if (synchronizationStatusHandler.getState() != State.SHUTTING_DOWN) {
                            if (_blockHeaderDownloader.getBlockHeight() > headBlockHeight) {
                                synchronizationStatusHandler.setState(State.SYNCHRONIZING);
                            }
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.warn(exception);
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

            _transactionProcessor.setNewTransactionProcessedCallback(new TransactionProcessor.Callback() {
                @Override
                public void onNewTransactions(final List<Transaction> transactions) {

                    _transactionRelay.relayTransactions(transactions);
                    _addressProcessor.wakeUp();
                }
            });
        }

        _socketServer = new BinarySocketServer(bitcoinProperties.getBitcoinPort(), BitcoinProtocolMessage.BINARY_PACKET_FORMAT, _mainThreadPool);
        _socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final Ip ip = binarySocket.getIp();

                final Boolean isBanned = _banFilter.isIpBanned(ip);
                if (isBanned) {
                    Logger.trace("Ignoring Banned Connection: " + binarySocket.toString());
                    binarySocket.close();
                    return;
                }

                Logger.debug("New Connection: " + binarySocket.toString());
                _banFilter.onNodeConnected(ip);
                final BitcoinNode node = _nodeInitializer.initializeNode(binarySocket);
                _bitcoinNodeManager.addNode(node);
            }
        });

        final Integer rpcPort = _bitcoinProperties.getBitcoinRpcPort();
        if (rpcPort > 0) {
            final NodeRpcHandler.StatisticsContainer statisticsContainer = new NodeRpcHandler.StatisticsContainer();
            { // Initialize statistics container...
                statisticsContainer.averageBlockHeadersPerSecond = _blockHeaderDownloader.getAverageBlockHeadersPerSecondContainer();
                statisticsContainer.averageBlocksPerSecond = blockProcessor.getAverageBlocksPerSecondContainer();
                statisticsContainer.averageTransactionsPerSecond = blockProcessor.getAverageTransactionsPerSecondContainer();
            }

            final NodeRpcHandler rpcSocketServerHandler = new NodeRpcHandler(statisticsContainer, _rpcThreadPool, _masterInflater);
            {
                final ShutdownHandler shutdownHandler = new ShutdownHandler(mainThread, _blockHeaderDownloader, _blockDownloader, _blockchainBuilder, synchronizationStatusHandler);
                final NodeHandler nodeHandler = new NodeHandler(_bitcoinNodeManager, _nodeInitializer);
                final QueryAddressHandler queryAddressHandler = new QueryAddressHandler(databaseManagerFactory);
                final ThreadPoolInquisitor threadPoolInquisitor = new ThreadPoolInquisitor(_mainThreadPool);

                final BlockValidator blockValidator = blockValidatorFactory.newBlockValidator(databaseManagerFactory, transactionValidatorFactory, _mutableNetworkTime, medianBlockTime);
                final RpcDataHandler rpcDataHandler = new RpcDataHandler(databaseManagerFactory, _transactionDownloader, _blockDownloader, blockValidator, transactionValidatorFactory, _mutableNetworkTime, medianBlockTime, _blockCache);

                final MetadataHandler metadataHandler = new MetadataHandler(databaseManagerFactory);
                final QueryBlockchainHandler queryBlockchainHandler = new QueryBlockchainHandler(databaseConnectionPool);

                final ServiceInquisitor serviceInquisitor = new ServiceInquisitor();
                for (final SleepyService sleepyService : new SleepyService[]{ _addressProcessor, _slpTransactionProcessor, _transactionProcessor, _transactionDownloader, _blockchainBuilder, _blockDownloader, _blockHeaderDownloader }) {
                    if (sleepyService != null) {
                        final Class<?> clazz = sleepyService.getClass();
                        final String serviceName = clazz.getSimpleName();
                        serviceInquisitor.addService(serviceName, sleepyService.getStatusMonitor());
                    }
                }

                final NodeRpcHandler.LogLevelSetter logLevelSetter = new NodeRpcHandler.LogLevelSetter() {
                    @Override
                    public void setLogLevel(final String packageName, final String logLevelString) {
                        final LogLevel logLevel = LogLevel.fromString(logLevelString);
                        if (logLevel == null) { return; }

                        Logger.setLogLevel(packageName, logLevel);
                        Logger.debug("Updated Log Level: " + packageName + "=" + logLevel);
                    }
                };

                rpcSocketServerHandler.setSynchronizationStatusHandler(synchronizationStatusHandler);
                rpcSocketServerHandler.setShutdownHandler(shutdownHandler);
                rpcSocketServerHandler.setNodeHandler(nodeHandler);
                rpcSocketServerHandler.setQueryAddressHandler(queryAddressHandler);
                rpcSocketServerHandler.setThreadPoolInquisitor(threadPoolInquisitor);
                rpcSocketServerHandler.setServiceInquisitor(serviceInquisitor);
                rpcSocketServerHandler.setDataHandler(rpcDataHandler);
                rpcSocketServerHandler.setMetadataHandler(metadataHandler);
                rpcSocketServerHandler.setQueryBlockchainHandler(queryBlockchainHandler);
                rpcSocketServerHandler.setLogLevelSetter(logLevelSetter);
            }

            final JsonSocketServer jsonRpcSocketServer = new JsonSocketServer(rpcPort, _rpcThreadPool);
            jsonRpcSocketServer.setSocketConnectedCallback(rpcSocketServerHandler);
            _nodeRpcHandler = rpcSocketServerHandler;
            _jsonRpcSocketServer = jsonRpcSocketServer;
        }
        else {
            _nodeRpcHandler = null;
            _jsonRpcSocketServer = null;
        }

        { // Initialize Transaction Relay...
            final Boolean shouldRelayInvalidSlpTransactions = _bitcoinProperties.isInvalidSlpTransactionRelayEnabled();
            _transactionRelay = new TransactionRelay(databaseManagerFactory, _bitcoinNodeManager, requestDataHandler, _nodeRpcHandler, shouldRelayInvalidSlpTransactions);
        }

        _transactionBloomFilterFilename = (_bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME + "/transaction-bloom-filter");

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
            final Long headBlockHeaderHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId));
            final Long headBlockHeight = Util.coalesce(blockHeaderDatabaseManager.getBlockHeight(headBlockId));

            // TODO: If an exception is found here (and perhaps other places within the constructor) then the application stalls after _shutdown is called...
            if ( (headBlockHeaderHeight == 0L) || (headBlockHeaderHeight > headBlockHeight) ) {
                synchronizationStatusHandler.setState(State.SYNCHRONIZING);
            }
            else {
                synchronizationStatusHandler.setState(State.ONLINE);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        { // Initialize the DatabaseMaintenance Thread...
            final DatabaseMaintainer databaseMaintainer = new DatabaseMaintainer(databaseConnectionPool);
            _databaseMaintenanceThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    final Thread thread = _databaseMaintenanceThread;
                    // HH    MM    SS      MS
                    final Long analyzeEveryMilliseconds = (24L * 60L * 60L * 1000L); // 24 Hours
                    while (! thread.isInterrupted()) {
                        try {
                            Thread.sleep(analyzeEveryMilliseconds);
                            databaseMaintainer.analyzeTables();
                        }
                        catch (final InterruptedException exception) { break; }
                    }
                }
            });
            _databaseMaintenanceThread.setName("Database Maintenance Thread");
            _databaseMaintenanceThread.setDaemon(false);
            _databaseMaintenanceThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.error("Uncaught exception in Database Maintenance Thread", exception);
                }
            });
        }
    }

    public void loop() {
        final Runtime runtime = Runtime.getRuntime();

        if (_bitcoinProperties.isBootstrapEnabled()) {
            Logger.info("[Bootstrapping Headers]");
            final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
            final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, _blockCache, _masterInflater);
            final HeadersBootstrapper headersBootstrapper = new HeadersBootstrapper(databaseManagerFactory);
            headersBootstrapper.run();
        }

        if (! _bitcoinProperties.skipNetworking()) {
            Logger.info("[Starting Node Manager]");
            _bitcoinNodeManager.startNodeMaintenanceThread();

            final SeedNodeProperties[] whitelistedNodes = _bitcoinProperties.getWhitelistedNodes();
            for (final SeedNodeProperties seedNodeProperties : whitelistedNodes) {
                final String host = seedNodeProperties.getAddress();
                try {
                    final Ip ip = Ip.fromStringOrHost(host);
                    if (ip == null) {
                        Logger.warn("Unable to determine seed node host: " + host);
                        continue;
                    }

                    _bitcoinNodeManager.addIpToWhitelist(ip);
                }
                catch (final Exception exception) {
                    Logger.debug("Unable to determine host: " + host);
                }
            }

            final SeedNodeProperties[] seedNodes = _bitcoinProperties.getSeedNodeProperties();
            for (final SeedNodeProperties seedNodeProperties : seedNodes) {
                final String host = seedNodeProperties.getAddress();
                final Integer port = seedNodeProperties.getPort();
                try {
                    final Ip ip = Ip.fromStringOrHost(host);
                    if (ip == null) {
                        Logger.warn("Unable to determine seed node host: " + host);
                        continue;
                    }

                    final String ipAddressString = ip.toString();

                    final BitcoinNode node = _nodeInitializer.initializeNode(ipAddressString, port);
                    _bitcoinNodeManager.addNode(node);
                }
                catch (final Exception exception) {
                    Logger.debug("Unable to determine host: " + host);
                }
            }
        }
        else {
            Logger.info("[Skipped Networking]");
        }

        if (_jsonRpcSocketServer != null) {
            Logger.info("[Starting RPC Server]");
            _jsonRpcSocketServer.start();
        }
        else {
            Logger.warn("Bitcoin RPC Server not started.");
        }

        Logger.info("[Starting Socket Server]");
        _socketServer.start();

        if (! _bitcoinProperties.skipNetworking()) {
            Logger.info("[Starting Header Downloader]");
            _blockHeaderDownloader.start();

            Logger.info("[Starting Block Downloader]");
            _blockDownloader.start();

            Logger.info("[Starting Transaction Downloader]");
            _transactionDownloader.start();
        }

        Logger.info("[Starting Block Processor]");
        _blockchainBuilder.start();

        Logger.info("[Starting Transaction Processor]");
        _transactionProcessor.start();

        Logger.info("[Started Address Processor]");
        _addressProcessor.start();

        if (_slpTransactionProcessor != null) {
            Logger.info("[Started SlpTransaction Processor]");
            _slpTransactionProcessor.start();
        }

        if (! _bitcoinProperties.skipNetworking()) {
            Logger.info("[Connecting To Peers]");
            _connectToAdditionalNodes();
        }

        _uptimeTimer.start();
        _databaseMaintenanceThread.start();

        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(5000); } catch (final Exception exception) { break; }

            // Logger.debug("Current Memory Usage: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes | MAX=" + runtime.maxMemory() + " TOTAL=" + runtime.totalMemory() + " FREE=" + runtime.freeMemory());
            // Logger.debug("ThreadPool Queue: " + _mainThreadPool.getQueueCount() + " | Active Thread Count: " + _mainThreadPool.getActiveThreadCount());
            //
            // final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
            // Logger.debug("Alive Connections Count: " + databaseConnectionPool.getAliveConnectionCount());
            // Logger.debug("Buffered Connections Count: " + databaseConnectionPool.getCurrentPoolSize());
            // Logger.debug("In-Use Connections Count: " + databaseConnectionPool.getInUseConnectionCount());
            //
            // Logger.flush();
            //
            // try { Thread.sleep(5000); } catch (final Exception exception) { break; }

            Logger.flush();
        }

        _shutdown();

        System.exit(0);
    }

    public void shutdown() {
        _shutdown();
    }
}

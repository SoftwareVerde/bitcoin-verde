package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.core.BlockDownloaderContext;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.PendingBlockLoaderContext;
import com.softwareverde.bitcoin.context.core.TransactionProcessorContext;
import com.softwareverde.bitcoin.context.lazy.LazyTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.SeedNodeProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.DatabaseMaintainer;
import com.softwareverde.bitcoin.server.database.pool.DatabaseConnectionPool;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.SpentTransactionOutputsCleanupService;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.MemoryPoolEnquirerHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SpvUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestSpvBlockHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.QueryUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.RequestSlpTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionInventoryMessageHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.manager.RequestDataHandlerMonitor;
import com.softwareverde.bitcoin.server.module.node.manager.TransactionRelay;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.DisabledBanFilter;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.MetadataHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.NodeHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.QueryAddressHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.QueryBlockchainHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.RpcDataHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ServiceInquisitor;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ShutdownHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ThreadPoolInquisitor;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.UtxoCacheHandler;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockDownloadRequester;
import com.softwareverde.bitcoin.server.module.node.sync.BlockDownloadRequesterCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.DisabledBlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.SlpTransactionProcessor;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.BlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.HeadersBootstrapper;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
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
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeModule {
    protected final Boolean _rebuildUtxoSet = false;

    protected final SystemTime _systemTime;
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final PendingBlockStoreCore _blockStore;
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
    protected final BlockchainIndexer _blockchainIndexer;
    protected final SlpTransactionProcessor _slpTransactionProcessor;
    protected final SpentTransactionOutputsCleanupService _spentTransactionOutputsCleanupService;
    protected final RequestDataHandler _requestDataHandler;
    protected final RequestDataHandlerMonitor _transactionWhitelist;
    protected final MutableMedianBlockTime _medianBlockTime;

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
            final DatabaseManager databaseManager = new FullNodeDatabaseManager(databaseConnection, _blockStore, _masterInflater);

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

        if (_spentTransactionOutputsCleanupService != null) {
            Logger.info("[Stopping Spent UTXO  Cleanup Service]");
            _spentTransactionOutputsCleanupService.stop();
        }

        if (! (_blockchainIndexer instanceof DisabledBlockchainIndexer)) {
            Logger.info("[Stopping Addresses Processor]");
            _blockchainIndexer.stop();
        }

        Logger.info("[Stopping Transaction Processor]");
        _transactionProcessor.stop();

        Logger.info("[Stopping Transaction Relay]");
        _transactionRelay.stop();

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

        Logger.info("[Committing UTXO Set]");
        {
            final DatabaseConnectionFactory databaseConnectionPool = _environment.getDatabaseConnectionPool();
            final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(databaseConnectionPool, _blockStore, _masterInflater);
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                final MilliTimer utxoCommitTimer = new MilliTimer();
                utxoCommitTimer.start();
                unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseConnectionPool);
                utxoCommitTimer.stop();
                Logger.debug("Commit Timer: " + utxoCommitTimer.getMillisecondsElapsed() + "ms.");
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }

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

        Logger.info("[Exiting]");
    }

    public NodeModule(final BitcoinProperties bitcoinProperties, final Environment environment) {
        final Thread mainThread = Thread.currentThread();

        _systemTime = new SystemTime();
        _masterInflater = new CoreInflater();

        { // Initialize the BlockCache...
            final String blockCacheDirectory = (bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME + "/blocks");
            final String pendingBlockCacheDirectory = (bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME + "/pending-blocks");
            _blockStore = new PendingBlockStoreCore(blockCacheDirectory, pendingBlockCacheDirectory, _masterInflater) {
                @Override
                protected void _deletePendingBlockData(final String blockPath) {
                    if (bitcoinProperties.isDeletePendingBlocksEnabled()) {
                        super._deletePendingBlockData(blockPath);
                    }
                }
            };
        }

        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        final int maxPeerCount = (bitcoinProperties.skipNetworking() ? 0 : bitcoinProperties.getMaxPeerCount());
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
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(
            databaseConnectionPool,
            _blockStore,
            _masterInflater,
            _bitcoinProperties.getMaxCachedUtxoCount(),
            _bitcoinProperties.getUtxoCachePurgePercent()
        );

        _banFilter = (bitcoinProperties.isBanFilterEnabled() ? new BanFilterCore(databaseManagerFactory) : new DisabledBanFilter());

        { // Ensure the data/cache directory exists...
            final String dataCacheDirectory = bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_CACHE_DIRECTORY_NAME;
            final File file = new File(dataCacheDirectory);
            if (! file.exists()) {
                final boolean wasSuccessful = file.mkdirs();
                if (! wasSuccessful) {
                    Logger.warn("Unable to create data cache directory: " + dataCacheDirectory);
                }
            }
        }

        { // Initialize MedianBlockTime...
            {
                MutableMedianBlockTime newMedianBlockTime = null;
                try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    newMedianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();
                }
                catch (final DatabaseException exception) {
                    Logger.error(exception);
                    BitcoinUtil.exitFailure();
                }
                _medianBlockTime = newMedianBlockTime;
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(databaseManagerFactory);
        final MemoryPoolEnquirer memoryPoolEnquirer = new MemoryPoolEnquirerHandler(databaseManagerFactory);

        final BlockInventoryMessageHandler blockInventoryMessageHandler;
        {
            blockInventoryMessageHandler = new BlockInventoryMessageHandler(databaseManagerFactory, synchronizationStatusHandler);
        }

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

        _requestDataHandler = new RequestDataHandler(databaseManagerFactory, _blockStore);
        _transactionWhitelist = RequestDataHandlerMonitor.wrap(_requestDataHandler);
        { // Initialize the monitor with transactions from the memory pool...
            Logger.info("[Loading RequestDataHandlerMonitor]");
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                for (final TransactionId transactionId : transactionDatabaseManager.getUnconfirmedTransactionIds()) {
                    final Sha256Hash transactionHash = transactionDatabaseManager.getTransactionHash(transactionId);
                    _transactionWhitelist.addTransactionHash(transactionHash);
                }
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }

        { // Initialize NodeInitializer...
            final SpvUnconfirmedTransactionsHandler spvUnconfirmedTransactionsHandler = new SpvUnconfirmedTransactionsHandler(databaseManagerFactory);

            final NodeInitializer.Context nodeInitializerContext = new NodeInitializer.Context();

            final Runnable newInventoryCallback = new Runnable() {
                @Override
                public void run() {
                    _transactionDownloader.wakeUp();
                }
            };

            nodeInitializerContext.synchronizationStatus = synchronizationStatusHandler;
            nodeInitializerContext.blockInventoryMessageHandler = blockInventoryMessageHandler;
            nodeInitializerContext.threadPoolFactory = nodeThreadPoolFactory;
            nodeInitializerContext.localNodeFeatures = localNodeFeatures;
            nodeInitializerContext.transactionsAnnouncementCallbackFactory = new TransactionInventoryMessageHandlerFactory(databaseManagerFactory, synchronizationStatusHandler, newInventoryCallback);
            nodeInitializerContext.queryBlocksCallback = new QueryBlocksHandler(databaseManagerFactory);
            nodeInitializerContext.queryBlockHeadersCallback = new QueryBlockHeadersHandler(databaseManagerFactory);
            nodeInitializerContext.requestDataCallback = _transactionWhitelist;
            nodeInitializerContext.requestSpvBlocksCallback = new RequestSpvBlockHandler(databaseManagerFactory, spvUnconfirmedTransactionsHandler);
            nodeInitializerContext.requestSlpTransactionsCallback = new RequestSlpTransactionsHandler(databaseManagerFactory);
            nodeInitializerContext.queryUnconfirmedTransactionsCallback = new QueryUnconfirmedTransactionsHandler(databaseManagerFactory);

            nodeInitializerContext.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
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

            nodeInitializerContext.binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;

            nodeInitializerContext.onNewBloomFilterCallback = new BitcoinNode.OnNewBloomFilterCallback() {
                @Override
                public void run(final BitcoinNode bitcoinNode) {
                    spvUnconfirmedTransactionsHandler.broadcastUnconfirmedTransactions(bitcoinNode);
                }
            };

            _nodeInitializer = new NodeInitializer(nodeInitializerContext);
        }

        { // Initialize NodeManager...
            final BitcoinNodeManager.Context context = new BitcoinNodeManager.Context();
            {
                context.databaseManagerFactory = databaseManagerFactory;
                context.nodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, nodeThreadPoolFactory, localNodeFeatures);
                context.maxNodeCount = maxPeerCount;
                context.networkTime = _mutableNetworkTime;
                context.nodeInitializer = _nodeInitializer;
                context.banFilter = _banFilter;
                context.memoryPoolEnquirer = memoryPoolEnquirer;
                context.synchronizationStatusHandler = synchronizationStatusHandler;
                context.threadPool = _mainThreadPool;
            }

            _bitcoinNodeManager = new BitcoinNodeManager(context);
        }

        // final NodeModuleContext context = new NodeModuleContext(_masterInflater, _blockStore, databaseManagerFactory, _bitcoinNodeManager, synchronizationStatusHandler, _medianBlockTime, _systemTime, _mainThreadPool, _mutableNetworkTime);
        final TransactionValidatorFactory transactionValidatorFactory = new TransactionValidatorFactory() {
            @Override
            public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
                return new TransactionValidatorCore(blockOutputs, transactionValidatorContext);
            }
        };

        { // Initialize the TransactionDownloader...
            _transactionDownloader = new TransactionDownloader(databaseManagerFactory, _bitcoinNodeManager);
        }

        { // Initialize the TransactionProcessor...
            final TransactionProcessorContext transactionProcessorContext = new TransactionProcessorContext(_masterInflater, databaseManagerFactory, _mutableNetworkTime, _systemTime, transactionValidatorFactory);
            _transactionProcessor = new TransactionProcessor(transactionProcessorContext);
        }

        final BlockProcessor blockProcessor;
        { // Initialize BlockSynchronizer...
            final BlockProcessor.Context blockProcessorContext = new BlockProcessorContext(_masterInflater, _masterInflater, _blockStore, databaseManagerFactory, _mutableNetworkTime, synchronizationStatusHandler, transactionValidatorFactory);
            blockProcessor = new BlockProcessor(blockProcessorContext);
            blockProcessor.setUtxoCommitFrequency(bitcoinProperties.getUtxoCacheCommitFrequency());
            blockProcessor.setMaxThreadCount(bitcoinProperties.getMaxThreadCount());
            blockProcessor.setTrustedBlockHeight(bitcoinProperties.getTrustedBlockHeight());
        }

        final BlockDownloadRequester blockDownloadRequester;
        { // Initialize the BlockHeaderDownloader/BlockDownloader...
            final BlockDownloaderContext blockDownloaderContext = new BlockDownloaderContext(_bitcoinNodeManager, _masterInflater, databaseManagerFactory, _mutableNetworkTime, _blockStore, synchronizationStatusHandler, _systemTime, _mainThreadPool);
            _blockDownloader = new BlockDownloader(blockDownloaderContext);
            blockDownloadRequester = new BlockDownloadRequesterCore(databaseManagerFactory, _blockDownloader, _bitcoinNodeManager);
            _blockHeaderDownloader = new BlockHeaderDownloader(blockDownloaderContext, blockDownloadRequester);
        }

        { // Initialize BlockchainBuilder...
            final PendingBlockLoaderContext pendingBlockLoaderContext = new PendingBlockLoaderContext(_masterInflater, databaseManagerFactory, _mainThreadPool);
            final PendingBlockLoader pendingBlockLoader = new PendingBlockLoader(pendingBlockLoaderContext, 8);
            final Long trustedBlockHeight = bitcoinProperties.getTrustedBlockHeight();
            pendingBlockLoader.setLoadUnspentOutputsAfterBlockHeight((trustedBlockHeight >= 0) ? trustedBlockHeight : null);

            final BlockDownloader.StatusMonitor blockDownloaderStatusMonitor = _blockDownloader.getStatusMonitor();
            final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(_masterInflater, databaseManagerFactory, _bitcoinNodeManager, _mainThreadPool);
            _blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, pendingBlockLoader, blockDownloaderStatusMonitor, blockDownloadRequester);
        }

        final Boolean indexModeIsEnabled = bitcoinProperties.isIndexingModeEnabled();
        if (! indexModeIsEnabled) {
            _slpTransactionProcessor = null;
            _blockchainIndexer = new DisabledBlockchainIndexer();
        }
        else {
            _slpTransactionProcessor = new SlpTransactionProcessor(databaseManagerFactory);

            final TransactionOutputIndexerContext transactionOutputIndexerContext = new LazyTransactionOutputIndexerContext(databaseManagerFactory);
            _blockchainIndexer = new BlockchainIndexer(transactionOutputIndexerContext);
            _blockchainIndexer.setOnSleepCallback(new Runnable() {
                @Override
                public void run() {
                    _slpTransactionProcessor.wakeUp();
                }
            });
        }

        _spentTransactionOutputsCleanupService = new SpentTransactionOutputsCleanupService(databaseManagerFactory);

        { // Set the synchronization elements to cascade to each component...
            _blockchainBuilder.setSynchronousNewBlockProcessedCallback(new BlockchainBuilder.NewBlockProcessedCallback() {
                @Override
                public void onNewBlock(final ProcessBlockResult processBlockResult) {
                    if (! processBlockResult.isValid) { return; }

                    _blockStore.storeBlock(processBlockResult.block, processBlockResult.blockHeight);

                    if (! processBlockResult.bestBlockchainHasChanged) {
                        _medianBlockTime.addBlock(processBlockResult.block);
                    }
                    else {
                        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                            final MedianBlockTimeWithBlocks newMedianBlockTime = blockHeaderDatabaseManager.initializeMedianBlockTime();
                            _medianBlockTime.setTo(newMedianBlockTime);
                        }
                        catch (final DatabaseException exception) {
                            Logger.error(exception);
                            _medianBlockTime.clear();
                        }
                    }
                }
            });

            _blockchainBuilder.setAsynchronousNewBlockProcessedCallback(new BlockchainBuilder.NewBlockProcessedCallback() {
                @Override
                public void onNewBlock(final ProcessBlockResult processBlockResult) {
                    if (! processBlockResult.isValid) { return; }

                    final Block block = processBlockResult.block;
                    final Long blockHeight = processBlockResult.blockHeight;

                    final Sha256Hash blockHash = block.getHash();

                    _blockchainIndexer.wakeUp();

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
                            if (Util.coalesce(_blockHeaderDownloader.getBlockHeight()) > Util.coalesce(headBlockHeight, -1L)) {
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
                    _blockchainIndexer.wakeUp();

                    for (final Transaction transaction : transactions) {
                        final Sha256Hash transactionHash = transaction.getHash();
                        if (_transactionWhitelist != null) { // Prevent penalizing nodes requesting this Transaction...
                            _transactionWhitelist.addTransactionHash(transactionHash);
                        }
                    }

                    _transactionRelay.relayTransactions(transactions);
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
                statisticsContainer.averageBlocksPerSecond = _blockchainBuilder.getAverageBlocksPerSecondContainer();
                statisticsContainer.averageTransactionsPerSecond = blockProcessor.getAverageTransactionsPerSecondContainer();
            }

            final NodeRpcHandler rpcSocketServerHandler = new NodeRpcHandler(statisticsContainer, _rpcThreadPool, _masterInflater);
            {
                final ShutdownHandler shutdownHandler = new ShutdownHandler(mainThread, _blockHeaderDownloader, _blockDownloader, _blockchainBuilder, synchronizationStatusHandler);
                final UtxoCacheHandler utxoCacheHandler = new UtxoCacheHandler(databaseManagerFactory);
                final NodeHandler nodeHandler = new NodeHandler(_bitcoinNodeManager, _nodeInitializer);
                final QueryAddressHandler queryAddressHandler = new QueryAddressHandler(databaseManagerFactory);
                final ThreadPoolInquisitor threadPoolInquisitor = new ThreadPoolInquisitor(_mainThreadPool);

                final TransactionInflaters transactionInflaters = _masterInflater;
                final RpcDataHandler rpcDataHandler = new RpcDataHandler(transactionInflaters, databaseManagerFactory, transactionValidatorFactory, _transactionDownloader, _blockchainBuilder, _blockDownloader, _mutableNetworkTime);

                final MetadataHandler metadataHandler = new MetadataHandler(databaseManagerFactory);
                final QueryBlockchainHandler queryBlockchainHandler = new QueryBlockchainHandler(databaseConnectionPool);

                final ServiceInquisitor serviceInquisitor = new ServiceInquisitor();
                for (final SleepyService sleepyService : new SleepyService[] { _blockchainIndexer, _slpTransactionProcessor, _transactionProcessor, _transactionDownloader, _blockchainBuilder, _blockDownloader, _blockHeaderDownloader, _spentTransactionOutputsCleanupService }) {
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
                rpcSocketServerHandler.setUtxoCacheHandler(utxoCacheHandler);
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
            _transactionRelay = new TransactionRelay(databaseManagerFactory, _bitcoinNodeManager, _nodeRpcHandler, shouldRelayInvalidSlpTransactions, 100L);
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
                    // HH    MM    SS      MS
                    final long analyzeEveryMilliseconds = (24L * 60L * 60L * 1000L); // 24 Hours
                    while (! _databaseMaintenanceThread.isInterrupted()) {
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
        final MilliTimer timer = new MilliTimer();
        timer.start();

        final DatabaseConnectionPool databaseConnectionPool = _environment.getDatabaseConnectionPool();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(
            databaseConnectionPool,
            _blockStore,
            _masterInflater,
            _bitcoinProperties.getMaxCachedUtxoCount(),
            _bitcoinProperties.getUtxoCachePurgePercent()
        );

        if (_bitcoinProperties.isBootstrapEnabled()) {
            Logger.info("[Bootstrapping Headers]");
            final HeadersBootstrapper headersBootstrapper = new HeadersBootstrapper(databaseManagerFactory);
            headersBootstrapper.run();
        }

        final Long headBlockHeight;
        {
            Long blockHeight = null;
            try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                blockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }
            headBlockHeight = Util.coalesce(blockHeight);
        }

        if (headBlockHeight < 2016L) { // Index previously downloaded blocks...
            Logger.info("[Indexing Pending Blocks]");
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

                final String pendingBlockDataDirectory = _blockStore.getPendingBlockDataDirectory();
                try (final DirectoryStream<Path> pendingBlockSubDirectories = Files.newDirectoryStream(Paths.get(pendingBlockDataDirectory))) {
                    for (final Path pendingBlockSubDirectory : pendingBlockSubDirectories) {
                        try (final DirectoryStream<Path> pendingBlockPaths = Files.newDirectoryStream(pendingBlockSubDirectory)) {
                            for (final Path pendingBlockPath : pendingBlockPaths) {
                                final File pendingBlockFile = pendingBlockPath.toFile();
                                final String blockHashString = pendingBlockFile.getName();
                                final Sha256Hash blockHash = Sha256Hash.fromHexString(blockHashString);
                                if (blockHash != null) {
                                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                                    if (blockId != null) {
                                        final Boolean blockHasBeenProcessed = blockDatabaseManager.hasTransactions(blockHash);
                                        if (blockHasBeenProcessed) { continue; }
                                    }

                                    final BlockId parentBlockId = blockHeaderDatabaseManager.getAncestorBlockId(blockId, 1);
                                    final Sha256Hash parentBlockHash = blockHeaderDatabaseManager.getBlockHash(parentBlockId);
                                    pendingBlockDatabaseManager.insertBlockHash(blockHash, parentBlockHash, true);
                                    Logger.debug("Indexed Existing pending Block: " + blockHash);
                                }
                            }
                        }
                    }
                }
            }
            catch (final Exception exception) {
                Logger.debug(exception);
            }
        }

        { // Validate the UTXO set is up to date...
            Logger.info("[Checking UTXO Set]");
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
                final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

                final Long utxoCommitFrequency = _bitcoinProperties.getUtxoCacheCommitFrequency();
                final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, databaseConnectionPool, utxoCommitFrequency);

                final BlockLoader blockLoader = new BlockLoader(headBlockchainSegmentId, 128, databaseManagerFactory, _mainThreadPool);

                if (_rebuildUtxoSet) {
                    unspentTransactionOutputManager.rebuildUtxoSetFromGenesisBlock(blockLoader);
                }
                else {
                    unspentTransactionOutputManager.buildUtxoSet(blockLoader);
                }
            }
            catch (final DatabaseException exception) {
                Logger.error(exception);
            }
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

        Logger.info("[Starting Transaction Relay]");
        _transactionRelay.start();

        if (! (_blockchainIndexer instanceof DisabledBlockchainIndexer)) {
            Logger.info("[Starting Address Processor]");
            _blockchainIndexer.start();
        }

        if (_slpTransactionProcessor != null) {
            Logger.info("[Started SlpTransaction Processor]");
            _slpTransactionProcessor.start();
        }

        if (_spentTransactionOutputsCleanupService != null) {
            Logger.info("[Started Spent UTXO Cleanup Service]");
            _spentTransactionOutputsCleanupService.start();
        }

        if (! _bitcoinProperties.skipNetworking()) {
            Logger.info("[Connecting To Peers]");
            _connectToAdditionalNodes();
        }

        _uptimeTimer.start();
        _databaseMaintenanceThread.start();

        final Runtime runtime = Runtime.getRuntime();
        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(60000); } catch (final Exception exception) { break; }
            runtime.gc();

            // Wakeup the Spent UTXO Cleanup Service...
            if (_spentTransactionOutputsCleanupService != null) {
                _spentTransactionOutputsCleanupService.wakeUp();
            }

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
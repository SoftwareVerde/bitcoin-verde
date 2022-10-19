package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.IndexerCache;
import com.softwareverde.bitcoin.context.TransactionOutputIndexerContext;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.core.BlockHeaderDownloaderContext;
import com.softwareverde.bitcoin.context.core.BlockProcessorContext;
import com.softwareverde.bitcoin.context.core.BlockchainBuilderContext;
import com.softwareverde.bitcoin.context.core.DoubleSpendProofProcessorContext;
import com.softwareverde.bitcoin.context.core.TransactionProcessorContext;
import com.softwareverde.bitcoin.context.lazy.LazyTransactionOutputIndexerContext;
import com.softwareverde.bitcoin.inflater.BlockHeaderInflaters;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.configuration.NodeProperties;
import com.softwareverde.bitcoin.server.configuration.TestNetCheckpointConfiguration;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.DatabaseMaintainer;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.memory.LowMemoryMonitor;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.callback.NewBlockHeadersAvailableCallbackBuilder;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CacheLoadingMethod;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UndoLogDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputJvmManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.server.module.node.handler.BlockInventoryMessageHandler;
import com.softwareverde.bitcoin.server.module.node.handler.MemoryPoolEnquirerHandler;
import com.softwareverde.bitcoin.server.module.node.handler.QueryUtxoCommitmentsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.RequestDataHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SpvUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestBlockHashesHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestSpvBlocksHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.QueryUnconfirmedTransactionsHandler;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.TransactionAnnouncementHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofAnnouncementHandlerFactory;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofDatabase;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofProcessor;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.dsproof.DoubleSpendProofStore;
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
import com.softwareverde.bitcoin.server.module.node.rpc.handler.RpcIndexerHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.RpcStatisticsHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ServiceInquisitor;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ShutdownHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.ThreadPoolInquisitor;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.UtxoCacheHandler;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStore;
import com.softwareverde.bitcoin.server.module.node.store.UtxoCommitmentStoreCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.DisabledBlockchainIndexer;
import com.softwareverde.bitcoin.server.module.node.sync.SlpTransactionProcessor;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloadPlannerCore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockPruner;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.FullNodeHeadersBootstrapper;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.HeadersBootstrapper;
import com.softwareverde.bitcoin.server.module.node.sync.bootstrap.UtxoCommitmentDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeBlockInventoryTracker;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeHeadBlockFinder;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.server.module.node.utxo.UtxoCommitmentGenerator;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.properties.DatabasePropertiesStore;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofWithTransactions;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPoolFactory;
import com.softwareverde.concurrent.threadpool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.embedded.EmbeddedMysqlDatabase;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.network.socket.BinarySocketServer;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class NodeModule {
    protected final Boolean _rebuildUtxoSet = false;
    protected final Boolean _utxoCacheLoadFileIsEnabled;

    protected final SystemTime _systemTime;
    protected final BitcoinProperties _bitcoinProperties;
    protected final Environment _environment;
    protected final DatabasePropertiesStore _propertiesStore;
    protected final PendingBlockStoreCore _blockStore;
    protected final CheckpointConfiguration _checkpointConfiguration;
    protected final MasterInflater _masterInflater;
    protected final UpgradeSchedule _upgradeSchedule;

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
    protected final RequestDataHandler _requestDataHandler;
    protected final RequestDataHandlerMonitor _transactionWhitelist;
    protected final BlockPruner _blockPruner;
    protected final UtxoCommitmentStore _utxoCommitmentStore;
    protected final UtxoCommitmentGenerator _utxoCommitmentGenerator;
    protected final UtxoCommitmentDownloader _utxoCommitmentDownloader;
    protected final List<SleepyService> _allServices;

    protected final BitcoinNodeFactory _bitcoinNodeFactory;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
    protected final BanFilter _banFilter;
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final CachedThreadPool _generalThreadPool;
    protected final CachedThreadPool _networkThreadPool;
    protected final CachedThreadPool _blockProcessingThreadPool;
    protected final CachedThreadPool _rpcThreadPool;

    protected final MilliTimer _uptimeTimer = new MilliTimer();
    protected final Thread _databaseMaintenanceThread;

    protected final LowMemoryMonitor _lowMemoryMonitor;

    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected Long _getUtxoCommitFrequency() {
        final Long utxoCommitProperty = _bitcoinProperties.getUtxoCacheCommitFrequency();
        return (_bitcoinProperties.isPruningModeEnabled() ? Math.min(UndoLogDatabaseManager.MAX_REORG_DEPTH, utxoCommitProperty) : utxoCommitProperty);
    }

    protected void _shutdown() {
        synchronized (_isShuttingDown) {
            if (! _isShuttingDown.compareAndSet(false, true)) {
                Logger.info("[Awaiting Shutdown Completion]");
                try { _isShuttingDown.wait(30000); } catch (final Exception exception) { }
                return;
            }
        }

        _utxoCommitmentDownloader.shutdown();

        Logger.info("[Stopping Request Handler]");
        _requestDataHandler.shutdown();

        Logger.info("[Stopping Database Maintenance Thread]");
        _databaseMaintenanceThread.interrupt();

        if (_slpTransactionProcessor != null) {
            Logger.info("[Stopping SlpTransaction Processor]");
            _slpTransactionProcessor.stop();
        }

        if (! (_blockchainIndexer instanceof DisabledBlockchainIndexer)) {
            Logger.info("[Stopping Blockchain Indexer]");
            _blockchainIndexer.stop();
        }

        Logger.info("[Stopping UTXO Commitment Generator]");
        _utxoCommitmentGenerator.stop();

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

        Logger.info("[Stopping Socket Server]");
        _socketServer.stop();

        if (_blockPruner != null) {
            Logger.info("[Stopping BlockPruner]");
            _blockPruner.stop();
        }

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            if (unspentTransactionOutputDatabaseManager instanceof UnspentTransactionOutputJvmManager) {
                Logger.info("[Saving UTXO Cache]");

                final UnspentTransactionOutputJvmManager unspentTransactionOutputJvmManager = (UnspentTransactionOutputJvmManager) unspentTransactionOutputDatabaseManager;
                if (_utxoCacheLoadFileIsEnabled) {
                    unspentTransactionOutputJvmManager.writeUtxoCacheLoadFile();
                }
                else {
                    unspentTransactionOutputJvmManager.deleteUtxoCacheLoadFile();
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        Logger.info("[Committing UTXO Set]");
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
            final MilliTimer utxoCommitTimer = new MilliTimer();
            utxoCommitTimer.start();
            Logger.info("Committing UTXO set.");
            unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_databaseManagerFactory, CommitAsyncMode.BLOCK_UNTIL_COMPLETE);
            utxoCommitTimer.stop();
            Logger.debug("Commit Timer: " + utxoCommitTimer.getMillisecondsElapsed() + "ms.");
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        Logger.info("[Shutting Down Thread Server]");
        _networkThreadPool.stop();
        _blockProcessingThreadPool.stop();
        _generalThreadPool.stop();
        _rpcThreadPool.stop();

        if (_jsonRpcSocketServer != null) {
            Logger.info("[Shutting Down RPC Server]");
            _jsonRpcSocketServer.stop();
        }

        Logger.info("[Shutting Down Database]");
        final DatabaseConnectionFactory databaseConnectionFactory = _environment.getDatabaseConnectionFactory();
        try {
            databaseConnectionFactory.close();
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
        final Database database = _environment.getDatabase();
        if (database instanceof EmbeddedMysqlDatabase) {
            try {
                ((EmbeddedMysqlDatabase) database).stop();
            }
            catch (final Exception exception) { }
        }

        try { _databaseMaintenanceThread.join(30000L); } catch (final InterruptedException exception) { }

        _propertiesStore.stop();

        Logger.flush();

        synchronized (_isShuttingDown) {
            _isShuttingDown.notifyAll();
        }

        Logger.info("[Exiting]");
    }

    public NodeModule(final BitcoinProperties bitcoinProperties, final Environment environment) {
        final Thread mainThread = Thread.currentThread();
        mainThread.setName("Bitcoin Verde - Main");
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

        _systemTime = new SystemTime();
        _masterInflater = new CoreInflater();

        { // Upgrade Schedule
            if (bitcoinProperties.isTestNet()) {
                final NetworkType networkType = bitcoinProperties.getNetworkType();
                switch (networkType) {
                    case TEST_NET4: {
                        _upgradeSchedule = new TestNet4UpgradeSchedule();
                    } break;
                    case CHIP_NET: {
                        _upgradeSchedule = new ChipNetUpgradeSchedule();
                    } break;
                    default: {
                        _upgradeSchedule = new TestNetUpgradeSchedule();
                    }
                }
            }
            else {
                _upgradeSchedule = new CoreUpgradeSchedule();
            }
        }

        { // Initialize the BlockStore...
            final String dataDirectory = bitcoinProperties.getDataDirectory();
            final BlockHeaderInflaters blockHeaderInflaters = _masterInflater;
            final BlockInflaters blockInflaters = _masterInflater;
            _blockStore = new PendingBlockStoreCore(dataDirectory, blockHeaderInflaters, blockInflaters) {
                @Override
                protected void _deletePendingBlockData(final String blockPath) {
                    if (bitcoinProperties.isDeletePendingBlocksEnabled()) {
                        super._deletePendingBlockData(blockPath);
                    }
                }
            };
        }

        { // Initialize the UtxoCommitmentStore...
            final String dataDirectory = bitcoinProperties.getDataDirectory();
            _utxoCommitmentStore = new UtxoCommitmentStoreCore(dataDirectory);

            final String utxoCommitmentOutputDirectory = _utxoCommitmentStore.getUtxoDataDirectory();
            final File utxoCommitmentOutputDirectoryFile = new File(utxoCommitmentOutputDirectory);
            if (! utxoCommitmentOutputDirectoryFile.exists()) {
                utxoCommitmentOutputDirectoryFile.mkdirs();
            }
        }

        { // Block Checkpoints
            final Boolean isTestNet = bitcoinProperties.isTestNet();
            if (isTestNet) {
                _checkpointConfiguration = new TestNetCheckpointConfiguration();
            }
            else {
                _checkpointConfiguration = new CheckpointConfiguration();
            }
        }

        _bitcoinProperties = bitcoinProperties;
        _environment = environment;

        _utxoCacheLoadFileIsEnabled = true;

        final int minPeerCount = (bitcoinProperties.shouldSkipNetworking() ? 0 : bitcoinProperties.getMinPeerCount());
        final BitcoinBinaryPacketFormat binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;

        final int maxPeerCount = (bitcoinProperties.shouldSkipNetworking() ? 0 : bitcoinProperties.getMaxPeerCount());
        _generalThreadPool = new CachedThreadPool(256, 60000L);
        _networkThreadPool = new CachedThreadPool((16 + (maxPeerCount * 8)), 60000L);
        _blockProcessingThreadPool = new CachedThreadPool(256, 60000L);
        _rpcThreadPool = new CachedThreadPool(32, 60000L);

        final Database database = _environment.getDatabase();

        { // Initialize the property store...
            // NOTE: The DatabasePropertiesStore should not use the core DatabaseConnectionFactory;
            //  it is important that its values are written to disk even during a partially-unclean shutdown.
            final DatabaseConnectionFactory databaseConnectionFactory = _environment.newDatabaseConnectionFactory();
            _propertiesStore = new DatabasePropertiesStore(databaseConnectionFactory);
        }

        final DatabaseConnectionFactory databaseConnectionFactory = _environment.getDatabaseConnectionFactory();

        final FullNodeDatabaseManagerFactory databaseManagerFactory = new FullNodeDatabaseManagerFactory(
            databaseConnectionFactory,
            database.getMaxQueryBatchSize(),
            _propertiesStore,
            _blockStore,
            _utxoCommitmentStore,
            _masterInflater,
            _checkpointConfiguration,
            _bitcoinProperties.getMaxCachedUtxoCount(),
            _bitcoinProperties.getUtxoCachePurgePercent()
        );
        _databaseManagerFactory = databaseManagerFactory;

        _banFilter = (bitcoinProperties.isBanFilterEnabled() ? new BanFilterCore(databaseManagerFactory) : new DisabledBanFilter());

        { // Ensure the data/cache directory exists...
            final String dataCacheDirectory = bitcoinProperties.getDataDirectory() + "/" + BitcoinProperties.DATA_DIRECTORY_NAME;
            final File file = new File(dataCacheDirectory);
            if (! file.exists()) {
                final boolean wasSuccessful = file.mkdirs();
                if (! wasSuccessful) {
                    Logger.warn("Unable to create data cache directory: " + dataCacheDirectory);
                }
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(databaseManagerFactory);
        final MemoryPoolEnquirer memoryPoolEnquirer = new MemoryPoolEnquirerHandler(databaseManagerFactory);

        final BlockInventoryMessageHandler blockInventoryMessageHandler = new BlockInventoryMessageHandler(databaseManagerFactory, synchronizationStatusHandler, _banFilter);

        final ThreadPoolFactory nodeThreadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                final ThreadPoolThrottle threadPoolThrottle = new ThreadPoolThrottle(bitcoinProperties.getMaxMessagesPerSecond(), _networkThreadPool);
                threadPoolThrottle.start();
                return threadPoolThrottle;
            }
        };

        final boolean indexModeIsEnabled = bitcoinProperties.isIndexingModeEnabled();
        final boolean pruningModeIsEnabled = bitcoinProperties.isPruningModeEnabled();
        final boolean fastSyncIsEnabled = bitcoinProperties.isFastSyncEnabled();
        final Long fastSyncTimeout = _bitcoinProperties.getFastSyncTimeoutMs();

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);

                nodeFeatures.enableFeature(NodeFeatures.Feature.EXTENDED_DOUBLE_SPEND_PROOFS_ENABLED); // BitcoinVerde 2021-04-27
                nodeFeatures.enableFeature(NodeFeatures.Feature.UTXO_COMMITMENTS_ENABLED); // BitcoinVerde 2021-05-26

                if (! pruningModeIsEnabled) {
                    nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                }

                nodeFeatures.enableFeature(NodeFeatures.Feature.MINIMUM_OF_TWO_DAYS_BLOCKCHAIN_ENABLED);

                return nodeFeatures;
            }
        };

        final DoubleSpendProofStore doubleSpendProofStore;
        final DoubleSpendProofProcessor doubleSpendProofProcessor;
        {
            final int maxCacheItemCount = 256;
            if (bitcoinProperties.isIndexingModeEnabled()) {
                doubleSpendProofStore = new DoubleSpendProofDatabase(maxCacheItemCount, databaseManagerFactory);
            }
            else {
                doubleSpendProofStore = new DoubleSpendProofStore(maxCacheItemCount);
            }

            final DoubleSpendProofProcessorContext doubleSpendProofProcessorContext = new DoubleSpendProofProcessorContext(databaseManagerFactory, _upgradeSchedule);
            doubleSpendProofProcessor = new DoubleSpendProofProcessor(doubleSpendProofStore, doubleSpendProofProcessorContext);
        }

        _requestDataHandler = new RequestDataHandler(databaseManagerFactory, doubleSpendProofStore);
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

        final NodeInitializer nodeInitializer;
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
            nodeInitializerContext.transactionsAnnouncementHandlerFactory = new TransactionAnnouncementHandlerFactory(databaseManagerFactory, synchronizationStatusHandler, newInventoryCallback);
            nodeInitializerContext.doubleSpendProofAnnouncementHandlerFactory = new DoubleSpendProofAnnouncementHandlerFactory(doubleSpendProofProcessor, doubleSpendProofStore,
                new DoubleSpendProofAnnouncementHandlerFactory.BitcoinNodeCollector() {
                    @Override
                    public List<BitcoinNode> getConnectedNodes() {
                        return _bitcoinNodeManager.getNodes();
                    }
                },
                new DoubleSpendProofAnnouncementHandlerFactory.DoubleSpendProofCallback() {
                    @Override
                    public void onNewDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
                        final NodeRpcHandler nodeRpcHandler = _nodeRpcHandler;
                        if (nodeRpcHandler != null) {
                            nodeRpcHandler.onNewDoubleSpendProof(doubleSpendProof);
                        }
                    }
                }
            );
            nodeInitializerContext.requestBlockHashesHandler = new RequestBlockHashesHandler(databaseManagerFactory);
            nodeInitializerContext.requestBlockHeadersHandler = new RequestBlockHeadersHandler(databaseManagerFactory);
            nodeInitializerContext.requestDataHandler = _transactionWhitelist;
            nodeInitializerContext.requestSpvBlocksHandler = new RequestSpvBlocksHandler(databaseManagerFactory, spvUnconfirmedTransactionsHandler);
            nodeInitializerContext.queryUtxoCommitmentsHandler = new QueryUtxoCommitmentsHandler(databaseManagerFactory);
            nodeInitializerContext.requestUnconfirmedTransactionsHandler = new QueryUnconfirmedTransactionsHandler(databaseManagerFactory);

            nodeInitializerContext.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<>(connectedNodes.getCount());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            nodeInitializerContext.binaryPacketFormat = binaryPacketFormat;

            nodeInitializerContext.newBloomFilterHandler = new BitcoinNode.NewBloomFilterHandler() {
                @Override
                public void run(final BitcoinNode bitcoinNode) {
                    spvUnconfirmedTransactionsHandler.broadcastUnconfirmedTransactions(bitcoinNode);
                }
            };

            nodeInitializerContext.unsolicitedBlockReceivedCallback = new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                    final Sha256Hash blockHash = block.getHash();

                    final Boolean blockWasAlreadyDownloaded = _blockStore.pendingBlockExists(blockHash);
                    if (blockWasAlreadyDownloaded) { return; }

                    boolean blockHeaderIsKnown = false;
                    try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        blockHeaderIsKnown = (blockId != null);
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                    }

                    if (blockHeaderIsKnown) {
                        Logger.debug("Storing unsolicited Block: " + blockHash);
                        _blockDownloader.submitBlock(block);
                    }
                }
            };

            nodeInitializer = new NodeInitializer(nodeInitializerContext);
        }

        _bitcoinNodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, nodeThreadPoolFactory, localNodeFeatures);

        { // Initialize DifficultyCalculatorFactory...
            final Boolean isTestNet = bitcoinProperties.isTestNet();
            if (isTestNet) {
                _difficultyCalculatorFactory = new DifficultyCalculatorFactory() {
                    @Override
                    public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
                        return new TestNetDifficultyCalculator(context);
                    }
                };
            }
            else {
                _difficultyCalculatorFactory = new DifficultyCalculatorFactory() {
                    @Override
                    public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
                        return new DifficultyCalculator(context);
                    }
                };
            }
        }

        { // Initialize NodeManager...
            final BitcoinNodeManager.Context context = new BitcoinNodeManager.Context();
            {
                context.systemTime = _systemTime;
                context.databaseManagerFactory = databaseManagerFactory;
                context.nodeFactory = _bitcoinNodeFactory;
                context.minNodeCount = minPeerCount;
                context.maxNodeCount = maxPeerCount;
                context.networkTime = _mutableNetworkTime;
                context.nodeInitializer = nodeInitializer;
                context.banFilter = _banFilter;
                context.memoryPoolEnquirer = memoryPoolEnquirer;
                context.synchronizationStatusHandler = synchronizationStatusHandler;
                context.threadPool = _generalThreadPool;
            }

            _bitcoinNodeManager = new BitcoinNodeManager(context);
            _bitcoinNodeManager.setDefaultExternalPort(bitcoinProperties.getBitcoinPort());

            final BitcoinNodeHeadBlockFinder bitcoinNodeHeadBlockFinder = new BitcoinNodeHeadBlockFinder(databaseManagerFactory, _generalThreadPool, _banFilter);
            _bitcoinNodeManager.setNewNodeHandshakedCallback(new BitcoinNodeManager.NewNodeCallback() {
                @Override
                public void onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
                    bitcoinNodeHeadBlockFinder.determineHeadBlock(bitcoinNode, new BitcoinNodeHeadBlockFinder.Callback() {
                        @Override
                        public void onHeadBlockDetermined(final Long blockHeight, final Sha256Hash blockHash) {
                            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                                final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
                                nodeDatabaseManager.updateBlockInventory(bitcoinNode, blockHeight, blockHash);
                            }
                            catch (final DatabaseException databaseException) {
                                Logger.debug(databaseException);
                            }

                            _blockHeaderDownloader.wakeUp();
                            _blockDownloader.wakeUp();
                        }

                        @Override
                        public void onFailure() {
                            _blockHeaderDownloader.wakeUp();
                            _blockDownloader.wakeUp();
                        }
                    });
                }
            });
        }

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
            final TransactionProcessorContext transactionProcessorContext = new TransactionProcessorContext(_masterInflater, databaseManagerFactory, _mutableNetworkTime, _systemTime, transactionValidatorFactory, _upgradeSchedule, _generalThreadPool);
            _transactionProcessor = new TransactionProcessor(transactionProcessorContext);
        }

        final BlockProcessor blockProcessor;
        { // Initialize BlockProcessor...
            final Long utxoCommitFrequency = _getUtxoCommitFrequency();
            final Integer maxThreadCount = bitcoinProperties.getMaxThreadCount();
            final Long trustedBlockHeight = bitcoinProperties.getTrustedBlockHeight();

            final BlockProcessor.Context blockProcessorContext = new BlockProcessorContext(_masterInflater, _masterInflater, _blockStore, databaseManagerFactory, _mutableNetworkTime, synchronizationStatusHandler, _difficultyCalculatorFactory, transactionValidatorFactory, _upgradeSchedule);
            blockProcessor = new BlockProcessor(blockProcessorContext);
            blockProcessor.setUtxoCommitFrequency(utxoCommitFrequency);
            blockProcessor.setMaxThreadCount(maxThreadCount);
            blockProcessor.setTrustedBlockHeight(trustedBlockHeight);
            blockProcessor.enableUndoLog(pruningModeIsEnabled);
        }

        if (pruningModeIsEnabled) {
            _blockPruner = new BlockPruner(databaseManagerFactory, _blockStore, indexModeIsEnabled, new BlockPruner.RequiredBlockChecker() {
                @Override
                public Boolean isBlockRequired(final Long blockHeight, final Sha256Hash blockHash) {
                    final boolean utxoCommitmentGeneratorRequiresBlock = _utxoCommitmentGenerator.requiresBlock(blockHeight, blockHash);
                    if (utxoCommitmentGeneratorRequiresBlock) { return true; }

                    final long pruneBlockHeightLag = UndoLogDatabaseManager.MAX_REORG_DEPTH;
                    final Long currentBlockHeight = blockProcessor.getCurrentBlockHeight();
                    return (blockHeight < (currentBlockHeight - pruneBlockHeightLag));
                }
            });
        }
        else {
            _blockPruner = null;
        }

        { // Initialize the BlockHeaderDownloader/BlockDownloader...
            final BitcoinNodeBlockInventoryTracker blockInventoryTracker = new BitcoinNodeBlockInventoryTracker();

            final BlockDownloader.BitcoinNodeCollector bitcoinNodeCollector = new BlockDownloader.BitcoinNodeCollector() {
                @Override
                public List<BitcoinNode> getBitcoinNodes() {
                    return _bitcoinNodeManager.getPreferredNodes();
                }
            };

            final BlockDownloadPlannerCore blockDownloadPlanner = new BlockDownloadPlannerCore(databaseManagerFactory, _blockStore);
            if (pruningModeIsEnabled) {
                blockDownloadPlanner.setMaxDownloadAheadDepth(256);
                blockDownloadPlanner.enableAlternateChainDownload(false);
            }

            _blockDownloader = new BlockDownloader(_blockStore, bitcoinNodeCollector, blockInventoryTracker, blockDownloadPlanner, _generalThreadPool);

            final int maxConcurrentDownloadCount = bitcoinProperties.getMinPeerCount();
            final int maxConcurrentDownloadCountPerNode = 2;
            _blockDownloader.setMaxConcurrentDownloadCount(maxConcurrentDownloadCount);
            _blockDownloader.setMaxConcurrentDownloadCountPerNode(maxConcurrentDownloadCountPerNode);

            final BlockHeaderDownloaderContext blockHeaderDownloaderContext = new BlockHeaderDownloaderContext(_bitcoinNodeManager, databaseManagerFactory, _difficultyCalculatorFactory, _mutableNetworkTime, _systemTime, _generalThreadPool, _upgradeSchedule);

            _blockHeaderDownloader = new BlockHeaderDownloader(blockHeaderDownloaderContext, blockInventoryTracker);
        }

        { // Initialize BlockchainBuilder...
            final BlockDownloader.StatusMonitor blockDownloaderStatusMonitor = _blockDownloader.getStatusMonitor();
            final BlockchainBuilderContext blockchainBuilderContext = new BlockchainBuilderContext(_masterInflater, databaseManagerFactory, _bitcoinNodeManager, _systemTime, _blockProcessingThreadPool);
            _blockchainBuilder = new BlockchainBuilder(blockchainBuilderContext, blockProcessor, _blockStore, blockDownloaderStatusMonitor);
            _blockchainBuilder.setUnavailableBlockCallback(new BlockchainBuilder.UnavailableBlockCallback() {
                @Override
                public void onRequiredBlockUnavailable(final Sha256Hash blockHash, final Long blockHeight) {
                    try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
                        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        if (blockId == null) { return; }

                        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                        final Boolean blockIsOnMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);

                        if (blockIsOnMainChain) {
                            Logger.debug("Requesting Block: " + blockHash + ":" + blockHeight + " (paused=" + _blockDownloader.isPaused() +")");
                            _blockDownloader.requestBlock(blockHash, blockHeight, null);
                        }
                    }
                    catch (final DatabaseException exception) {
                        Logger.debug(exception);
                    }
                }
            });
        }

        if (! indexModeIsEnabled) {
            _slpTransactionProcessor = null;
            _blockchainIndexer = new DisabledBlockchainIndexer();
        }
        else {
            _slpTransactionProcessor = new SlpTransactionProcessor(databaseManagerFactory);

            final Integer threadCount = bitcoinProperties.getMaxThreadCount();
            final IndexerCache indexerCache = new IndexerCache(64);
            final TransactionOutputIndexerContext transactionOutputIndexerContext = new LazyTransactionOutputIndexerContext(databaseManagerFactory, indexerCache);
            _blockchainIndexer = new BlockchainIndexer(transactionOutputIndexerContext, threadCount);
            _blockchainIndexer.setOnSleepCallback(new Runnable() {
                @Override
                public void run() {
                    _slpTransactionProcessor.wakeUp();
                }
            });
        }

        final boolean fastSyncHasCompleted;
        final long maxCommitmentBlockHeight;
        final long minCommitmentBlockHeight;
        {
            long maxBlockHeight = 0L;
            long minBlockHeight = Long.MAX_VALUE;
            for (final UtxoCommitmentMetadata utxoCommitments : BitcoinConstants.getUtxoCommitments()) {
                if (utxoCommitments.blockHeight > maxBlockHeight) {
                    maxBlockHeight = utxoCommitments.blockHeight;
                }
                if (utxoCommitments.blockHeight < minBlockHeight) {
                    minBlockHeight = utxoCommitments.blockHeight;
                }
            }
            maxCommitmentBlockHeight = maxBlockHeight;
            minCommitmentBlockHeight = minBlockHeight;

            Long headBlockHeight = null;
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }

            fastSyncHasCompleted = (Util.coalesce(headBlockHeight) >= minCommitmentBlockHeight);
        }

        _utxoCommitmentGenerator = new UtxoCommitmentGenerator(databaseManagerFactory, _utxoCommitmentStore.getUtxoDataDirectory());
        _utxoCommitmentDownloader = new UtxoCommitmentDownloader(databaseManagerFactory, _bitcoinNodeManager, _utxoCommitmentStore, _blockPruner, fastSyncTimeout);

        { // Set the synchronization elements to cascade to each component...
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

                    _blockDownloader.wakeUp();

                    if (blockHeight >= blockHeaderDownloaderBlockHeight) {
                        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
                            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

                            final BlockId newBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                            final BlockchainSegmentId newBlockBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newBlockId);
                            final Boolean newBlockIsOnMainChain = blockchainDatabaseManager.areBlockchainSegmentsConnected(newBlockBlockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);

                            if (newBlockIsOnMainChain) {
                                synchronizationStatusHandler.setState(State.ONLINE);
                            }

                            { // Broadcast new Block...
                                final HashMap<NodeId, BitcoinNode> bitcoinNodeMap = new HashMap<>();
                                final List<NodeId> connectedNodeIds;
                                {
                                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                                    final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<>(connectedNodes.getCount());
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

                                    if (bitcoinNode.isNewBlocksViaHeadersEnabled()) {
                                        bitcoinNode.transmitBlockHeader(block);
                                    }
                                    else {
                                        bitcoinNode.transmitBlockHashes(new ImmutableList<>(blockHash));
                                    }
                                }
                            }
                        }
                        catch (final DatabaseException exception) {
                            Logger.warn(exception);
                        }
                    }

                    final NodeRpcHandler nodeRpcHandler = _nodeRpcHandler;
                    if (nodeRpcHandler != null) {
                        nodeRpcHandler.onNewBlock(block);
                    }

                    if (pruningModeIsEnabled) { // Handle Block Pruning...
                        _blockPruner.wakeUp();
                    }

                    _utxoCommitmentGenerator.wakeUp();
                }
            });

            final NewBlockHeadersAvailableCallbackBuilder blockHeadersAvailableCallbackBuilder = new NewBlockHeadersAvailableCallbackBuilder(databaseManagerFactory, _blockHeaderDownloader, _blockDownloader, synchronizationStatusHandler, blockInventoryMessageHandler);
            if (fastSyncIsEnabled && (! fastSyncHasCompleted) ) {
                blockHeadersAvailableCallbackBuilder.enableFastSync(maxCommitmentBlockHeight, indexModeIsEnabled, _utxoCommitmentDownloader, _blockchainIndexer, new Runnable() {
                    @Override
                    public void run() {
                        Logger.debug("Resuming paused services...");
                        _blockDownloader.resume();
                        _utxoCommitmentGenerator.resume();
                        _blockchainIndexer.resume();

                        _blockDownloader.wakeUp();
                        _utxoCommitmentGenerator.wakeUp();
                        _blockchainIndexer.wakeUp();
                    }
                });
            }
            _blockHeaderDownloader.setNewBlockHeaderAvailableCallback(blockHeadersAvailableCallbackBuilder.build());

            _blockDownloader.setBlockDownloadedCallback(new BlockDownloader.BlockDownloadCallback() {
                @Override
                public void onBlockDownloaded(final Block block, final BitcoinNode bitcoinNode) {
                    _blockchainBuilder.wakeUp();
                }
            });

            blockInventoryMessageHandler.setNewInventoryReceivedCallback(new BlockInventoryMessageHandler.NewInventoryReceivedCallback() {
                @Override
                public void onNewBlockHashesReceived(final List<Sha256Hash> blockHashes) {
                    _blockDownloader.wakeUp();
                }

                @Override
                public void onNewBlockHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                    _blockHeaderDownloader.onNewBlockHeaders(bitcoinNode, blockHeaders);
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
                    if (indexModeIsEnabled) {
                        _blockchainIndexer.wakeUp();
                    }

                    for (final Transaction transaction : transactions) {
                        final Sha256Hash transactionHash = transaction.getHash();
                        { // Prevent penalizing nodes requesting this Transaction...
                            _transactionWhitelist.addTransactionHash(transactionHash);
                        }
                    }

                    _transactionRelay.relayTransactions(transactions);

                    final List<DoubleSpendProof> doubleSpendProofsToRetry = doubleSpendProofStore.getTriggeredPendingDoubleSpendProof(transactions);
                    for (final DoubleSpendProof doubleSpendProof : doubleSpendProofsToRetry) {
                        final Boolean isValidAndUnseen = doubleSpendProofProcessor.processDoubleSpendProof(doubleSpendProof);
                        if (! isValidAndUnseen) { return; }

                        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
                        Logger.debug("DoubleSpendProof validated: " + doubleSpendProofHash);

                        final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getNodes();
                        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
                            bitcoinNode.transmitDoubleSpendProofHash(doubleSpendProofHash);
                        }

                        final NodeRpcHandler nodeRpcHandler = _nodeRpcHandler;
                        if (nodeRpcHandler != null) {
                            nodeRpcHandler.onNewDoubleSpendProof(doubleSpendProof);
                        }
                    }
                }
            });

            _transactionProcessor.setNewDoubleSpendProofCallback(new TransactionProcessor.DoubleSpendProofCallback() {
                @Override
                public void onNewDoubleSpendProof(final DoubleSpendProofWithTransactions doubleSpendProof) {
                    doubleSpendProofStore.storeDoubleSpendProof(doubleSpendProof);
                    _bitcoinNodeManager.broadcastDoubleSpendProof(doubleSpendProof);
                }
            });
        }

        _socketServer = new BinarySocketServer(bitcoinProperties.getBitcoinPort(), binaryPacketFormat, _generalThreadPool);
        _socketServer.setSocketConnectedCallback(new BinarySocketServer.SocketConnectedCallback() {
            @Override
            public void run(final BinarySocket binarySocket) {
                final Ip ip = binarySocket.getIp();

                final Boolean isBanned = _banFilter.isIpBanned(ip);
                if (isBanned) {
                    Logger.trace("Ignoring Banned Connection: " + binarySocket);
                    binarySocket.close();
                    return;
                }

                Logger.debug("New Connection: " + binarySocket);
                _banFilter.onNodeConnected(ip);

                final BitcoinNode bitcoinNode = _bitcoinNodeFactory.newNode(binarySocket);
                _bitcoinNodeManager.addNode(bitcoinNode);
            }
        });


        if  ( fastSyncIsEnabled && (! fastSyncHasCompleted) ) {
            _bitcoinNodeManager.setFastSyncIsEnabled(true); // Allow connection with pruned nodes with UTXO Commitments when fast sync is enabled.

            Logger.debug("Pausing services for fast sync...");
            _blockDownloader.pause();
            _utxoCommitmentGenerator.pause();
            _blockchainIndexer.pause();
        }

        {
            final ImmutableListBuilder<SleepyService> listBuilder = new ImmutableListBuilder<>();
            listBuilder.add(_blockchainIndexer);
            listBuilder.add(_slpTransactionProcessor);
            listBuilder.add(_transactionProcessor);
            listBuilder.add(_transactionDownloader);
            listBuilder.add(_blockchainBuilder);
            listBuilder.add(_blockDownloader);
            listBuilder.add(_blockHeaderDownloader);
            listBuilder.add(_utxoCommitmentGenerator);
            if (_blockPruner != null) {
                listBuilder.add(_blockPruner);
            }
            _allServices = listBuilder.build();
        }

        final Integer rpcPort = _bitcoinProperties.getBitcoinRpcPort();
        if (rpcPort > 0) {
            final NodeRpcHandler rpcSocketServerHandler = new NodeRpcHandler(_rpcThreadPool, _masterInflater);
            {
                final ShutdownHandler shutdownHandler = new ShutdownHandler(mainThread, synchronizationStatusHandler);
                final UtxoCacheHandler utxoCacheHandler = new UtxoCacheHandler(databaseManagerFactory);
                final NodeHandler nodeHandler = new NodeHandler(_bitcoinNodeManager, _bitcoinNodeFactory);
                final QueryAddressHandler queryAddressHandler = new QueryAddressHandler(databaseManagerFactory);
                final ThreadPoolInquisitor threadPoolInquisitor = new ThreadPoolInquisitor(_generalThreadPool); // TODO: Should combine _generalThreadPool and _networkThreadPool, and/or refactor completely.
                final RpcStatisticsHandler statisticsHandler = new RpcStatisticsHandler(_blockHeaderDownloader, _blockchainBuilder, blockProcessor, _bitcoinNodeManager);

                final RpcDataHandler rpcDataHandler = new RpcDataHandler(_systemTime, _masterInflater, databaseManagerFactory, _difficultyCalculatorFactory, transactionValidatorFactory, _transactionDownloader, _blockchainBuilder, _blockHeaderDownloader, _blockDownloader, doubleSpendProofStore, _mutableNetworkTime, _upgradeSchedule);

                final MetadataHandler metadataHandler = new MetadataHandler(databaseManagerFactory, doubleSpendProofStore);
                final QueryBlockchainHandler queryBlockchainHandler = new QueryBlockchainHandler(databaseConnectionFactory);

                final ServiceInquisitor serviceInquisitor = new ServiceInquisitor();
                for (final SleepyService sleepyService : _allServices) {
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

                final RpcIndexerHandler slpValidationHandler = new RpcIndexerHandler(databaseManagerFactory);

                rpcSocketServerHandler.setSynchronizationStatusHandler(synchronizationStatusHandler);
                rpcSocketServerHandler.setShutdownHandler(shutdownHandler);
                rpcSocketServerHandler.setUtxoCacheHandler(utxoCacheHandler);
                rpcSocketServerHandler.setNodeHandler(nodeHandler);
                rpcSocketServerHandler.setQueryAddressHandler(queryAddressHandler);
                rpcSocketServerHandler.setThreadPoolInquisitor(threadPoolInquisitor);
                rpcSocketServerHandler.setServiceInquisitor(serviceInquisitor);
                rpcSocketServerHandler.setStatisticsHandler(statisticsHandler);
                rpcSocketServerHandler.setDataHandler(rpcDataHandler);
                rpcSocketServerHandler.setMetadataHandler(metadataHandler);
                rpcSocketServerHandler.setQueryBlockchainHandler(queryBlockchainHandler);
                rpcSocketServerHandler.setLogLevelSetter(logLevelSetter);
                rpcSocketServerHandler.setIndexerHandler(slpValidationHandler);
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
            final DatabaseMaintainer databaseMaintainer = new DatabaseMaintainer(databaseConnectionFactory);
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

        _lowMemoryMonitor = new LowMemoryMonitor(0.9F, new Runnable() {
            @Override
            public void run() {
                Logger.warn("90% of memory usage reached.");
                final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getNodes();
                final int bitcoinNodeCount = bitcoinNodes.getCount();
                for (int i = 0; i < bitcoinNodeCount; ++i) {
                    final BitcoinNode bitcoinNode = bitcoinNodes.get(i);
                    if ((i % 2) == 0) {
                        bitcoinNode.disconnect();
                    }
                }

                _bitcoinNodeManager.setMaxNodeCount(Math.max(4, (bitcoinNodeCount / 2)));
            }
        });
    }

    public void loop() {
        final boolean indexModeIsEnabled = _bitcoinProperties.isIndexingModeEnabled();
        final boolean pruningModeIsEnabled = _bitcoinProperties.isPruningModeEnabled();
        final boolean fastSyncIsEnabled = _bitcoinProperties.isFastSyncEnabled();
        final boolean bootstrapIsEnabled = _bitcoinProperties.isBootstrapEnabled();

        _propertiesStore.start();
        _networkThreadPool.start();
        _blockProcessingThreadPool.start();
        _generalThreadPool.start();
        _rpcThreadPool.start();

        final MilliTimer timer = new MilliTimer();
        timer.start();

        final FullNodeDatabaseManagerFactory databaseManagerFactory = _databaseManagerFactory;

        if (bootstrapIsEnabled) {
            final HeadersBootstrapper headersBootstrapper = new FullNodeHeadersBootstrapper(databaseManagerFactory);
            if (headersBootstrapper.shouldRun()) {
                Logger.info("[Bootstrapping Headers]");
                headersBootstrapper.run();
            }
        }

        { // Validate the UTXO set is up to date...
            Logger.info("[Checking UTXO Set]");
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final long utxoCommitFrequency = _getUtxoCommitFrequency();
                final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, utxoCommitFrequency);

                if (_rebuildUtxoSet) {
                    Logger.info("Rebuilding UTXO set from genesis.");
                    unspentTransactionOutputManager.rebuildUtxoSetFromGenesisBlock(databaseManagerFactory, UndoLogDatabaseManager.MAX_REORG_DEPTH.longValue(), new Runnable() {
                        @Override
                        public void run() {
                            _utxoCommitmentGenerator.wakeUp();
                        }
                    });
                }
                else {
                    unspentTransactionOutputManager.buildUtxoSet(databaseManagerFactory);
                }
            }
            catch (final DatabaseException exception) {
                Logger.error(exception);
                _shutdown();
                System.exit(1);
            }
        }

        if (_bitcoinProperties.isBlockchainCacheEnabled()) { // Warm up the DB Cache...
            Logger.info("[Warming DB Cache]");
            try {
                databaseManagerFactory.initializeCache();
            }
            catch (final Exception exception) {
                Logger.error(exception);
                _shutdown();
                System.exit(1);
            }
        }

        { // Warm up the UTXO cache...
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                if (unspentTransactionOutputDatabaseManager instanceof UnspentTransactionOutputJvmManager) {
                    Logger.info("[Warming UTXO Cache]");

                    final UnspentTransactionOutputJvmManager unspentTransactionOutputJvmManager = (UnspentTransactionOutputJvmManager) unspentTransactionOutputDatabaseManager;
                    final Boolean utxoCacheFileExists = unspentTransactionOutputJvmManager.doesUtxoCacheLoadFileExist();
                    // The LOAD_VIA_RECENT_TRANSACTIONS depends on the transactions table being populated which is (effectively) unavailable in pruning mode.

                    final CacheLoadingMethod cacheLoadingMethod;
                    if (utxoCacheFileExists && _utxoCacheLoadFileIsEnabled) {
                        cacheLoadingMethod = CacheLoadingMethod.LOAD_VIA_UTXO_LOAD_FILE;
                    }
                    else {
                        cacheLoadingMethod =  (! pruningModeIsEnabled ? CacheLoadingMethod.LOAD_VIA_RECENT_TRANSACTIONS : null);
                    }

                    if (cacheLoadingMethod != null) {
                        unspentTransactionOutputJvmManager.populateCache(cacheLoadingMethod);
                    }
                }
                else {
                    Logger.debug("Unexpected UnspentTransactionOutputDatabaseManager implementation.");
                }
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }

        if (! _bitcoinProperties.shouldSkipNetworking()) {
            Logger.info("[Starting Node Manager]");
            _bitcoinNodeManager.start();

            final List<String> userAgentBlacklist = _bitcoinProperties.getUserAgentBlacklist();
            for (final String stringMatcher : userAgentBlacklist) {
                try {
                    final Pattern pattern = Pattern.compile(stringMatcher);
                    _bitcoinNodeManager.addToUserAgentBlacklist(pattern);
                }
                catch (final Exception exception) {
                    Logger.info("Ignoring invalid user agent blacklist pattern: " + stringMatcher);
                }
            }

            final List<NodeProperties> nodeWhitelist = _bitcoinProperties.getNodeWhitelist();
            for (final NodeProperties whiteListedNode : nodeWhitelist) {
                final String host = whiteListedNode.getAddress();
                try {
                    final Ip ip = Ip.fromStringOrHost(host);
                    if (ip == null) {
                        Logger.warn("Unable to determine seed node host: " + host);
                        continue;
                    }

                    _bitcoinNodeManager.addToWhitelist(ip);
                }
                catch (final Exception exception) {
                    Logger.debug("Unable to determine host: " + host);
                }
            }

            final List<NodeProperties> seedNodes = _bitcoinProperties.getSeedNodeProperties();
            for (final NodeProperties nodeProperties : seedNodes) {
                final String host = nodeProperties.getAddress();
                final Integer port = nodeProperties.getPort();
                try {
                    final Ip ip = Ip.fromStringOrHost(host);
                    if (ip == null) {
                        Logger.warn("Unable to determine seed node host: " + host);
                        continue;
                    }

                    final String ipAddressString = ip.toString();

                    _bitcoinNodeManager.defineSeedNode(new NodeIpAddress(ip, port));

                    final BitcoinNode node = _bitcoinNodeFactory.newNode(ipAddressString, port);
                    _bitcoinNodeManager.addNode(node);
                }
                catch (final Exception exception) {
                    Logger.debug("Unable to determine host: " + host);
                }
            }

            final List<String> dnsSeeds = _bitcoinProperties.getDnsSeeds();
            _bitcoinNodeManager.defineDnsSeeds(dnsSeeds);
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

        if (! _bitcoinProperties.shouldSkipNetworking()) {
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

        Logger.info("[Starting UTXO Commitment Generator]");
        _utxoCommitmentGenerator.start();

        if (! (_blockchainIndexer instanceof DisabledBlockchainIndexer)) {
            Logger.info("[Starting Blockchain Indexer]");
            _blockchainIndexer.start();
        }

        if (_slpTransactionProcessor != null) {
            Logger.info("[Started SlpTransaction Processor]");
            _slpTransactionProcessor.start();
        }

        if (_blockPruner != null) {
            Logger.info("[Starting BlockPruner]");
            _blockPruner.start();
        }

        _uptimeTimer.start();
        _databaseMaintenanceThread.start();

        final Runtime runtime = Runtime.getRuntime();
        int sleepCount = 1;
        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(5000); } catch (final Exception exception) { break; }

            if (sleepCount == 0) {
                runtime.gc();

                // Wakeup sleepy services every 5 minutes...
                for (final SleepyService sleepyService : _allServices) {
                    if (sleepyService != null) {
                        sleepyService.wakeUp();
                    }
                }
            }

            Logger.flush();

            sleepCount = ((sleepCount + 1) % 12);
        }

        _shutdown();
    }

    public void shutdown() {
        _shutdown();
    }
}

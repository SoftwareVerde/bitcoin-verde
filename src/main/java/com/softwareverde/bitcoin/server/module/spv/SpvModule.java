package com.softwareverde.bitcoin.server.module.spv;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactory;
import com.softwareverde.bitcoin.block.validator.BlockValidatorFactoryCore;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.SeedNodeProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.ReadOnlyLocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.spv.SpvBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.spv.handler.MerkleBlockDownloader;
import com.softwareverde.bitcoin.server.module.spv.handler.SpvRequestDataHandler;
import com.softwareverde.bitcoin.server.module.spv.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.Wallet;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.pool.MainThreadPool;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.concurrent.pool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashSet;

public class SpvModule {
    public interface MerkleBlockSyncUpdateCallback {
        void onMerkleBlockHeightUpdated(Long currentBlockHeight, Boolean isSynchronizing);
    }

    public interface NewTransactionCallback {
        void onNewTransactionReceived(Transaction transaction);
    }

    public enum Status {
        INITIALIZING        ("Initializing"),
        LOADING             ("Loading"),
        ONLINE              ("Online"),
        SHUTTING_DOWN       ("Shutting Down"),
        OFFLINE             ("Offline");

        private final String value;
        Status(final String value) { this.value = value; }

        public String getValue() { return this.value; }
    }

    protected final Object _initPin = new Object();
    protected Boolean _isInitialized = false;

    protected final SeedNodeProperties[] _seedNodes;
    protected final Environment _environment;

    protected final Wallet _wallet;

    protected final SpvRequestDataHandler _spvRequestDataHandler = new SpvRequestDataHandler();
    protected BitcoinNodeManager _bitcoinNodeManager;
    protected BlockHeaderDownloader _blockHeaderDownloader;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;

    protected final SystemTime _systemTime = new SystemTime();
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;
    protected final ReadOnlyLocalDatabaseManagerCache _readOnlyDatabaseManagerCache;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final SpvDatabaseManagerFactory _databaseManagerFactory;
    protected final MainThreadPool _mainThreadPool;
    protected final BanFilter _banFilter;
    protected MerkleBlockDownloader _merkleBlockDownloader;

    protected NodeInitializer _nodeInitializer;

    protected Long _minimumMerkleBlockHeight = 575000L;

    protected volatile Status _status = Status.OFFLINE;
    protected Runnable _onStatusUpdatedCallback = null;
    protected NewTransactionCallback _newTransactionCallback = null;

    protected Runnable _newBlockHeaderAvailableCallback = null;

    protected Sha256Hash _currentMerkleBlockHash = null;
    protected MerkleBlockSyncUpdateCallback _merkleBlockSyncUpdateCallback = null;

    protected void _setStatus(final Status status) {
        final Status previousStatus = _status;
        _status = status;

        if (previousStatus != status) {
            final Runnable callback = _onStatusUpdatedCallback;
            if (callback != null) {
                callback.run();
            }
        }
    }

    protected void _waitForInit() {
        if (_isInitialized) { return; }

        synchronized (_initPin) {
            if (_isInitialized) { return; }
            try { _initPin.wait(); } catch (final Exception exception) { }
        }
    }

    protected void _connectToSeedNodes() {
        for (final SeedNodeProperties seedNodeProperties : _seedNodes) {
            final NodeIpAddress nodeIpAddress = SeedNodeProperties.toNodeIpAddress(seedNodeProperties);
            if (nodeIpAddress == null) { continue; }

            final boolean isAlreadyConnectedToNode = _bitcoinNodeManager.isConnectedToNode(nodeIpAddress);
            if (isAlreadyConnectedToNode) { continue; }

            final BitcoinNode node = _nodeInitializer.initializeNode(nodeIpAddress);
            _bitcoinNodeManager.addNode(node);

        }
    }

    protected void _executeMerkleBlockSyncUpdateCallback() {
        final MerkleBlockSyncUpdateCallback merkleBlockSyncUpdateCallback = _merkleBlockSyncUpdateCallback;
        if (merkleBlockSyncUpdateCallback == null) { return; }

        _mainThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Database database = _environment.getDatabase();
                try (final DatabaseConnection databaseConnection = database.newConnection()) {
                    final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                    final Long blockHeight;
                    {
                        final BlockId blockId;
                        final Sha256Hash currentMerkleBlockHash = _currentMerkleBlockHash;
                        if (currentMerkleBlockHash != null) {
                            blockId = blockHeaderDatabaseManager.getBlockHeaderId(_currentMerkleBlockHash);
                        }
                        else {
                            blockId = blockDatabaseManager.getHeadBlockId();
                        }

                        blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
                    }

                    final Boolean isSynchronizingMerkleBlocks = _merkleBlockDownloader.isRunning();
                    merkleBlockSyncUpdateCallback.onMerkleBlockHeightUpdated(blockHeight, isSynchronizingMerkleBlocks);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                }
            }
        });
    }

    protected void _synchronizeMerkleBlocks() {
        if (! _bitcoinNodeManager.hasBloomFilter()) {
            Logger.warn("Unable to synchronize merkle blocks. Bloom filter not set.", new Exception());
            return;
        }

        _merkleBlockDownloader.start();
    }

    protected void _shutdown() {
        _setStatus(Status.SHUTTING_DOWN);

        Logger.info("[Stopping MerkleBlock Downloader]");
        _merkleBlockDownloader.shutdown();

        Logger.info("[Stopping Header Downloader]");
        _blockHeaderDownloader.stop();

        Logger.info("[Stopping Node Manager]");
        _bitcoinNodeManager.shutdown();
        _bitcoinNodeManager.stopNodeMaintenanceThread();

        Logger.info("[Shutting Down Thread Server]");
        _mainThreadPool.stop();

        Logger.info("[Shutting Down Database]");
        _environment.getMasterDatabaseManagerCache().close();

        Logger.flush();
        _setStatus(Status.OFFLINE);
    }

    protected void _loadDownloadedTransactionsIntoWallet() {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, _readOnlyDatabaseManagerCache);
            final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final HashSet<TransactionId> confirmedTransactionIds = new HashSet<TransactionId>();

            { // Load confirmed Transactions from database...
                int loadedConfirmedTransactionCount = 0;
                final List<BlockId> blockIds = blockDatabaseManager.getBlockIdsWithTransactions();
                for (final BlockId blockId : blockIds) {
                    final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(blockId);
                    for (final TransactionId transactionId : transactionIds) {
                        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                        if (transaction == null) {
                            Logger.warn("Unable to inflate Transaction: " + transactionId);
                            continue;
                        }

                        confirmedTransactionIds.add(transactionId);
                        _wallet.addTransaction(transaction);
                        loadedConfirmedTransactionCount += 1;
                    }
                }
                Logger.debug("Loaded " + loadedConfirmedTransactionCount + " confirmed transactions.");
            }

            { // Load remaining Transactions as unconfirmed Transactions from database...
                int loadedUnconfirmedTransactionCount = 0;
                final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds();
                for (final TransactionId transactionId : transactionIds) {
                    final boolean isUniqueTransactionId = confirmedTransactionIds.add(transactionId);
                    if (! isUniqueTransactionId) { continue; }

                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    if (transaction == null) {
                        Logger.warn("Unable to inflate Transaction: " + transactionId);
                        continue;
                    }

                    _wallet.addUnconfirmedTransaction(transaction);
                    loadedUnconfirmedTransactionCount += 1;
                }
                Logger.debug("Loaded " + loadedUnconfirmedTransactionCount + " unconfirmed transactions.");
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    public SpvModule(final Environment environment, final SeedNodeProperties[] seedNodes, final Wallet wallet) {
        _seedNodes = seedNodes;
        _wallet = wallet;
        final Integer maxPeerCount = seedNodes.length; // (bitcoinProperties.skipNetworking() ? 0 : bitcoinProperties.getMaxPeerCount());
        _mainThreadPool = new MainThreadPool(Math.min(maxPeerCount * 8, 256), 5000L);

        _mainThreadPool.setShutdownCallback(new Runnable() {
            @Override
            public void run() {
                try {
                    _shutdown();
                } catch (final Throwable ignored) { }
            }
        });

        _environment = environment;

        final Database database = _environment.getDatabase();
        _masterDatabaseManagerCache = _environment.getMasterDatabaseManagerCache();
        _readOnlyDatabaseManagerCache = new ReadOnlyLocalDatabaseManagerCache(_masterDatabaseManagerCache);
        _databaseConnectionFactory = database.newConnectionFactory();
        _databaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, _readOnlyDatabaseManagerCache);
        _banFilter = new BanFilterCore(_databaseManagerFactory);
    }

    public Boolean isInitialized() {
        return _isInitialized;
    }

    public void initialize() {
        final Thread mainThread = Thread.currentThread();
        _setStatus(Status.INITIALIZING);

        final Integer maxPeerCount = 5;

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

        _setStatus(Status.LOADING);

        final MutableMedianBlockTime medianBlockHeaderTime;
        { // Initialize MedianBlockTime...
            {
                MutableMedianBlockTime newMedianBlockHeaderTime = null;
                try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final DatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

                    newMedianBlockHeaderTime = blockHeaderDatabaseManager.initializeMedianBlockHeaderTime();
                }
                catch (final DatabaseException exception) {
                    Logger.error(exception);
                    BitcoinUtil.exitFailure();
                }
                medianBlockHeaderTime = newMedianBlockHeaderTime;
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(_databaseManagerFactory);

        final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                final ThreadPoolThrottle threadPoolThrottle = new ThreadPoolThrottle(64, _mainThreadPool);
                threadPoolThrottle.start();
                return threadPoolThrottle;
            }
        };

        final SpvDatabaseManagerFactory databaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, _readOnlyDatabaseManagerCache);

        _merkleBlockDownloader = new MerkleBlockDownloader(databaseManagerFactory, new MerkleBlockDownloader.Downloader() {
            @Override
            public void requestMerkleBlock(final Sha256Hash blockHash, final BitcoinNodeManager.DownloadMerkleBlockCallback callback) {
                if (! _bitcoinNodeManager.hasBloomFilter()) {
                    if (callback != null) {
                        callback.onFailure(blockHash);
                    }
                    return;
                }

                _bitcoinNodeManager.requestMerkleBlock(blockHash, callback);
            }
        });
        _merkleBlockDownloader.setMinimumMerkleBlockHeight(_minimumMerkleBlockHeight);
        _merkleBlockDownloader.setDownloadCompleteCallback(new MerkleBlockDownloader.DownloadCompleteCallback() {
            @Override
            public void newMerkleBlockDownloaded(final MerkleBlock merkleBlock, final List<Transaction> transactions) {
                final BloomFilter walletBloomFilter = _wallet.getBloomFilter();
                final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(walletBloomFilter);

                try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                    final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                    TransactionUtil.startTransaction(databaseConnection);
                    final Sha256Hash previousBlockHash = merkleBlock.getPreviousBlockHash();
                    if (! Util.areEqual(previousBlockHash, Sha256Hash.EMPTY_HASH)) { // Check for Genesis Block...
                        final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(merkleBlock.getPreviousBlockHash());
                        if (previousBlockId == null) {
                            Logger.debug("Out of order MerkleBlock received. Discarding. " + merkleBlock.getHash());
                            return;
                        }
                    }

                    synchronized (BlockHeaderDatabaseManager.MUTEX) {
                        final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(merkleBlock);
                        blockDatabaseManager.storePartialMerkleTree(blockId, merkleBlock.getPartialMerkleTree());

                        for (final Transaction transaction : transactions) {
                            if (transactionBloomFilterMatcher.shouldInclude(transaction)) {
                                final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
                                blockDatabaseManager.addTransactionToBlock(blockId, transactionId);

                                _wallet.addTransaction(transaction);
                            }
                        }
                    }
                    TransactionUtil.commitTransaction(databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return;
                }

                final NewTransactionCallback newTransactionCallback = _newTransactionCallback;
                if (newTransactionCallback != null) {
                    _mainThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            for (final Transaction transaction : transactions) {
                                newTransactionCallback.onNewTransactionReceived(transaction);
                            }
                        }
                    });
                }
            }
        });
        _merkleBlockDownloader.setMerkleBlockProcessedCallback(new Runnable() {
            @Override
            public void run() {
                _executeMerkleBlockSyncUpdateCallback();
            }
        });

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                return nodeFeatures;
            }
        };

        { // Initialize NodeInitializer...
            final NodeInitializer.TransactionsAnnouncementCallbackFactory transactionsAnnouncementCallbackFactory = new NodeInitializer.TransactionsAnnouncementCallbackFactory() {

                protected final BitcoinNodeManager.DownloadTransactionCallback _downloadTransactionsCallback = new BitcoinNodeManager.DownloadTransactionCallback() {
                    @Override
                    public void onResult(final Transaction transaction) {
                        final Sha256Hash transactionHash = transaction.getHash();
                        Logger.debug("Received Transaction: " + transactionHash);

                        final BloomFilter walletBloomFilter = _wallet.getBloomFilter();
                        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(walletBloomFilter);
                        if (! transactionBloomFilterMatcher.shouldInclude(transaction)) {
                            Logger.debug("Skipping Transaction that does not match filter: " + transactionHash);
                            return;
                        }

                        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                            TransactionUtil.startTransaction(databaseConnection);
                            transactionDatabaseManager.storeTransaction(transaction);
                            TransactionUtil.commitTransaction(databaseConnection);
                        }
                        catch (final DatabaseException exception) {
                            Logger.warn(exception);
                        }

                        _wallet.addUnconfirmedTransaction(transaction);

                        final NewTransactionCallback newTransactionCallback = _newTransactionCallback;
                        if (newTransactionCallback != null) {
                            _mainThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    newTransactionCallback.onNewTransactionReceived(transaction);
                                }
                            });
                        }
                    }
                };

                @Override
                public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
                    return new BitcoinNode.TransactionInventoryMessageCallback() {
                        @Override
                        public void onResult(final List<Sha256Hash> transactions) {
                            Logger.debug("Received " + transactions.getSize() + " transaction inventories.");

                            final MutableList<Sha256Hash> unseenTransactions = new MutableList<Sha256Hash>(transactions.getSize());
                            try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                                final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                                final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                                for (final Sha256Hash transactionHash : transactions) {
                                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                                    if (transactionId == null) {
                                        unseenTransactions.add(transactionHash);
                                    }
                                }
                            }
                            catch (final DatabaseException exception) {
                                Logger.warn(exception);
                            }

                            Logger.debug(unseenTransactions.getSize() + " transactions were new.");
                            if (! unseenTransactions.isEmpty()) {
                                bitcoinNode.requestTransactions(unseenTransactions, _downloadTransactionsCallback);
                            }
                        }
                    };
                }
            };

            final QueryBlockHeadersHandler queryBlockHeadersHandler = new QueryBlockHeadersHandler(databaseManagerFactory);
            final BitcoinNode.RequestPeersHandler requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<BitcoinNodeIpAddress>(connectedNodes.getSize());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {

                        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
                        bitcoinNodeIpAddress.setNodeFeatures(bitcoinNode.getNodeFeatures());

                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            final NodeInitializer.Properties nodeInitializerProperties = new NodeInitializer.Properties();
            nodeInitializerProperties.synchronizationStatus = synchronizationStatusHandler;
            nodeInitializerProperties.blockInventoryMessageHandler = new BitcoinNode.BlockInventoryMessageCallback() {
                @Override
                public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
                    if (! _bitcoinNodeManager.hasBloomFilter()) { return; }

                    // Only restart the synchronization process if it has already successfully completed.
                    _merkleBlockDownloader.wakeUp();
                }
            };
            nodeInitializerProperties.threadPoolFactory = threadPoolFactory;
            nodeInitializerProperties.localNodeFeatures = localNodeFeatures;
            nodeInitializerProperties.transactionsAnnouncementCallbackFactory = transactionsAnnouncementCallbackFactory;
            nodeInitializerProperties.queryBlocksCallback = null;
            nodeInitializerProperties.queryBlockHeadersCallback = queryBlockHeadersHandler;
            nodeInitializerProperties.requestDataCallback = _spvRequestDataHandler;
            nodeInitializerProperties.requestPeersHandler = requestPeersHandler;
            nodeInitializerProperties.queryUnconfirmedTransactionsCallback = null;
            nodeInitializerProperties.spvBlockInventoryMessageCallback = _merkleBlockDownloader;

            nodeInitializerProperties.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<BitcoinNodeIpAddress>(connectedNodes.getSize());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {
                        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
                        bitcoinNodeIpAddress.setNodeFeatures(bitcoinNode.getNodeFeatures());

                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            nodeInitializerProperties.binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;

            _nodeInitializer = new NodeInitializer(nodeInitializerProperties);
        }

        { // Initialize NodeManager...
            final BitcoinNodeManager.Properties properties = new BitcoinNodeManager.Properties();
            {
                properties.databaseManagerFactory = databaseManagerFactory;
                properties.nodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPoolFactory, localNodeFeatures);
                properties.maxNodeCount = maxPeerCount;
                properties.networkTime = _mutableNetworkTime;
                properties.nodeInitializer = _nodeInitializer;
                properties.banFilter = _banFilter;
                properties.memoryPoolEnquirer = null;
                properties.synchronizationStatusHandler = synchronizationStatusHandler;
                properties.threadPool = _mainThreadPool;
            }

            _bitcoinNodeManager = new BitcoinNodeManager(properties);
            _bitcoinNodeManager.enableTransactionRelay(false);
            _bitcoinNodeManager.setShouldOnlyConnectToSeedNodes(_shouldOnlyConnectToSeedNodes);

            for (final SeedNodeProperties seedNodeProperties : _seedNodes) {
                final NodeIpAddress nodeIpAddress = SeedNodeProperties.toNodeIpAddress(seedNodeProperties);
                if (nodeIpAddress == null) { continue; }

                _bitcoinNodeManager.defineSeedNode(nodeIpAddress);
            }
        }

        { // Initialize BlockHeaderDownloader...
            final BlockValidatorFactory blockValidatorFactory = new BlockValidatorFactoryCore();
            _blockHeaderDownloader = new BlockHeaderDownloader(databaseManagerFactory, _bitcoinNodeManager, blockValidatorFactory, medianBlockHeaderTime, null, _mainThreadPool);
            _blockHeaderDownloader.setMaxHeaderBatchSize(100);
            _blockHeaderDownloader.setMinBlockTimestamp(_systemTime.getCurrentTimeInSeconds());
        }

        final LocalDatabaseManagerCache localDatabaseCache = (_masterDatabaseManagerCache != null ? new LocalDatabaseManagerCache(_masterDatabaseManagerCache) : null);

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final DatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, localDatabaseCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
            final Long headBlockHeaderTimestamp = (headBlockHeaderId != null ? blockHeaderDatabaseManager.getBlockTimestamp(headBlockHeaderId) : 0L);
            final Long currentTimestamp = _systemTime.getCurrentTimeInSeconds();
            final long behindThreshold = (60L * 60L); // 1 Hour

            if ((currentTimestamp - headBlockHeaderTimestamp) > behindThreshold) {
                synchronizationStatusHandler.setState(State.SYNCHRONIZING);
            }
            else {
                synchronizationStatusHandler.setState(State.ONLINE);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        _blockHeaderDownloader.setNewBlockHeaderAvailableCallback(new Runnable() {
            @Override
            public void run() {
                final Runnable newBlockHeaderAvailableCallback = _newBlockHeaderAvailableCallback;
                if (newBlockHeaderAvailableCallback != null) {
                    newBlockHeaderAvailableCallback.run();
                }
            }
        });

        if (_wallet.hasPrivateKeys()) { // Avoid sending an empty bloom filter since Bitcoin Unlimited nodes will ignore it.
            final MutableBloomFilter bloomFilter = _wallet.generateBloomFilter();
            _bitcoinNodeManager.setBloomFilter(bloomFilter);
        }

        _loadDownloadedTransactionsIntoWallet();

        _isInitialized = true;
        synchronized (_initPin) {
            _initPin.notifyAll();
        }
    }

    public void setNewBlockHeaderAvailableCallback(final Runnable newBlockHeaderAvailableCallback) {
        _newBlockHeaderAvailableCallback = newBlockHeaderAvailableCallback;
    }

    public Long getBlockHeight() {
        return _blockHeaderDownloader.getBlockHeight();
    }

    public void loop() {
        _waitForInit();
        _setStatus(Status.ONLINE);

        if ( (! _bitcoinNodeManager.hasBloomFilter()) && (_wallet.hasPrivateKeys()) ) {
            final MutableBloomFilter bloomFilter = _wallet.generateBloomFilter();
            _bitcoinNodeManager.setBloomFilter(bloomFilter);
            _merkleBlockDownloader.wakeUp();
        }

        Logger.info("[Starting Node Manager]");
        _bitcoinNodeManager.startNodeMaintenanceThread();

        _connectToSeedNodes();

        Logger.info("[Starting Header Downloader]");
        _blockHeaderDownloader.start();

        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(50000); } catch (final Exception exception) { break; }
        }

        Logger.info("[SPV Module Exiting]");
        _shutdown();
    }

    public void setOnStatusUpdatedCallback(final Runnable callback) {
        _onStatusUpdatedCallback = callback;
    }

    public void setNewTransactionCallback(final NewTransactionCallback newTransactionCallback) {
        _newTransactionCallback = newTransactionCallback;
    }

    public Status getStatus() {
        return _status;
    }

    public BitcoinNodeManager getBitcoinNodeManager() {
        return _bitcoinNodeManager;
    }

    public void connectToSeedNodes() {
        _connectToSeedNodes();
    }

    public void storeTransaction(final Transaction transaction) throws DatabaseException {
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            transactionDatabaseManager.storeTransaction(transaction);
        }
    }

    public void broadcastTransaction(final Transaction transaction) {
        // TODO: Simply broadcast the transaction unannounced instead of advertising the inventory...

        _spvRequestDataHandler.addSpvTransaction(transaction);

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(1);
        transactionHashes.add(transaction.getHash());

        for (final BitcoinNode bitcoinNode : _bitcoinNodeManager.getNodes()) {
            Logger.info("Sending Tx Hash " + transaction.getHash() + " to " + bitcoinNode.getConnectionString());
            bitcoinNode.transmitTransactionHashes(transactionHashes);
        }
    }

    public void setMerkleBlockSyncUpdateCallback(final MerkleBlockSyncUpdateCallback merkleBlockSyncUpdateCallback) {
        _merkleBlockSyncUpdateCallback = merkleBlockSyncUpdateCallback;
    }

    public void synchronizeMerkleBlocks() {
        _synchronizeMerkleBlocks();
    }

    /**
     * Sets the merkleBlock height to begin syncing at.
     *  This value is inclusive.
     *  MerkleBlocks before this height will not be queried for transactions.
     */
    public void setMinimumMerkleBlockHeight(final Long minimumMerkleBlockHeight) {
        _minimumMerkleBlockHeight = minimumMerkleBlockHeight;
        _merkleBlockDownloader.setMinimumMerkleBlockHeight(minimumMerkleBlockHeight);
        _merkleBlockDownloader.resetQueue();
    }

    public void setShouldOnlyConnectToSeedNodes(final Boolean shouldOnlyConnectToSeedNodes) {
        _shouldOnlyConnectToSeedNodes = shouldOnlyConnectToSeedNodes;

        if (_bitcoinNodeManager != null) {
            _bitcoinNodeManager.setShouldOnlyConnectToSeedNodes(shouldOnlyConnectToSeedNodes);
        }
    }
}

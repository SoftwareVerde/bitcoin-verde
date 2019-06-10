package com.softwareverde.bitcoin.server.module.spv;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
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
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.spv.SpvBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.spv.SpvDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.block.QueryBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.manager.BanFilter;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.spv.handler.SpvRequestDataHandler;
import com.softwareverde.bitcoin.server.module.spv.handler.SynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.wallet.Wallet;
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
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpvModule {
    protected interface DownloadMerkleBlockCallback extends BitcoinNodeManager.DownloadMerkleBlockCallback {
        Boolean run(BitcoinNode.MerkleBlockParameters merkleBlockParameters);
        Boolean onFailed(Sha256Hash merkleBlockHash);
    }

    public interface MerkleBlockSyncUpdateCallback {
        void onMerkleBlockHeightUpdated(Long currentBlockHeight, Boolean isSynchronizing);
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

    protected final SystemTime _systemTime = new SystemTime();
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;
    protected final ReadOnlyLocalDatabaseManagerCache _readOnlyDatabaseManagerCache;
    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final MainThreadPool _mainThreadPool;
    protected final BanFilter _banFilter;

    protected NodeInitializer _nodeInitializer;

    protected final AtomicBoolean _merkleBlockSyncHasCompleted = new AtomicBoolean(false); // Is true if the MerkleBlock synchronization process has been run and completed successfully.
    protected final AtomicBoolean _isSynchronizingMerkleBlocks = new AtomicBoolean(false);
    protected final DownloadMerkleBlockCallback _onMerkleBlockDownloaded;

    protected Long _minimumMerkleBlockHeight = 575000L;

    protected volatile Status _status = Status.OFFLINE;
    protected Runnable _onStatusUpdatedCallback = null;
    protected Runnable _newTransactionCallback = null;

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
            try {
                final String host = seedNodeProperties.getAddress();
                final String ipAddressString;
                try {
                    final InetAddress ipAddress = InetAddress.getByName(host);
                    ipAddressString = ipAddress.getHostAddress();
                }
                catch (final Exception exception) {
                    Logger.log("Unable to determine host: " + host);
                    continue;
                }

                final Integer port = seedNodeProperties.getPort();
                final Ip ip = Ip.fromString(ipAddressString);

                final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, port);

                final boolean isAlreadyConnectedToNode = _bitcoinNodeManager.isConnectedToNode(nodeIpAddress);
                if (isAlreadyConnectedToNode) { continue; }

                final BitcoinNode node = _nodeInitializer.initializeNode(ipAddressString, port);
                _bitcoinNodeManager.addNode(node);
            }
            catch (final Exception exception) {
                Logger.log(exception);
            }
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
                    merkleBlockSyncUpdateCallback.onMerkleBlockHeightUpdated(blockHeight, _isSynchronizingMerkleBlocks.get());
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        });
    }

    protected void _synchronizeMerkleBlocks() {
        if (! _bitcoinNodeManager.hasBloomFilter()) {
            Logger.log("Unable to synchronize merkle blocks. Bloom filter not set.");
            Logger.log(new Exception());
        }

        if (_isSynchronizingMerkleBlocks.getAndSet(true)) { return; }

        final Boolean synchronizationContinues = _synchronizeNextMerkleBlock();
        if (! synchronizationContinues) {
            _isSynchronizingMerkleBlocks.set(false);
        }
    }

    protected Boolean _synchronizeNextMerkleBlock() {
        final Database database = _environment.getDatabase();
        try (final DatabaseConnection databaseConnection = database.newConnection()) {
            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockId nextBlockId = blockDatabaseManager.selectNextIncompleteBlock(_minimumMerkleBlockHeight);
            if (nextBlockId == null) {
                _merkleBlockSyncHasCompleted.set(true);
                _currentMerkleBlockHash = null;
                return false;
            }

            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
            _currentMerkleBlockHash = blockHash;

            _bitcoinNodeManager.requestMerkleBlock(blockHash, new BitcoinNodeManager.DownloadMerkleBlockCallback() {
                @Override
                public void onResult(final BitcoinNode.MerkleBlockParameters merkleBlockParameters) {
                    final Boolean wasSuccess = _onMerkleBlockDownloaded.run(merkleBlockParameters);
                    if (! wasSuccess) {
                        _isSynchronizingMerkleBlocks.set(false);
                        _executeMerkleBlockSyncUpdateCallback();
                        return;
                    }

                    final Boolean synchronizationContinues = _synchronizeNextMerkleBlock();
                    if (! synchronizationContinues) {
                        _isSynchronizingMerkleBlocks.set(false);
                        _executeMerkleBlockSyncUpdateCallback();
                        return;
                    }

                    _executeMerkleBlockSyncUpdateCallback();
                }

                @Override
                public void onFailure(final Sha256Hash blockHash) {
                    final Boolean didFail = _onMerkleBlockDownloaded.onFailed(blockHash);

                    if (didFail) {
                        _isSynchronizingMerkleBlocks.set(false);
                        _executeMerkleBlockSyncUpdateCallback();
                    }
                }
            });

            return true;
        }
        catch (final DatabaseException exception) {
            _isSynchronizingMerkleBlocks.set(false);
            exception.printStackTrace(System.err);
            return false;
        }
    }

    protected void _shutdown() {
        _setStatus(Status.SHUTTING_DOWN);

        Logger.log("[Stopping Header Downloader]");
        _blockHeaderDownloader.stop();

        Logger.log("[Stopping Node Manager]");
        _bitcoinNodeManager.shutdown();
        _bitcoinNodeManager.stopNodeMaintenanceThread();

        Logger.log("[Shutting Down Thread Server]");
        _mainThreadPool.stop();

        Logger.log("[Shutting Down Database]");
        _environment.getMasterDatabaseManagerCache().close();

        Logger.shutdown();
        _setStatus(Status.OFFLINE);
    }

    public SpvModule(final Environment environment, final SeedNodeProperties[] seedNodes, final Wallet wallet) {
        _seedNodes = seedNodes;
        _wallet = wallet;
        final Integer maxPeerCount = 8; // (bitcoinProperties.skipNetworking() ? 0 : bitcoinProperties.getMaxPeerCount());
        _mainThreadPool = new MainThreadPool(Math.max(maxPeerCount * 8, 256), 10000L);

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
        _banFilter = new BanFilter(_databaseManagerFactory);

        _onMerkleBlockDownloaded = new DownloadMerkleBlockCallback() {
            private final ConcurrentHashMap<Sha256Hash, ConcurrentLinkedDeque<Long>> _failedDownloadTimes = new ConcurrentHashMap<Sha256Hash, ConcurrentLinkedDeque<Long>>();

            @Override
            public Boolean run(final BitcoinNode.MerkleBlockParameters merkleBlockParameters) {
                if (merkleBlockParameters == null) { return false; }

                final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
                if (merkleBlock == null) { return false; }

                final PartialMerkleTree partialMerkleTree = merkleBlock.getPartialMerkleTree();
                if (partialMerkleTree == null) { return false; }

                final List<Transaction> transactions = merkleBlockParameters.getTransactions();
                if (transactions == null) { return false; }

                if (! merkleBlock.isValid()) {
                    Logger.log("Invalid MerkleBlock received. Discarding. " + merkleBlock.getHash());
                    return false;
                }

                for (final Transaction transaction : transactions) {
                    final Sha256Hash transactionHash = transaction.getHash();
                    if (! merkleBlock.containsTransaction(transactionHash)) {
                        Logger.log("MerkleBlock did not contain transaction. Block: " + merkleBlock.getHash() + " Tx: " + transactionHash);
                        return false;
                    }
                }

                final Database database = _environment.getDatabase();
                try (final DatabaseConnection databaseConnection = database.newConnection()) {
                    TransactionUtil.startTransaction(databaseConnection);
                    final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
                    final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                    final Sha256Hash previousBlockHash = merkleBlock.getPreviousBlockHash();
                    if (! Util.areEqual(previousBlockHash, Sha256Hash.EMPTY_HASH)) { // Check for Genesis Block...
                        final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(merkleBlock.getPreviousBlockHash());
                        if (previousBlockId == null) {
                            Logger.log("NOTICE: Out of order MerkleBlock received. Discarding. " + merkleBlock.getHash());
                            return false;
                        }
                    }

                    synchronized (BlockHeaderDatabaseManager.MUTEX) {
                        final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(merkleBlock);
                        blockDatabaseManager.storePartialMerkleTree(blockId, partialMerkleTree);

                        for (final Transaction transaction : transactions) {
                            final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
                            blockDatabaseManager.addTransactionToBlock(blockId, transactionId);
                        }
                    }
                    TransactionUtil.commitTransaction(databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                    return false;
                }

                for (final Transaction transaction : transactions) {
                    _wallet.addTransaction(transaction);
                }

                final Runnable newTransactionCallback = _newTransactionCallback;
                if (newTransactionCallback != null) {
                    _mainThreadPool.execute(newTransactionCallback);
                }

                return true;
            }

            @Override
            public synchronized Boolean onFailed(final Sha256Hash merkleBlockHash) {
                final Long now = _systemTime.getCurrentTimeInMilliSeconds();
                ConcurrentLinkedDeque<Long> failedDownloadTimestamps = _failedDownloadTimes.get(merkleBlockHash);
                if (failedDownloadTimestamps == null) {
                    failedDownloadTimestamps = new ConcurrentLinkedDeque<Long>();
                    _failedDownloadTimes.put(merkleBlockHash, failedDownloadTimestamps);
                }
                failedDownloadTimestamps.add(now);

                int recentFailureCount = 0;
                if (failedDownloadTimestamps.size() > 3) {
                    for (final Long failedTimestamp : failedDownloadTimestamps) {
                        if (now - failedTimestamp > 30000L) {
                            recentFailureCount += 1;
                        }
                    }
                }

                if (recentFailureCount < 3) {
                    _bitcoinNodeManager.requestMerkleBlock(merkleBlockHash, this);
                    return false;
                }

                return true;
            }

            @Override
            public void onResult(final BitcoinNode.MerkleBlockParameters merkleBlockParameters) {
                this.run(merkleBlockParameters);
            }

            @Override
            public void onFailure(final Sha256Hash merkleBlockHash) {
                this.onFailed(merkleBlockHash);
            }
        };
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
                    Logger.log(throwable);
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
                    Logger.log(exception);
                    BitcoinUtil.exitFailure();
                }
                medianBlockHeaderTime = newMedianBlockHeaderTime;
            }
        }

        final SynchronizationStatusHandler synchronizationStatusHandler = new SynchronizationStatusHandler(_databaseManagerFactory);

        final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                return new ThreadPoolThrottle(64, _mainThreadPool);
            }
        };

        final DatabaseManagerFactory databaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, _readOnlyDatabaseManagerCache);

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
                        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection);
                            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

                            TransactionUtil.startTransaction(databaseConnection);
                            transactionDatabaseManager.storeTransaction(transaction);
                            TransactionUtil.commitTransaction(databaseConnection);
                        }
                        catch (final DatabaseException exception) {
                            Logger.log(exception);
                        }

                        _wallet.addTransaction(transaction);

                        final Runnable newTransactionCallback = _newTransactionCallback;
                        if (newTransactionCallback != null) {
                            _mainThreadPool.execute(newTransactionCallback);
                        }
                    }
                };

                @Override
                public BitcoinNode.TransactionInventoryMessageCallback createTransactionsAnnouncementCallback(final BitcoinNode bitcoinNode) {
                    return new BitcoinNode.TransactionInventoryMessageCallback() {
                        @Override
                        public void onResult(final List<Sha256Hash> transactions) {
                            System.out.println("Received " + transactions.getSize() + " transaction inventories.");

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
                                Logger.log(exception);
                            }

                            System.out.println(unseenTransactions.getSize() + " transactions were new.");
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
                    // Only restart the synchronization process if it has already successfully completed.
                    if (_merkleBlockSyncHasCompleted.get()) {
                        _synchronizeMerkleBlocks();
                    }
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
            nodeInitializerProperties.spvBlockInventoryMessageCallback = new BitcoinNode.SpvBlockInventoryMessageCallback() {
                @Override
                public void onResult(final List<Sha256Hash> blockHashes) {
                    for (final Sha256Hash hash : blockHashes) {
                        _bitcoinNodeManager.requestMerkleBlock(hash, _onMerkleBlockDownloaded);
                    }
                }
            };

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

            _nodeInitializer = new NodeInitializer(nodeInitializerProperties);
        }

        { // Initialize NodeManager...
            _bitcoinNodeManager = new BitcoinNodeManager(_databaseManagerFactory, maxPeerCount, _mutableNetworkTime, _nodeInitializer, _banFilter, null, synchronizationStatusHandler, _mainThreadPool, threadPoolFactory, localNodeFeatures);
            _bitcoinNodeManager.enableTransactionRelay(false);
        }

        { // Initialize BlockHeaderDownloader...
            _blockHeaderDownloader = new BlockHeaderDownloader(databaseManagerFactory, _bitcoinNodeManager, medianBlockHeaderTime, null, _mainThreadPool);
            _blockHeaderDownloader.setMaxHeaderBatchSize(256);
        }

        final LocalDatabaseManagerCache localDatabaseCache = new LocalDatabaseManagerCache(_masterDatabaseManagerCache);

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
            Logger.log(exception);
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

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, _readOnlyDatabaseManagerCache);
            final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final TransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final List<BlockId> blockIds = blockDatabaseManager.getBlockIdsWithTransactions();
            for (final BlockId blockId : blockIds) {
                final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(blockId);
                for (final TransactionId transactionId : transactionIds) {
                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    if (transaction == null) {
                        System.err.println("NOTICE: Unable to inflate Transaction: " + transactionId);
                        continue;
                    }

                    _wallet.addTransaction(transaction);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        if (_wallet.hasPrivateKeys()) { // Avoid sending an empty bloom filter since Bitcoin Unlimited nodes will ignore it.
            final MutableBloomFilter bloomFilter = _wallet.generateBloomFilter();
            _bitcoinNodeManager.setBloomFilter(bloomFilter);
        }

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

        Logger.log("[Starting Node Manager]");
        _bitcoinNodeManager.startNodeMaintenanceThread();

        _connectToSeedNodes();

        Logger.log("[Starting Header Downloader]");
        _blockHeaderDownloader.start();

        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(50000); } catch (final Exception exception) { break; }
        }

        Logger.log("[SPV Module Exiting]");
        _shutdown();
    }

    public void setOnStatusUpdatedCallback(final Runnable callback) {
        _onStatusUpdatedCallback = callback;
    }

    public void setNewTransactionCallback(final Runnable callback) {
        _newTransactionCallback = callback;
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

    public void broadcastTransaction(final Transaction transaction) {
        // TODO: Simply broadcast the transaction unannounced instead of advertising the inventory...

        _spvRequestDataHandler.addSpvTransaction(transaction);

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(1);
        transactionHashes.add(transaction.getHash());

        for (final BitcoinNode bitcoinNode : _bitcoinNodeManager.getNodes()) {
            System.out.println("Sending Tx Hash " + transaction.getHash() + " to " + bitcoinNode.getConnectionString());
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
    }
}

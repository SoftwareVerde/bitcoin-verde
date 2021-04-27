package com.softwareverde.bitcoin.server.module.spv;

import com.softwareverde.bitcoin.CoreInflater;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.core.BlockHeaderDownloaderContext;
import com.softwareverde.bitcoin.inflater.MasterInflater;
import com.softwareverde.bitcoin.server.Environment;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.configuration.NodeProperties;
import com.softwareverde.bitcoin.server.database.Database;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
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
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SlpValidity;
import com.softwareverde.bitcoin.server.module.node.database.transaction.spv.SpvTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.handler.block.RequestBlockHeadersHandler;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeInitializer;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.SpvSlpTransactionValidator;
import com.softwareverde.bitcoin.server.module.spv.handler.MerkleBlockDownloader;
import com.softwareverde.bitcoin.server.module.spv.handler.SpvRequestDataHandler;
import com.softwareverde.bitcoin.server.module.spv.handler.SpvSynchronizationStatusHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.wallet.Wallet;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPoolFactory;
import com.softwareverde.concurrent.threadpool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Tuple;
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

    public interface TransactionValidityChangedCallback {
        void onTransactionValidityChanged(Sha256Hash transactionHash, SlpValidity slpValidity);
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

    protected static final TransactionValidityChangedCallback IGNORE_TRANSACTION_VALIDITY_CHANGED_CALLBACK = new TransactionValidityChangedCallback() {
        @Override
        public void onTransactionValidityChanged(final Sha256Hash transactionHash, final SlpValidity slpValidity) { }
    };

    protected final Object _initPin = new Object();
    protected final Integer _maxPeerCount;
    protected Boolean _isInitialized = false;

    protected final List<NodeProperties> _seedNodes;
    protected final Environment _environment;
    protected final CheckpointConfiguration _checkpointConfiguration;

    protected final MasterInflater _masterInflater;

    protected final Wallet _wallet;

    protected final Boolean _isTestNet;
    protected final SpvRequestDataHandler _spvRequestDataHandler = new SpvRequestDataHandler();
    protected BitcoinNodeManager _bitcoinNodeManager;
    protected BlockHeaderDownloader _blockHeaderDownloader;
    protected SpvSlpTransactionValidator _spvSlpTransactionValidator;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;

    protected final SystemTime _systemTime = new SystemTime();
    protected final MutableNetworkTime _mutableNetworkTime = new MutableNetworkTime();

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final SpvDatabaseManagerFactory _databaseManagerFactory;
    protected final CachedThreadPool _generalThreadPool;
    protected final CachedThreadPool _networkThreadPool;
    protected final BanFilter _banFilter;
    protected MerkleBlockDownloader _merkleBlockDownloader;

    protected BitcoinNodeFactory _bitcoinNodeFactory;
    protected DifficultyCalculatorFactory _difficultyCalculatorFactory;

    protected Long _minimumMerkleBlockHeight = 575000L;

    protected volatile Status _status = Status.OFFLINE;
    protected Runnable _onStatusUpdatedCallback = null;
    protected NewTransactionCallback _newTransactionCallback = null;
    protected TransactionValidityChangedCallback _transactionValidityChangedCallback = null;

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

    protected void _synchronizeSlpValidity() {
        _spvSlpTransactionValidator.wakeUp();
    }

    protected void _executeMerkleBlockSyncUpdateCallback() {
        final MerkleBlockSyncUpdateCallback merkleBlockSyncUpdateCallback = _merkleBlockSyncUpdateCallback;
        if (merkleBlockSyncUpdateCallback == null) { return; }

        _generalThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Database database = _environment.getDatabase();
                try (final DatabaseConnection databaseConnection = database.newConnection()) {
                    final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, database.getMaxQueryBatchSize(), _checkpointConfiguration);
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

        if (_merkleBlockDownloader != null) {
            Logger.info("[Stopping MerkleBlock Downloader]");
            _merkleBlockDownloader.shutdown();
        }

        if (_blockHeaderDownloader != null) {
            Logger.info("[Stopping Header Downloader]");
            _blockHeaderDownloader.stop();
        }

        if (_spvSlpTransactionValidator != null) {
            Logger.info("[Stopping SPV SLP Validator]");
            _spvSlpTransactionValidator.stop();
        }

        if (_bitcoinNodeManager != null) {
            Logger.info("[Stopping Node Manager]");
            _bitcoinNodeManager.shutdown();
        }

        Logger.info("[Shutting Down Thread Server]");
        _networkThreadPool.stop();
        _generalThreadPool.stop();

        Logger.flush();
        _setStatus(Status.OFFLINE);
    }

    protected void _loadDownloadedTransactionsIntoWallet() {
        final Database database = _environment.getDatabase();
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, database.getMaxQueryBatchSize(), _checkpointConfiguration);
            final SpvBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final HashSet<TransactionId> confirmedTransactionIds = new HashSet<TransactionId>();

            { // load known valid/invalid SLP transaction hashes (unknowns will be handed automatically)
                final List<Sha256Hash> validSlpTransactions = transactionDatabaseManager.getSlpTransactionsWithSlpStatus(SlpValidity.VALID);
                for (final Sha256Hash transactionHash : validSlpTransactions) {
                    _wallet.markSlpTransactionAsValid(transactionHash);
                }

                final List<Sha256Hash> invalidSlpTransactions = transactionDatabaseManager.getSlpTransactionsWithSlpStatus(SlpValidity.INVALID);
                for (final Sha256Hash transactionHash : invalidSlpTransactions) {
                    _wallet.markSlpTransactionAsInvalid(transactionHash);
                }
            }

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

    public SpvModule(final Environment environment, final List<NodeProperties> seedNodes, final Integer maxPeerCount, final Wallet wallet) {
        this(environment, seedNodes, maxPeerCount, wallet, false);
    }

    public SpvModule(final Environment environment, final List<NodeProperties> seedNodes, final Integer maxPeerCount, final Wallet wallet, final Boolean isTestNet) {
        _isTestNet = isTestNet;
        _masterInflater = new CoreInflater();
        _seedNodes = seedNodes;
        _wallet = wallet;
        _generalThreadPool = new CachedThreadPool(256, 60000L);
        _networkThreadPool = new CachedThreadPool((16 + (maxPeerCount * 8)), 60000L);
        _maxPeerCount = maxPeerCount;

        _environment = environment;
        _checkpointConfiguration = new CheckpointConfiguration();

        final Database database = _environment.getDatabase();
        _databaseConnectionFactory = database.newConnectionFactory();
        _databaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, database.getMaxQueryBatchSize(), _checkpointConfiguration);
        _banFilter = new BanFilterCore(_databaseManagerFactory);
    }

    public Boolean isInitialized() {
        return _isInitialized;
    }

    public void initialize() {
        final Thread mainThread = Thread.currentThread();
        _setStatus(Status.INITIALIZING);

        _generalThreadPool.start();
        _networkThreadPool.start();

        final Integer maxQueryBatchSize;
        {
            final Database database = _environment.getDatabase();
            maxQueryBatchSize = database.getMaxQueryBatchSize();
        }

        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

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

        final SpvSynchronizationStatusHandler synchronizationStatusHandler = new SpvSynchronizationStatusHandler(_databaseManagerFactory);

        final ThreadPoolFactory threadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                final ThreadPoolThrottle threadPoolThrottle = new ThreadPoolThrottle(64, _networkThreadPool);
                threadPoolThrottle.start();
                return threadPoolThrottle;
            }
        };

        final SpvDatabaseManagerFactory databaseManagerFactory = new SpvDatabaseManagerFactory(_databaseConnectionFactory, maxQueryBatchSize, _checkpointConfiguration);

        _merkleBlockDownloader = new MerkleBlockDownloader(databaseManagerFactory, new MerkleBlockDownloader.Downloader() {
            @Override
            public Tuple<RequestId, BitcoinNode> requestMerkleBlock(final Sha256Hash blockHash, final BitcoinNode.DownloadMerkleBlockCallback callback) {
                if (! _bitcoinNodeManager.hasBloomFilter()) {
                    if (callback != null) {
                        callback.onFailure(null, null, blockHash);
                    }
                    return new Tuple<>();
                }

                final List<BitcoinNode> bitcoinNodes = _bitcoinNodeManager.getPreferredNodes();
                if (bitcoinNodes.isEmpty()) { return new Tuple<>(); }

                final int index = (int) (Math.random() * bitcoinNodes.getCount());
                final BitcoinNode bitcoinNode = bitcoinNodes.get(index);
                final RequestId requestId = bitcoinNode.requestMerkleBlock(blockHash, callback);
                return new Tuple<>(requestId, bitcoinNode);
            }
        });
        _merkleBlockDownloader.setMinimumMerkleBlockHeight(_minimumMerkleBlockHeight);
        _merkleBlockDownloader.setDownloadCompleteCallback(new MerkleBlockDownloader.DownloadCompleteCallback() {
            @Override
            public void newMerkleBlockDownloaded(final MerkleBlock merkleBlock, final List<Transaction> transactions) {
                final BloomFilter walletBloomFilter = _wallet.getBloomFilter();
                final AddressInflater addressInflater = _masterInflater.getAddressInflater();
                final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(walletBloomFilter, addressInflater);

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

                        _synchronizeSlpValidity();
                    }
                    TransactionUtil.commitTransaction(databaseConnection);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return;
                }

                final NewTransactionCallback newTransactionCallback = _newTransactionCallback;
                if (newTransactionCallback != null) {
                    _generalThreadPool.execute(new Runnable() {
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
                nodeFeatures.enableFeature(NodeFeatures.Feature.EXTENDED_DOUBLE_SPEND_PROOFS_ENABLED); // BitcoinVerde 2021-04-27
                return nodeFeatures;
            }
        };

        final NodeInitializer nodeInitializer;
        { // Initialize NodeInitializer...
            final NodeInitializer.TransactionsAnnouncementHandlerFactory transactionsAnnouncementHandlerFactory = new NodeInitializer.TransactionsAnnouncementHandlerFactory() {

                protected final BitcoinNode.DownloadTransactionCallback _downloadTransactionsCallback = new BitcoinNode.DownloadTransactionCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Transaction transaction) {
                        final Sha256Hash transactionHash = transaction.getHash();
                        Logger.debug("Received Transaction: " + transactionHash);

                        final BloomFilter walletBloomFilter = _wallet.getBloomFilter();
                        final AddressInflater addressInflater = _masterInflater.getAddressInflater();
                        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(walletBloomFilter, addressInflater);
                        if (! transactionBloomFilterMatcher.shouldInclude(transaction)) {
                            Logger.debug("Skipping Transaction that does not match filter: " + transactionHash);
                            return;
                        }

                        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                            final SpvDatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, maxQueryBatchSize, _checkpointConfiguration);
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
                            _generalThreadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    newTransactionCallback.onNewTransactionReceived(transaction);
                                }
                            });
                        }

                        if (Transaction.isSlpTransaction(transaction)) {
                            _synchronizeSlpValidity();
                        }
                    }
                };

                @Override
                public BitcoinNode.TransactionInventoryAnnouncementHandler createTransactionsAnnouncementHandler(final BitcoinNode bitcoinNode) {
                    return new BitcoinNode.TransactionInventoryAnnouncementHandler() {
                        @Override
                        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactions) {
                            Logger.debug("Received " + transactions.getCount() + " transaction inventories.");

                            final MutableList<Sha256Hash> unseenTransactions = new MutableList<Sha256Hash>(transactions.getCount());
                            try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
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

                            Logger.debug(unseenTransactions.getCount() + " transactions were new.");
                            if (! unseenTransactions.isEmpty()) {
                                bitcoinNode.requestTransactions(unseenTransactions, _downloadTransactionsCallback);
                            }
                        }

                        @Override
                        public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes, final Boolean isValid) {
                            this.onResult(bitcoinNode, transactionHashes);

                            try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                                final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
                                final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

                                final TransactionValidityChangedCallback transactionValidityChangedCallback = Util.coalesce(_transactionValidityChangedCallback, IGNORE_TRANSACTION_VALIDITY_CHANGED_CALLBACK);

                                TransactionUtil.startTransaction(databaseConnection);
                                Logger.info("Marking " + transactionHashes.getCount() + " SLP transactions as " + (isValid ? "valid" : "invalid"));
                                for (final Sha256Hash transactionHash : transactionHashes) {
                                    if (isValid) {
                                        _wallet.markSlpTransactionAsValid(transactionHash);
                                    }
                                    else {
                                        _wallet.markSlpTransactionAsInvalid(transactionHash);
                                    }

                                    final SlpValidity slpValidity = (isValid ? SlpValidity.VALID : SlpValidity.INVALID);

                                    final TransactionId transactionId = transactionDatabaseManager.getTransactionId(transactionHash);
                                    if (transactionId != null) {
                                        final SlpValidity currentSlpValidity = transactionDatabaseManager.getSlpValidity(transactionId);
                                        if (currentSlpValidity != slpValidity) {
                                            transactionDatabaseManager.setSlpValidity(transactionId, slpValidity);
                                            transactionValidityChangedCallback.onTransactionValidityChanged(transactionHash, slpValidity);
                                        }
                                    }
                                }
                                TransactionUtil.commitTransaction(databaseConnection);
                            }
                            catch (final DatabaseException exception) {
                                Logger.warn("Problem tracking SLP validity", exception);
                            }
                        }
                    };
                }
            };

            final RequestBlockHeadersHandler requestBlockHeadersHandler = new RequestBlockHeadersHandler(databaseManagerFactory);
            final BitcoinNode.RequestPeersHandler requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<BitcoinNodeIpAddress>(connectedNodes.getCount());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {

                        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
                        bitcoinNodeIpAddress.setNodeFeatures(bitcoinNode.getNodeFeatures());

                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            final BitcoinBinaryPacketFormat binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;

            final NodeInitializer.Context nodeInitializerContext = new NodeInitializer.Context();
            nodeInitializerContext.synchronizationStatus = synchronizationStatusHandler;
            nodeInitializerContext.blockInventoryMessageHandler = new BitcoinNode.BlockInventoryAnnouncementHandler() {
                @Override
                public void onNewInventory(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
                    if (! _bitcoinNodeManager.hasBloomFilter()) { return; }

                    // Only restart the synchronization process if it has already successfully completed.
                    _merkleBlockDownloader.wakeUp();
                }

                @Override
                public void onNewHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                    final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(blockHeaders.getCount());
                    for (final BlockHeader blockHeader : blockHeaders) {
                        final Sha256Hash blockHash = blockHeader.getHash();
                        blockHashes.add(blockHash);
                    }

                    this.onNewInventory(bitcoinNode, blockHashes);
                }
            };
            nodeInitializerContext.threadPoolFactory = threadPoolFactory;
            nodeInitializerContext.localNodeFeatures = localNodeFeatures;
            nodeInitializerContext.transactionsAnnouncementHandlerFactory = transactionsAnnouncementHandlerFactory;
            nodeInitializerContext.requestBlockHashesHandler = null;
            nodeInitializerContext.requestBlockHeadersHandler = requestBlockHeadersHandler;
            nodeInitializerContext.requestDataHandler = _spvRequestDataHandler;
            nodeInitializerContext.requestSpvBlocksHandler = null;
            nodeInitializerContext.requestSlpTransactionsHandler = null;
            nodeInitializerContext.requestUnconfirmedTransactionsHandler = null;

            nodeInitializerContext.requestPeersHandler = requestPeersHandler;
            nodeInitializerContext.spvBlockInventoryAnnouncementHandler = _merkleBlockDownloader;

            nodeInitializerContext.requestPeersHandler = new BitcoinNode.RequestPeersHandler() {
                @Override
                public List<BitcoinNodeIpAddress> getConnectedPeers() {
                    final List<BitcoinNode> connectedNodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<BitcoinNodeIpAddress> nodeIpAddresses = new ImmutableListBuilder<BitcoinNodeIpAddress>(connectedNodes.getCount());
                    for (final BitcoinNode bitcoinNode : connectedNodes) {
                        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
                        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
                        bitcoinNodeIpAddress.setNodeFeatures(bitcoinNode.getNodeFeatures());

                        nodeIpAddresses.add(bitcoinNodeIpAddress);
                    }
                    return nodeIpAddresses.build();
                }
            };

            nodeInitializerContext.binaryPacketFormat = binaryPacketFormat;
            nodeInitializerContext.newBloomFilterHandler = null;


            nodeInitializer = new NodeInitializer(nodeInitializerContext);
        }

        _bitcoinNodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPoolFactory, localNodeFeatures);

        { // Initialize DifficultyCalculatorFactory...
            if (_isTestNet) {
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
                context.minNodeCount = Math.max(1, _maxPeerCount);
                context.maxNodeCount = Math.max(1, _maxPeerCount);
                context.networkTime = _mutableNetworkTime;
                context.nodeInitializer = nodeInitializer;
                context.banFilter = _banFilter;
                context.memoryPoolEnquirer = null;
                context.synchronizationStatusHandler = synchronizationStatusHandler;
                context.threadPool = _generalThreadPool;
            }

            _bitcoinNodeManager = new BitcoinNodeManager(context);
            final Integer externalPort = (_isTestNet ? BitcoinConstants.getDefaultTestNetworkPort() : BitcoinConstants.getDefaultNetworkPort());
            _bitcoinNodeManager.setDefaultExternalPort(externalPort);
            _bitcoinNodeManager.enableTransactionRelay(false);
            _bitcoinNodeManager.enableSlpValidityChecking(true);
            _bitcoinNodeManager.setShouldOnlyConnectToSeedNodes(_shouldOnlyConnectToSeedNodes);

            for (final NodeProperties nodeProperties : _seedNodes) {
                final NodeIpAddress nodeIpAddress = NodeProperties.toNodeIpAddress(nodeProperties);
                if (nodeIpAddress == null) { continue; }

                _bitcoinNodeManager.defineSeedNode(nodeIpAddress);
                Logger.debug("Defined Seed Node: " + nodeIpAddress);
            }

            final List<String> dnsSeeds = new ImmutableList<String>("seed.bchd.cash", "seed-bch.bitcoinforks.org", "btccash-seeder.bitcoinunlimited.info", "seed.flowee.cash");
            _bitcoinNodeManager.defineDnsSeeds(dnsSeeds);
        }

        { // Initialize BlockHeaderDownloader...
            final BlockHeaderDownloaderContext blockHeaderDownloaderContext = new BlockHeaderDownloaderContext(_bitcoinNodeManager, databaseManagerFactory, _difficultyCalculatorFactory, _mutableNetworkTime, _systemTime, _generalThreadPool, upgradeSchedule);
            _blockHeaderDownloader = new BlockHeaderDownloader(blockHeaderDownloaderContext, null);
            _blockHeaderDownloader.setMinBlockTimestamp(_systemTime.getCurrentTimeInSeconds());
        }

        { // Initialize SpvSlpValidator
            _spvSlpTransactionValidator = new SpvSlpTransactionValidator(databaseManagerFactory, _bitcoinNodeManager);
        }

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final DatabaseManager databaseManager = new SpvDatabaseManager(databaseConnection, maxQueryBatchSize, _checkpointConfiguration);
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

        _blockHeaderDownloader.setNewBlockHeaderAvailableCallback(new BlockHeaderDownloader.NewBlockHeadersAvailableCallback() {
            @Override
            public void onNewHeadersReceived(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
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
        _bitcoinNodeManager.start();

        Logger.info("[Starting SPV SLP Validator]");
        _spvSlpTransactionValidator.start();

        Logger.info("[Starting Header Downloader]");
        _blockHeaderDownloader.start();

        while (! Thread.interrupted()) { // NOTE: Clears the isInterrupted flag for subsequent checks...
            try { Thread.sleep(15000); } catch (final Exception exception) { break; }

            _blockHeaderDownloader.wakeUp();
            _spvSlpTransactionValidator.wakeUp();
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

    public void setTransactionValidityChangedCallback(final TransactionValidityChangedCallback transactionValidityChangedCallback) {
        _transactionValidityChangedCallback = transactionValidityChangedCallback;
    }

    public Status getStatus() {
        return _status;
    }

    public BitcoinNodeManager getBitcoinNodeManager() {
        return _bitcoinNodeManager;
    }

    public void storeTransaction(final Transaction transaction) throws DatabaseException {
        try (final SpvDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final SpvTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            transactionDatabaseManager.storeTransaction(transaction);
        }

        _wallet.addTransaction(transaction);
    }

    public void broadcastTransaction(final Transaction transaction) {
        _spvRequestDataHandler.addSpvTransaction(transaction);

        final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>(1);
        transactionHashes.add(transaction.getHash());

        for (final BitcoinNode bitcoinNode : _bitcoinNodeManager.getNodes()) {
            Logger.info("Sending Tx Hash " + transaction.getHash() + " to " + bitcoinNode.getConnectionString());
            bitcoinNode.transmitTransactionHashes(transactionHashes);
        }

        _synchronizeSlpValidity();
    }

    public void setMerkleBlockSyncUpdateCallback(final MerkleBlockSyncUpdateCallback merkleBlockSyncUpdateCallback) {
        _merkleBlockSyncUpdateCallback = merkleBlockSyncUpdateCallback;
    }

    public void synchronizeMerkleBlocks() {
        _synchronizeMerkleBlocks();
    }

    public void synchronizeSlpValidity() {
        _synchronizeSlpValidity();
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

    /**
     * Should be called whenever an external addition/removal to the internal wallet's keys occurs.
     *  This function updates the node connections' bloom dilter.
     */
    public void onWalletKeysUpdated() {
        final MutableBloomFilter bloomFilter = _wallet.generateBloomFilter();
        _bitcoinNodeManager.setBloomFilter(bloomFilter);
    }
}

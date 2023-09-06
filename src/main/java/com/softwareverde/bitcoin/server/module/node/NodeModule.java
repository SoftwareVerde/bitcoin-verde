package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputFileDbManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.constable.set.mutable.MutableSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Promise;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeModule {
    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected final Long _maxTimeoutMs = 30000L;
    protected final BitcoinProperties _bitcoinProperties;
    protected final File _blockchainFile;
    protected final Blockchain _blockchain;
    protected final UnspentTransactionOutputFileDbManager _unspentTransactionOutputDatabaseManager;
    protected final TransactionIndexer _transactionIndexer;
    protected final PendingBlockStoreCore _blockStore;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;
    protected final DifficultyCalculator _difficultyCalculator;
    protected final WorkerManager _transactionIndexWorker;
    protected final WorkerManager _blockDownloadWorker;
    protected final WorkerManager _syncWorker;

    protected final MutableList<BitcoinNode> _bitcoinNodes = new MutableArrayList<>();
    protected final MutableHashSet<NodeIpAddress> _availablePeers = new MutableHashSet<>();
    protected int _attemptsWithEmptyIps = 0;
    protected final MutableHashSet<Ip> _previouslyConnectedIps = new MutableHashSet<>();
    protected final PendingBlockQueue _downloadedBlocks = new PendingBlockQueue(10);

    protected final Pin _shutdownPin = new Pin();
    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected void _syncHeaders() {
        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);

        final BitcoinNode.DownloadBlockHeadersCallback downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> response) {
                if (_isShuttingDown.get()) { return; }

                int count = 0;
                boolean hadInvalid = false;
                for (final BlockHeader blockHeader : response) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Long blockHeight = _blockchain.getHeadBlockHeaderHeight();

                    final Long existingBlockHeight = _blockchain.getBlockHeight(blockHash);
                    if (existingBlockHeight == null) {
                        if (blockHeight > 0L) {
                            final BlockHeaderValidator.BlockHeaderValidationResult validationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight + 1L);
                            if (! validationResult.isValid) {
                                bitcoinNode.disconnect();
                                Logger.debug(validationResult.errorMessage + " " + blockHash);
                                hadInvalid = true;
                                break;
                            }
                        }

                        final Boolean result = _blockchain.addBlockHeader(blockHeader);
                        if (! result) {
                            bitcoinNode.disconnect();
                            Logger.debug("Rejected: " + blockHash);
                            hadInvalid = true;
                            break;
                        }

                        count += 1;

                        final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
                        bitcoinNode.setBlockHeight(Math.max(blockHeight, nodeBlockHeight));
                    }
                    else { // BlockHeader is already known.
                        final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
                        bitcoinNode.setBlockHeight(Math.max(existingBlockHeight, nodeBlockHeight));
                    }
                }

                final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                Logger.info("Head: " + headBlockHash + " " + _blockchain.getHeadBlockHeaderHeight());

                if (count > 0 && (! hadInvalid)) {
                    if (count < RequestBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) {
                        Logger.debug("Block Headers Complete.");
                        try {
                            _blockchain.save(_blockchainFile);
                        }
                        catch (final Exception exception) {
                            Logger.debug(exception);
                        }
                        _syncBlocks();
                    }
                    else {
                        bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), this, RequestPriority.NORMAL);
                    }
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                _syncBlocks();
            }
        };

        final BitcoinNode bitcoinNode;
        synchronized (_bitcoinNodes) {
            if (_bitcoinNodes.isEmpty()) { return; }
            bitcoinNode = _bitcoinNodes.get(0);
        }

        _syncWorker.offerTask(new WorkerManager.Task() {
            @Override
            public void run() {
                final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();
                bitcoinNode.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(headBlockHash), downloadBlockHeadersCallback, RequestPriority.NORMAL);
            }
        });
    }

    protected UnspentTransactionOutputContext _getUnspentTransactionOutputContext(final Block block) throws Exception {
        final Sha256Hash blockHash = block.getHash();
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        final MutableUnspentTransactionOutputSet transactionOutputSet = new MutableUnspentTransactionOutputSet();
        transactionOutputSet.loadOutputsForBlock(_blockchain, block, blockHeight, _upgradeSchedule);
        return transactionOutputSet;
    }

    protected Promise<Block> _downloadBlock(final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
        final Promise<Block> promise = new Promise<>();

        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight != null) {
            final Promise<Block> existingPromise = _downloadedBlocks.getBlock(blockHeight);
            if (existingPromise != null) {
                final Block block = existingPromise.pollResult();
                if (block != null) {
                    return existingPromise;
                }
            }

            _downloadedBlocks.addBlock(blockHeight, promise);
        }

        _blockDownloadWorker.submitTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_blockStore.pendingBlockExists(blockHash)) {
                    final ByteArray byteArray = _blockStore.getPendingBlockData(blockHash);
                    final Block block = (new BlockInflater()).fromBytes(byteArray);
                    if (block != null) {
                        Logger.debug("Pending block exists: " + blockHash);
                        promise.setResult(block);
                        return;
                    }
                }

                final Container<Integer> failCountContainer = new Container<>(0);
                final NanoTimer downloadTimer = new NanoTimer();
                downloadTimer.start();
                bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                        if (_isShuttingDown.get()) { return; }
                        downloadTimer.stop();

                        Logger.debug("Downloaded " + blockHash + " in " + downloadTimer.getMillisecondsElapsed() + "ms.");
                        failCountContainer.value = 0;

                        promise.setResult(block);
                    }

                    @Override
                    public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                        Logger.info("Failed to receive Block: " + blockHash + " " + bitcoinNode);
                        failCountContainer.value += 1;
                        if (failCountContainer.value > 1) {
                            promise.setResult(null);
                            bitcoinNode.disconnect();
                        }
                        else {
                            bitcoinNode.requestBlock(blockHash, this, RequestPriority.NORMAL);
                            Logger.debug("Re-requested: " + blockHash + " from " + bitcoinNode);
                        }
                    }
                }, RequestPriority.NORMAL);
                Logger.debug("Requested: " + blockHash + " from " + bitcoinNode);
            }
        });
        return promise;
    }

    protected Promise<Block> _downloadBlocks(final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
        final Promise<Block> requestedPromise = _downloadBlock(bitcoinNode, blockHash);

        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        if (blockHeight != null) {
            final int queueSize = 10;
            for (int i = 0; i < queueSize; ++i) {
                final long nextBlockHeight = blockHeight + i;
                if (_downloadedBlocks.containsBlock(nextBlockHeight)) { continue; }

                final BlockHeader nextBlockHeader = _blockchain.getBlockHeader(nextBlockHeight);
                if (nextBlockHeader == null) { break; }

                final Sha256Hash nextBlockHash = nextBlockHeader.getHash();

                final BitcoinNode concurrentBitcoinNode;
                synchronized (_bitcoinNodes) {
                    final int nodeCount = _bitcoinNodes.getCount();
                    if (nodeCount == 0) { break; }

                    concurrentBitcoinNode = _bitcoinNodes.get(i % nodeCount);
                }

                _downloadBlock(concurrentBitcoinNode, nextBlockHash);
            }
        }

        return requestedPromise;
    }

    protected void _syncBlocks() {
        final BitcoinNode bitcoinNode;
        synchronized (_bitcoinNodes) {
            if (_bitcoinNodes.isEmpty()) { return; }
            bitcoinNode = _bitcoinNodes.get(0);
        }

        _syncWorker.offerTask(new WorkerManager.Task() {
            @Override
            public void run() {
                Logger.info("Syncing blocks.");

                final long trustedBlockHeight = _bitcoinProperties.getTrustedBlockHeight();
                final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);
                final BlockValidator blockValidator = new BlockValidator(_upgradeSchedule, _blockchain, _networkTime, blockHeaderValidator);

                long blockHeight = _blockchain.getHeadBlockHeight() + 1L;
                BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);

                while (blockHeader != null) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Promise<Block> promise = _downloadBlocks(bitcoinNode, blockHash);

                    try {
                        final Block block = promise.getResult(_maxTimeoutMs);
                        if (block == null) { break; }

                        Logger.debug("Processing: " + blockHash);
                        final NanoTimer utxoTimer = new NanoTimer();
                        final NanoTimer validationTimer = new NanoTimer();
                        final NanoTimer addBlockTimer = new NanoTimer();
                        final NanoTimer applyBlockTimer = new NanoTimer();

                        if (blockHeight > trustedBlockHeight) {
                            utxoTimer.start();
                            final UnspentTransactionOutputContext unspentTransactionOutputContext = _getUnspentTransactionOutputContext(block);
                            utxoTimer.stop();
                            validationTimer.start();
                            final BlockValidationResult result = blockValidator.validateBlock(block, unspentTransactionOutputContext);
                            validationTimer.stop();
                            if (! result.isValid) {
                                Logger.info(result.errorMessage + " " + block.getHash() + " " + result.invalidTransactions.get(0) + " (" + result.invalidTransactions.getCount() + ")");
                                bitcoinNode.disconnect();
                                break;
                            }

                            Logger.info("Valid: " + blockHeight + " " + blockHash);
                        }
                        else {
                            Logger.info("Assumed Valid: " + blockHeight + " " + blockHash);
                        }

                        addBlockTimer.start();
                        _blockchain.addBlock(block);
                        addBlockTimer.stop();

                        applyBlockTimer.start();
                        _unspentTransactionOutputDatabaseManager.applyBlock(block, blockHeight);
                        applyBlockTimer.stop();

                        if (_transactionIndexer != null) {
                            final Long indexBlockHeight = blockHeight;
                            _transactionIndexWorker.submitTask(new WorkerManager.Task() {
                                @Override
                                public void run() {
                                    final NanoTimer indexTimer = new NanoTimer();
                                    indexTimer.start();
                                    try {
                                        _transactionIndexer.indexTransactions(block, indexBlockHeight);
                                    }
                                    catch (final Exception exception) {
                                        Logger.debug(exception);
                                    }
                                    indexTimer.stop();
                                    Logger.debug("Indexed " + blockHash + " in " + indexTimer.getMillisecondsElapsed() + "ms.");
                                }
                            });
                        }

                        Logger.debug("Finished: " + blockHeight + " " + blockHash + " " +
                            "utxoTimer=" + utxoTimer.getMillisecondsElapsed() + " " +
                            "validationTimer=" + validationTimer.getMillisecondsElapsed() + " " +
                            "addBlockTimer=" + addBlockTimer.getMillisecondsElapsed() + " " +
                            "applyBlockTimer=" + applyBlockTimer.getMillisecondsElapsed()
                        );
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                        bitcoinNode.disconnect();
                        return;
                    }

                    blockHeight += 1;
                    blockHeader = _blockchain.getBlockHeader(blockHeight);
                }
            }
        });
    }

    protected void _removeBitcoinNode(final BitcoinNode bitcoinNode) {
        final NodeId nodeId = bitcoinNode.getId();
        synchronized (_bitcoinNodes) {
            _bitcoinNodes.mutableVisit(new MutableList.MutableVisitor<>() {
                @Override
                public boolean run(final Container<BitcoinNode> bitcoinNodeContainer) {
                    if (!Util.areEqual(nodeId, bitcoinNodeContainer.value.getId())) { return true; }

                    bitcoinNodeContainer.value = null;
                    return false;
                }
            });
        }
    }

    protected void _addNewNodes(final int numberOfNodesToAttemptConnectionsTo) {
        final Integer defaultPort = _bitcoinProperties.getBitcoinPort();
        while (_availablePeers.isEmpty()) { // Connect to DNS seeded nodes...
            final MutableHashSet<String> uniqueConnectionStrings = new MutableHashSet<>();
            final List<String> dnsSeeds = _bitcoinProperties.getDnsSeeds();
            for (final String seedHost : dnsSeeds) {
                Logger.info("seedHost=" + seedHost);
                final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                if (seedIps == null) { continue; }

                for (final Ip ip : seedIps) {
                    final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, defaultPort);

                    if (_previouslyConnectedIps.contains(ip)) { continue; }
                    if (! Util.areEqual(BitcoinConstants.getDefaultNetworkPort(), nodeIpAddress.getPort())) { continue; }

                    final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                    final boolean isUnique = uniqueConnectionStrings.add(connectionString);
                    if (! isUnique) { continue; }

                    _availablePeers.add(nodeIpAddress);
                }
            }

            if (_availablePeers.isEmpty()) {
                _attemptsWithEmptyIps += 1;
                try { Thread.sleep(3000); }
                catch (final InterruptedException exception) { return; }

                // Clear the previously-connected list in case all seeds have been exhausted.
                if (_attemptsWithEmptyIps >= 5) {
                    _previouslyConnectedIps.clear();
                    _attemptsWithEmptyIps = 0;
                }
            }
        }

        final Container<Integer> newPeerCount = new Container<>(0);
        _availablePeers.mutableVisit(new MutableSet.MutableVisitor<>() {
            @Override
            public boolean run(final Container<NodeIpAddress> container) {
                final NodeIpAddress nodeIpAddress = container.value;

                final Ip ip = nodeIpAddress.getIp();
                if (ip == null) { return true; }

                _previouslyConnectedIps.add(ip);

                final String host = ip.toString();
                final Integer port = nodeIpAddress.getPort();
                final BitcoinNode bitcoinNode = new BitcoinNode(host, port, new LocalNodeFeatures() {
                    @Override
                    public NodeFeatures getNodeFeatures() {
                        final NodeFeatures nodeFeatures = new NodeFeatures();
                        nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                        nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                        nodeFeatures.enableFeature(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED);
                        nodeFeatures.enableFeature(NodeFeatures.Feature.MINIMUM_OF_TWO_DAYS_BLOCKCHAIN_ENABLED);
                        return nodeFeatures;
                    }
                });
                bitcoinNode.setNodeConnectedCallback(new Node.NodeConnectedCallback() {
                    @Override
                    public void onNodeConnected() {
                        Logger.info("Connected to: " + host + ":" + port);
                    }

                    @Override
                    public void onFailure() {
                        _removeBitcoinNode(bitcoinNode);
                        _addNewNodes(1);
                    }
                });

                bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
                    @Override
                    public void onNodeDisconnected() {
                        _removeBitcoinNode(bitcoinNode);

                        _addNewNodes(1);
                    }
                });

                bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
                    @Override
                    public void onHandshakeComplete() {
                        final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();

                        if (bitcoinNode.getBlockHeight() < _blockchain.getHeadBlockHeaderHeight()) {
                            Logger.debug("Disconnecting from behind peer.");
                            bitcoinNode.disconnect();
                            return;
                        }

                        bitcoinNode.setNodeAddressesReceivedCallback(new Node.NodeAddressesReceivedCallback() {
                            @Override
                            public void onNewNodeAddresses(final List<NodeIpAddress> nodeIpAddresses) {
                                for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                                    if (_previouslyConnectedIps.contains(nodeIpAddress.getIp())) { continue; }
                                    if (! Util.areEqual(BitcoinConstants.getDefaultNetworkPort(), nodeIpAddress.getPort())) { continue; }
                                    _availablePeers.add(nodeIpAddress);
                                }
                            }
                        });
                        // bitcoinNode.requestNodeAddresses();

                        _syncHeaders();
                    }
                });
                bitcoinNode.setUnsolicitedBlockReceivedCallback(new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                        final Sha256Hash blockHash = block.getHash();
                        final Long headBlockHeight = _blockchain.getHeadBlockHeight();
                        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
                        if (blockHeight == null) {
                            final Sha256Hash headBlockHeaderHash = _blockchain.getHeadBlockHeaderHash();
                            if (! Util.areEqual(headBlockHeaderHash, block.getPreviousBlockHash())) {
                                Logger.debug("Received unknown, unrequested Block: " + blockHash + " from " + bitcoinNode.toString() + "; disconnecting.");
                                bitcoinNode.disconnect();
                                return;
                            }
                            else {
                                // TODO: Add header.
                            }
                        }

                        if (Math.abs(blockHeight - headBlockHeight) > 10) {
                            Logger.debug("Received unsolicited, irrelevant Block: " + blockHash + " from " + bitcoinNode.toString() + "; disconnecting.");
                            bitcoinNode.disconnect();
                            return;
                        }

                        Logger.debug("Received unsolicited Block: " + blockHash + " from " + bitcoinNode.toString());
                        _downloadedBlocks.addBlock(blockHeight, new Promise<>(block));
                    }
                });

                Logger.info("Connecting to: " + host + ":" + port);
                bitcoinNode.connect();

                synchronized (_bitcoinNodes) {
                    _bitcoinNodes.add(bitcoinNode);
                }
                container.value = null; // _availablePeers.remove(nodeIpAddress);

                newPeerCount.value += 1;
                return (newPeerCount.value < numberOfNodesToAttemptConnectionsTo);
            }
        });
    }

    public NodeModule(final BitcoinProperties bitcoinProperties) {
        _bitcoinProperties = bitcoinProperties;

        final Thread mainThread = Thread.currentThread();
        mainThread.setName("Bitcoin Verde - Main");
        mainThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.error(throwable);
                }
                catch (final Throwable ignored) { }
            }
        });

        final File dataDirectory = new File(bitcoinProperties.getDataDirectory());
        _blockchainFile = new File(dataDirectory, "block-headers.dat");

        final NetworkType networkType = bitcoinProperties.getNetworkType();
        BitcoinConstants.configureForNetwork(networkType);

        _blockStore = new PendingBlockStoreCore(dataDirectory, true);
        try {
            final File utxoDbDirectory = new File(dataDirectory, "utxo");
            Logger.info("Loading FileDB");
            _unspentTransactionOutputDatabaseManager = new UnspentTransactionOutputFileDbManager(utxoDbDirectory);
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }

        _blockchain = new Blockchain(_blockStore, _unspentTransactionOutputDatabaseManager);
        try {
            _blockchain.load(_blockchainFile);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        switch (networkType) {
            case TEST_NET: {
                _upgradeSchedule = new TestNetUpgradeSchedule();
                _difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case TEST_NET4: {
                _upgradeSchedule = new TestNet4UpgradeSchedule();
                _difficultyCalculator = new TestNetDifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            case CHIP_NET: {
                _upgradeSchedule = new ChipNetUpgradeSchedule();
                _difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
            default: {
                _upgradeSchedule = new CoreUpgradeSchedule();
                _difficultyCalculator = new DifficultyCalculator(_blockchain, _upgradeSchedule);
            } break;
        }

        final AsertReferenceBlock asertReferenceBlock = BitcoinConstants.getAsertReferenceBlock();
        _blockchain.setAsertReferenceBlock(asertReferenceBlock);

        try {
            final File transactionIndexDbDirectory = new File(dataDirectory, "index");
            Logger.info("Loading Transaction Index");
            _transactionIndexer = new TransactionIndexer(transactionIndexDbDirectory, _blockchain);
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }

        _transactionIndexWorker = new WorkerManager(1, 128);
        _transactionIndexWorker.setName("Blockchain Indexer");
        _transactionIndexWorker.start();

        _syncWorker = new WorkerManager(1, 1);
        _syncWorker.setName("Blockchain Sync");
        _syncWorker.start();

        _blockDownloadWorker = new WorkerManager(1, 4);
        _blockDownloadWorker.setName("Blockchain Downloader");
        _blockDownloadWorker.start();

        _networkTime = new MutableNetworkTime();

        _addNewNodes(3);

        final Thread shutdownThread = new Thread() {
            @Override
            public void run() {
                try {
                    NodeModule.this.close();
                    _shutdownPin.waitForRelease();
                }
                catch (final Exception exception) {
                    exception.printStackTrace();
                }
            }
        };
        shutdownThread.setName("Shutdown Thread");

        final Runtime runtime = Runtime.getRuntime();
        runtime.addShutdownHook(shutdownThread);
    }

    public void loop() {
        while (! _isShuttingDown.get()) {
            try {
                Thread.sleep(1000L);
            }
            catch (final Exception exception) {
                break;
            }
            Logger.flush();
        }

        try {
            this.close();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }

    public void close() throws Exception {
        if (! _isShuttingDown.compareAndSet(false, true)) { return; }

        try {
            for (final BitcoinNode bitcoinNode : _bitcoinNodes) {
                bitcoinNode.disconnect();
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        try {
            _blockDownloadWorker.close();
            _syncWorker.close();
            _unspentTransactionOutputDatabaseManager.close();
            _transactionIndexWorker.close();
            _transactionIndexer.close();
            _blockchain.save(_blockchainFile);
        }
        finally {
            _shutdownPin.release();
        }
        Logger.debug("Shutdown complete.");
    }
}

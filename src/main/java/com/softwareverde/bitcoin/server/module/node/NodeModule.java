package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
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
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputEntryInflater;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputFileDbManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.MetadataHandler;
import com.softwareverde.bitcoin.server.module.node.store.DiskKeyValueStore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.btreedb.file.InputOutputFileCore;
import com.softwareverde.btreedb.file.OutputFile;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.Visitor;
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
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.network.socket.SocketServer;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Promise;
import com.softwareverde.util.TimedPromise;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeModule {
    protected static class KeyValues {
        public static final String INDEXED_BLOCK_HEIGHT = "indexedBlockHeight";
    }

    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected final Long _maxTimeoutMs = 30000L;
    protected final BitcoinProperties _bitcoinProperties;
    protected final File _blockchainFile;
    protected final Blockchain _blockchain;
    protected final DiskKeyValueStore _keyValueStore;
    protected final UnspentTransactionOutputFileDbManager _unspentTransactionOutputDatabaseManager;
    protected final TransactionIndexer _transactionIndexer;
    protected final PendingBlockStoreCore _blockStore;
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;
    protected final JsonSocketServer _jsonSocketServer;
    protected final NodeRpcHandler _rpcHandler;
    protected final DifficultyCalculator _difficultyCalculator;
    protected final WorkerManager _blockchainIndexerWorker;
    protected final WorkerManager _syncWorker;
    protected final WorkerManager _undoBlockWorker;
    protected final ReentrantReadWriteLock.WriteLock _blockProcessLock;
    protected final TransactionMempool _transactionMempool;
    protected final BlockchainSynchronizationStatusHandler _synchronizationStatusHandler;

    protected final MutableList<BitcoinNode> _bitcoinNodes = new MutableArrayList<>();
    protected final MutableHashSet<NodeIpAddress> _availablePeers = new MutableHashSet<>();
    protected int _attemptsWithEmptyIps = 0;
    protected final MutableHashSet<Ip> _previouslyConnectedIps = new MutableHashSet<>();
    protected final PendingBlockQueue _blockDownloader;

    protected final Pin _shutdownPin = new Pin();
    protected final AtomicBoolean _isShuttingDown = new AtomicBoolean(false);

    protected final Container<Long> _headBlockHeightContainer = new Container<>(0L);
    protected final Container<Long> _headBlockHeaderHeightContainer = new Container<>(0L);
    protected final Container<Long> _indexedBlockHeightContainer = new Container<>(0L);
    protected final WorkerManager.UnsafeTask _indexBlockTask;
    protected boolean _skipNetworking = false;

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

                _headBlockHeaderHeightContainer.value = _blockchain.getHeadBlockHeaderHeight();
                _headBlockHeightContainer.value = _blockchain.getHeadBlockHeight();

                _synchronizationStatusHandler.recalculateState();

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
                if (_isShuttingDown.get()) { return; }
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

    protected void _createUndoLog(final Long blockHeight, final Sha256Hash blockHash, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        _undoBlockWorker.submitTask(new WorkerManager.UnsafeTask() {
            @Override
            public void run() throws Exception {
                final UnspentTransactionOutputEntryInflater unspentTransactionOutputEntryInflater = new UnspentTransactionOutputEntryInflater();
                final File blockSubDirectory = _blockStore.getBlockDataDirectory(blockHeight);
                final File undoFile = new File(blockSubDirectory, blockHash + ".undo");
                try (final OutputFile outputFile = new InputOutputFileCore(undoFile)) {
                    outputFile.open();

                    final MutableHashSet<Sha256Hash> blockTransactions;
                    {
                        final int transactionCount = block.getTransactionCount();
                        blockTransactions = new MutableHashSet<>(transactionCount);

                        for (final Transaction transaction : block.getTransactions()) {
                            final Sha256Hash transactionHash = transaction.getHash();
                            blockTransactions.add(transactionHash);
                        }
                    }

                    final BlockUtxoDiff utxoDiff = BlockUtil.getBlockUtxoDiff(block);
                    for (final TransactionOutputIdentifier transactionOutputIdentifier : utxoDiff.spentTransactionOutputIdentifiers) {
                        final Sha256Hash transactionHash = transactionOutputIdentifier.getTransactionHash();
                        if (blockTransactions.contains(transactionHash)) {
                            continue; // Exclude outputs that are both created and destroyed by the block.
                        }

                        final TransactionOutput utxo = unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
                        final Long utxoBlockHeight = unspentTransactionOutputContext.getBlockHeight(transactionOutputIdentifier);
                        final Boolean utxoIsCoinbase = unspentTransactionOutputContext.isCoinbaseTransactionOutput(transactionOutputIdentifier);

                        final UnspentTransactionOutput unspentTransactionOutput = new MutableUnspentTransactionOutput(utxo, utxoBlockHeight, utxoIsCoinbase);

                        final ByteArray keyBytes = unspentTransactionOutputEntryInflater.keyToBytes(transactionOutputIdentifier);
                        final ByteArray valueBytes = unspentTransactionOutputEntryInflater.valueToBytes(unspentTransactionOutput);

                        outputFile.write(keyBytes.getBytes());
                        outputFile.write(valueBytes.getBytes());
                    }
                }
            }
        });
    }

    protected void _syncBlocks() {
        final BitcoinNode bitcoinNode;
        if (_skipNetworking) {
            bitcoinNode = null;
        }
        else {
            synchronized (_bitcoinNodes) {
                if (_bitcoinNodes.isEmpty()) { return; }
                bitcoinNode = _bitcoinNodes.get(0);
            }
        }

        _syncWorker.offerTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_isShuttingDown.get()) { return; }

                Logger.info("Syncing blocks.");

                final long trustedBlockHeight = _bitcoinProperties.getTrustedBlockHeight();
                final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);
                final BlockValidator blockValidator = new BlockValidator(_upgradeSchedule, _blockchain, _networkTime, blockHeaderValidator);

                long blockHeight = _blockchain.getHeadBlockHeight() + 1L;
                BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);

                while (blockHeader != null) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Promise<Block> promise = _blockDownloader.getBlock(blockHeight);

                    if (! _blockProcessLock.tryLock()) { break; } // Close in process.
                    try {
                        final Block block = promise.getResult(_maxTimeoutMs);
                        if (block == null) {
                            if (_isShuttingDown.get()) { return; }

                            Thread.sleep(500L);
                            continue;
                        }
                        if (_isShuttingDown.get()) { return; }

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
                                Logger.info(result.errorMessage + " " + blockHash + " " + result.invalidTransactions.get(0) + " (" + result.invalidTransactions.getCount() + ")");
                                if (bitcoinNode != null) {
                                    bitcoinNode.disconnect();
                                }
                                break;
                            }

                            _createUndoLog(blockHeight, blockHash, block, unspentTransactionOutputContext);

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

                        _headBlockHeaderHeightContainer.value = _blockchain.getHeadBlockHeaderHeight();
                        _headBlockHeightContainer.value = _blockchain.getHeadBlockHeight();

                        _transactionMempool.revalidate();

                        if (_transactionIndexer != null) {
                            // asdf _blockchainIndexerWorker.offerTask(_indexBlockTask);
                        }

                        _synchronizationStatusHandler.recalculateState();
                        _blockDownloader.onBlockProcessed();

                        Logger.debug("Finished: " + blockHeight + " " + blockHash + " " +
                            "utxoTimer=" + utxoTimer.getMillisecondsElapsed() + " " +
                            "validationTimer=" + validationTimer.getMillisecondsElapsed() + " " +
                            "addBlockTimer=" + addBlockTimer.getMillisecondsElapsed() + " " +
                            "applyBlockTimer=" + applyBlockTimer.getMillisecondsElapsed()
                        );
                    }
                    catch (final InterruptedException exception) {
                        return;
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                        if (bitcoinNode != null) {
                            bitcoinNode.disconnect();
                        }
                        return;
                    }
                    finally {
                        _blockProcessLock.unlock();
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

    protected void _connectToNode(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        if (ip == null) { return; }

        synchronized (_previouslyConnectedIps) {
            _previouslyConnectedIps.add(ip);
        }

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
        bitcoinNode.setSynchronizationStatusHandler(_synchronizationStatusHandler);
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
                            synchronized (_previouslyConnectedIps) {
                                if (_previouslyConnectedIps.contains(nodeIpAddress.getIp())) {
                                    continue;
                                }
                            }

                            if (! Util.areEqual(BitcoinConstants.getDefaultNetworkPort(), nodeIpAddress.getPort())) { continue; }

                            synchronized (NodeModule.this) {
                                _availablePeers.add(nodeIpAddress);
                            }
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

                if (Math.abs(blockHeight - headBlockHeight) > 20) {
                    Logger.debug("Received unsolicited, irrelevant Block: " + blockHash + " from " + bitcoinNode.toString() + "; disconnecting.");
                    bitcoinNode.disconnect();
                    return;
                }

                Logger.debug("Received unsolicited Block: " + blockHash + " from " + bitcoinNode.toString());
                _blockDownloader.addBlock(blockHeight, new TimedPromise<>(block));
            }
        });

        bitcoinNode.setTransactionsAnnouncementCallback(new BitcoinNode.TransactionInventoryAnnouncementHandler() {
            @Override
            public void onResult(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
                final boolean isSynced = Util.areEqual(_blockchain.getHeadBlockHeaderHeight(), _blockchain.getHeadBlockHeight());
                if (! isSynced) { return; }

                final MutableList<Sha256Hash> unseenTransactions = new MutableArrayList<>();
                for (final Sha256Hash transactionHash : transactionHashes) {
                    if (! _transactionMempool.contains(transactionHash)) {
                        unseenTransactions.add(transactionHash);
                    }
                }
                if (unseenTransactions.isEmpty()) { return; }

                bitcoinNode.requestTransactions(unseenTransactions, new BitcoinNode.DownloadTransactionCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Transaction transaction) {
                        _transactionMempool.addTransaction(transaction);
                    }
                });
            }
        });

        Logger.info("Connecting to: " + host + ":" + port);
        bitcoinNode.connect();

        synchronized (_bitcoinNodes) {
            _bitcoinNodes.add(bitcoinNode);
        }
    }

    protected synchronized void _addNewNodes(final int numberOfNodesToAttemptConnectionsTo) {
        if (_isShuttingDown.get()) { return; }
        if (_skipNetworking) { return; }

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

                    synchronized (_previouslyConnectedIps) {
                        if (_previouslyConnectedIps.contains(ip)) {
                            continue;
                        }
                    }

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
                    synchronized (_previouslyConnectedIps) {
                        _previouslyConnectedIps.clear();
                    }
                    _attemptsWithEmptyIps = 0;
                }
            }
        }

        final Container<Integer> newPeerCount = new Container<>(0);
        _availablePeers.mutableVisit(new MutableSet.MutableVisitor<>() {
            @Override
            public boolean run(final Container<NodeIpAddress> container) {
                if (_isShuttingDown.get()) { return false; }

                final NodeIpAddress nodeIpAddress = container.value;
                if (nodeIpAddress.getIp() == null) { return true; }

                _connectToNode(nodeIpAddress);

                container.value = null;

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

        final File keyValueFile = new File(dataDirectory, "key-values.dat");
        _keyValueStore = new DiskKeyValueStore(new InputOutputFileCore(keyValueFile));
        try {
            _keyValueStore.open();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
        // TODO: Remove.
        if (! _keyValueStore.hasKey(KeyValues.INDEXED_BLOCK_HEIGHT)) {
            _keyValueStore.putString(KeyValues.INDEXED_BLOCK_HEIGHT, "" + _blockchain.getHeadBlockHeight());
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

        _networkTime = new MutableNetworkTime();

        _transactionMempool = new TransactionMempool(_blockchain, _upgradeSchedule, _networkTime, _unspentTransactionOutputDatabaseManager);

        _indexBlockTask = new WorkerManager.Task() {
            @Override
            public void run() {
                while (true) {
                    if (_isShuttingDown.get()) { return; }
                    final Long lastIndexedBlockHeight = Util.parseLong(_keyValueStore.getString(KeyValues.INDEXED_BLOCK_HEIGHT), 0L);
                    final Long blockHeight = lastIndexedBlockHeight + 1L;

                    final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
                    if (blockHeader == null) { return; }

                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Block block = _blockStore.getBlock(blockHash, blockHeight);
                    if (block == null) { return; }

                    final NanoTimer indexTimer = new NanoTimer();
                    indexTimer.start();
                    try {
                        _transactionIndexer.indexTransactions(block, blockHeight);
                        _keyValueStore.putString(KeyValues.INDEXED_BLOCK_HEIGHT, "" + blockHeight);
                        _indexedBlockHeightContainer.value = blockHeight;
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                    }
                    indexTimer.stop();
                    Logger.debug("Indexed " + blockHash + " in " + indexTimer.getMillisecondsElapsed() + "ms.");
                }
            }
        };

        _indexedBlockHeightContainer.value = Util.parseLong(_keyValueStore.getString(KeyValues.INDEXED_BLOCK_HEIGHT), 0L);
        _headBlockHeaderHeightContainer.value = _blockchain.getHeadBlockHeaderHeight();
        _headBlockHeightContainer.value = _blockchain.getHeadBlockHeight();

        _blockchainIndexerWorker = new WorkerManager(1, 128);
        _blockchainIndexerWorker.setName("Blockchain Indexer");
        _blockchainIndexerWorker.start();

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _blockProcessLock = readWriteLock.writeLock();

        _syncWorker = new WorkerManager(1, 1);
        _syncWorker.setName("Blockchain Sync");
        _syncWorker.start();

        _undoBlockWorker = new WorkerManager(2, 64);
        _undoBlockWorker.setName("Undo Log");
        _undoBlockWorker.start();

        _synchronizationStatusHandler = new BlockchainSynchronizationStatusHandler(_blockchain);
        final BlockchainDataHandler blockchainDataHandler = new BlockchainDataHandler(_blockchain, _blockStore, _upgradeSchedule, _transactionIndexer, _transactionMempool, _unspentTransactionOutputDatabaseManager);
        final BlockchainQueryAddressHandler queryAddressHandler = new BlockchainQueryAddressHandler(_blockchain, _transactionIndexer, _transactionMempool);
        final NodeRpcHandler.MetadataHandler metadataHandler = new MetadataHandler(_blockchain, _transactionIndexer, null);
        final NodeRpcHandler.NodeHandler nodeHandler = new NodeRpcHandler.NodeHandler() {
            @Override
            public void addNode(final Ip ip, final Integer port) {
                final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, port);
                _connectToNode(nodeIpAddress);
            }

            @Override
            public List<BitcoinNode> getNodes() {
                final MutableList<BitcoinNode> bitcoinNodes = new MutableList<>();
                synchronized (_bitcoinNodes) {
                    bitcoinNodes.addAll(_bitcoinNodes);
                }
                return bitcoinNodes;
            }

            @Override
            public Boolean isPreferredNode(final BitcoinNode bitcoinNode) {
                return false; // TODO
            }

            @Override
            public void banNode(final Ip ip) {
                synchronized (_previouslyConnectedIps) {
                    _previouslyConnectedIps.add(ip);
                }

                synchronized (_bitcoinNodes) {
                    _bitcoinNodes.visit(new Visitor<>() {
                        @Override
                        public boolean run(final BitcoinNode bitcoinNode) {
                            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                                bitcoinNode.disconnect();
                                return false;
                            }
                            return true;
                        }
                    });
                }
            }

            @Override
            public void unbanNode(final Ip ip) {
                synchronized (_previouslyConnectedIps) {
                    _previouslyConnectedIps.remove(ip);
                }
            }

            @Override
            public void addIpToWhitelist(final Ip ip) { }

            @Override
            public void removeIpFromWhitelist(final Ip ip) { }
        };
        blockchainDataHandler.setHeadBlockHeightContainer(_headBlockHeightContainer);
        blockchainDataHandler.setHeadBlockHeaderHeightContainer(_headBlockHeaderHeightContainer);
        blockchainDataHandler.setIndexedBlockHeightContainer(_indexedBlockHeightContainer);

        _blockDownloader = new PendingBlockQueue(_blockchain, _blockStore, new PendingBlockQueue.BitcoinNodeSelector() {
            @Override
            public BitcoinNode getBitcoinNode(final Long blockHeight) {
                synchronized (_bitcoinNodes) {
                    final long nodeCount = _bitcoinNodes.getCount();
                    if (nodeCount == 0L) { return null; }

                    final int index = (int) (blockHeight % nodeCount);
                    return _bitcoinNodes.get(index);
                }
            }
        });
        _blockDownloader.start();

        _rpcHandler = new NodeRpcHandler();
        _rpcHandler.setShutdownHandler(new NodeRpcHandler.ShutdownHandler() {
            @Override
            public Boolean shutdown() {
                try {
                    NodeModule.this.close();
                    return true;
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                    return false;
                }
            }
        });
        _rpcHandler.setDataHandler(blockchainDataHandler);
        _rpcHandler.setNodeHandler(nodeHandler);
        _rpcHandler.setMetadataHandler(metadataHandler);
        _rpcHandler.setQueryAddressHandler(queryAddressHandler);
        _rpcHandler.setSynchronizationStatusHandler(_synchronizationStatusHandler);

        _addNewNodes(3);

        if (_skipNetworking) {
            _syncBlocks();
        }

        _jsonSocketServer = new JsonSocketServer(_bitcoinProperties.getBitcoinRpcPort());
        _jsonSocketServer.setSocketConnectedCallback(new SocketServer.SocketConnectedCallback<>() {
            @Override
            public void run(final JsonSocket socketConnection) {
                _rpcHandler.run(socketConnection);
            }
        });

        _jsonSocketServer.start();

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

        _synchronizationStatusHandler.recalculateState();
        _blockchainIndexerWorker.offerTask(_indexBlockTask);
    }

    public void loop() {
        while (! _isShuttingDown.get()) {
            try {
                Thread.sleep(10000L);
                // System.gc();
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
        _blockProcessLock.lock();

        try {
            for (final BitcoinNode bitcoinNode : _bitcoinNodes) {
                bitcoinNode.disconnect();
            }
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        try {
            _jsonSocketServer.stop();
            _blockDownloader.stop();

            _syncWorker.waitForCompletion();
            _syncWorker.close();

            _unspentTransactionOutputDatabaseManager.close();

            _blockchainIndexerWorker.waitForCompletion();
            _blockchainIndexerWorker.close();

            _transactionIndexer.close();
            _blockchain.save(_blockchainFile);
            _keyValueStore.close();

            _undoBlockWorker.waitForCompletion();
            _undoBlockWorker.close();

            Logger.debug("Shutdown complete.");
            Logger.flush();
            Logger.close();
        }
        finally {
            _shutdownPin.release();
        }

        System.out.println("Shutdown complete.");
    }
}

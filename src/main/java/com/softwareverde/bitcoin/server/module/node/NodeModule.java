package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.bip.ChipNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNet4UpgradeSchedule;
import com.softwareverde.bitcoin.bip.TestNetUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockUtxoDiff;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.MutableChainWork;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.block.validator.difficulty.TestNetDifficultyCalculator;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.configuration.CheckpointConfiguration;
import com.softwareverde.bitcoin.server.configuration.ChipNetCheckpointConfiguration;
import com.softwareverde.bitcoin.server.configuration.DisabledCheckpointConfiguration;
import com.softwareverde.bitcoin.server.configuration.TestNetCheckpointConfiguration;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.main.NetworkType;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputEntryInflater;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputFileDbManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.rpc.handler.MetadataHandler;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.server.module.node.store.DiskKeyValueStore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStoreCore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledPublicKeyRequest;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledSha256HashRequest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.bitcoin.transaction.output.MutableUnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.UnspentTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.util.BlockUtil;
import com.softwareverde.btreedb.file.InputFile;
import com.softwareverde.btreedb.file.InputOutputFileCore;
import com.softwareverde.btreedb.file.OutputFile;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.constable.set.mutable.MutableSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Log;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;
import com.softwareverde.logging.filelog.AnnotatedFileLog;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.JsonSocket;
import com.softwareverde.network.socket.JsonSocketServer;
import com.softwareverde.network.socket.SocketServer;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Container;
import com.softwareverde.util.Function;
import com.softwareverde.util.TimedPromise;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NodeModule {
    public static class KeyValues {
        public static final String INDEXED_BLOCK_HEIGHT = "indexedBlockHeight";
        public static final String HEAD_BLOCK_HASH = "headBlockHash";
    }

    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final AtomicLong _timeAtLastBlock = new AtomicLong(0L);
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
    protected final WorkerManager _rpcWorkerManager;
    protected final ReentrantReadWriteLock.WriteLock _blockProcessLock;
    protected final TransactionMempool _transactionMempool;
    protected final CircleBuffer<Transaction> _submittedTransactions;
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

    protected final CircleBuffer<Double> _blockProcessMs = new CircleBuffer<>(100);
    protected final CircleBuffer<Double> _headerProcessMs = new CircleBuffer<>(100);
    protected final CircleBuffer<Double> _indexProcessMs = new CircleBuffer<>(100);

    /**
     * After execution, the head block header height will be equal to `endingBlockHeight`;
     *  if the blockchain is synced past `endingBlockHeight`, the UTXOs will also be undone.
     */
    protected void _undoToHeight(final long endingBlockHeight) {
        _blockProcessLock.lock();
        try {
            final long originalHeaderHeight = _blockchain.getHeadBlockHeaderHeight();
            final long undoDepth = (originalHeaderHeight - endingBlockHeight);
            long currentBlockHeight = _blockchain.getHeadBlockHeight();
            for (int i = 0; i < undoDepth; ++i) {
                final long preUndoHeaderHeight = _blockchain.getHeadBlockHeaderHeight();
                final Sha256Hash undoneBlockHash = _blockchain.getHeadBlockHeaderHash();

                _blockchain.undoHeadBlockHeader();
                Logger.info("UndoneHeader: " + undoneBlockHash);

                if (preUndoHeaderHeight == currentBlockHeight) {
                    final Block undoneBlock = _blockStore.getBlock(undoneBlockHash, preUndoHeaderHeight);
                    final BlockUtxoDiff blockUtxoDiff = BlockUtil.getBlockUtxoDiff(undoneBlock);
                    final Map<TransactionOutputIdentifier, UnspentTransactionOutput> destroyedUtxos = NodeModule.loadUndoLog(undoneBlockHash, _blockStore);
                    _unspentTransactionOutputDatabaseManager.undoBlock(blockUtxoDiff, destroyedUtxos);
                    Logger.debug("Applied UndoLog: " + undoneBlockHash);

                    currentBlockHeight -= 1L;
                }
            }

            // TODO: Undo any block-indexing that may have occurred.
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
        finally {
            _blockProcessLock.unlock();
        }
    }

    protected final BitcoinNode.DownloadBlockHeadersCallback _downloadBlockHeadersCallback;

    protected void _syncHeaders() {
        if (_isShuttingDown.get()) { return; }

        final BitcoinNode bitcoinNode;
        synchronized (_bitcoinNodes) {
            if (_bitcoinNodes.isEmpty()) { return; }
            bitcoinNode = _bitcoinNodes.get(0);
        }

        _syncWorker.offerTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_isShuttingDown.get()) { return; }
                final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(_blockchain);
                final List<Sha256Hash> blockHashes = blockFinderHashesBuilder.createBlockHeaderFinderBlockHashes();
                bitcoinNode.requestBlockHeadersAfter(blockHashes, _downloadBlockHeadersCallback, RequestPriority.NORMAL);
            }
        });
    }

    protected MutableUnspentTransactionOutputSet _getUnspentTransactionOutputContext(final Block block) throws Exception {
        final Sha256Hash blockHash = block.getHash();
        final Long blockHeight = _blockchain.getBlockHeight(blockHash);
        final MutableUnspentTransactionOutputSet transactionOutputSet = new MutableUnspentTransactionOutputSet();
        transactionOutputSet.loadOutputsForBlock(_blockchain, block, blockHeight, _upgradeSchedule);
        return transactionOutputSet;
    }

    public static Map<TransactionOutputIdentifier, UnspentTransactionOutput> loadUndoLog(final Sha256Hash blockHash, final BlockStore blockStore) throws Exception {
        final UnspentTransactionOutputEntryInflater unspentTransactionOutputEntryInflater = new UnspentTransactionOutputEntryInflater();
        final MutableHashMap<TransactionOutputIdentifier, UnspentTransactionOutput> destroyedUtxos = new MutableHashMap<>();

        final File blockSubDirectory = new File(blockStore.getBlockDataDirectory(), "undo");
        final File undoFile = new File(blockSubDirectory, blockHash.toString());
        try (final InputFile inputFile = new InputOutputFileCore(undoFile)) {
            inputFile.open();
            inputFile.setPosition(0L);

            final long fileByteCount = inputFile.getByteCount();

            final int keyByteCount = unspentTransactionOutputEntryInflater.getKeyByteCount();
            final MutableByteArray keyBuffer = new MutableByteArray(keyByteCount);
            final MutableByteArray intBuffer = new MutableByteArray(4);

            while (inputFile.getPosition() < fileByteCount) {
                inputFile.read(keyBuffer.unwrap());
                inputFile.read(intBuffer.unwrap());

                final int valueByteCount = ByteUtil.bytesToInteger(intBuffer);
                final MutableByteArray valueBytes = new MutableByteArray(valueByteCount);
                inputFile.read(valueBytes.unwrap());

                final TransactionOutputIdentifier transactionOutputIdentifier = unspentTransactionOutputEntryInflater.keyFromBytes(keyBuffer);
                final UnspentTransactionOutput transactionOutput = unspentTransactionOutputEntryInflater.valueFromBytes(valueBytes);
                destroyedUtxos.put(transactionOutputIdentifier, transactionOutput);
            }
        }

        return destroyedUtxos;
    }

    protected void _createUndoLog(final Sha256Hash blockHash, final Block block, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        _undoBlockWorker.submitTask(new WorkerManager.UnsafeTask() {
            @Override
            public void run() throws Exception {
                final UnspentTransactionOutputEntryInflater unspentTransactionOutputEntryInflater = new UnspentTransactionOutputEntryInflater();
                final File blockSubDirectory = new File(_blockStore.getBlockDataDirectory(), "undo");
                blockSubDirectory.mkdirs();

                final File undoFile = new File(blockSubDirectory, blockHash.toString());
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
                        outputFile.write(ByteUtil.integerToBytes(valueBytes.getByteCount()));
                        outputFile.write(valueBytes.getBytes());
                    }
                }
            }
        });
    }

    protected void _syncBlocks() {
        final boolean validateTrustedBlockUtxos = false;

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

                final NanoTimer blockProcessTimer = new NanoTimer();

                while (blockHeader != null) {
                    blockProcessTimer.start();

                    final Sha256Hash blockHash = blockHeader.getHash();
                    final TimedPromise<Block> promise = _blockDownloader.getBlock(blockHeight);

                    if (! _blockProcessLock.tryLock()) { break; } // Close in process.
                    try {
                        final Block block = promise.getResult(_maxTimeoutMs);
                        _blockDownloader.removeBlock(blockHeight);

                        if (block == null) {
                            _blockDownloader.purgeBlock(blockHeight);
                            if (_isShuttingDown.get()) { return; }

                            Logger.debug("Unable to get block, trying again in 500ms.");
                            Thread.sleep(500L);
                            continue;
                        }
                        if (_isShuttingDown.get()) { return; }

                        { // Check for a fork block by ensuring the block is the expected hash.
                            final Sha256Hash fullBlockHash = block.getHash();
                            if (! Util.areEqual(blockHash, fullBlockHash)) {
                                Logger.debug("Ignoring fork block: " + fullBlockHash + " conflicts with " + blockHash);
                                break;
                            }
                        }

                        Logger.debug("Processing: " + blockHash);
                        final NanoTimer utxoTimer = new NanoTimer();
                        final NanoTimer validationTimer = new NanoTimer();
                        final NanoTimer addBlockTimer = new NanoTimer();
                        final NanoTimer applyBlockTimer = new NanoTimer();
                        final NanoTimer miscTimer = new NanoTimer();

                        if (blockHeight > trustedBlockHeight) {
                            utxoTimer.start();
                            final UnspentTransactionOutputContext unspentTransactionOutputContext = _getUnspentTransactionOutputContext(block);
                            utxoTimer.stop();
                            validationTimer.start();
                            final BlockValidationResult result = blockValidator.validateBlock(block, unspentTransactionOutputContext);
                            validationTimer.stop();
                            if (! result.isValid) {
                                Logger.info(result.errorMessage + " " + blockHash + " " + (result.invalidTransactions.isEmpty() ? null : result.invalidTransactions.get(0)) + " (" + result.invalidTransactions.getCount() + ")");

                                if (result.invalidTransactions.isEmpty()) {
                                    // Block is likely corrupted since the BlockHeader was invalid.
                                    _blockStore.removePendingBlock(blockHash);
                                    continue;
                                }

                                break;
                            }

                            _createUndoLog(blockHash, block, unspentTransactionOutputContext);

                            Logger.info("Valid: " + blockHeight + " " + blockHash);
                        }
                        else {
                            if (! block.isValid()) {
                                _blockStore.removePendingBlock(blockHash);
                                continue;
                            }

                            if (validateTrustedBlockUtxos) {
                                utxoTimer.start();
                                final MutableUnspentTransactionOutputSet outputs = _getUnspentTransactionOutputContext(block);
                                if (! outputs.getMissingOutputs().isEmpty()) {
                                    Logger.info("Outputs not found: " + blockHeight + " " + blockHash);
                                    break;
                                }
                                utxoTimer.stop();
                            }

                            Logger.info("Assumed Valid: " + blockHeight + " " + blockHash);
                        }

                        addBlockTimer.start();
                        final boolean addBlockResult = _blockchain.addBlock(block);
                        addBlockTimer.stop();

                        if (! addBlockResult) {
                            Logger.debug("Unable to add block: " + blockHash);
                            break;
                        }

                        final long now = _systemTime.getCurrentTimeInMilliSeconds();
                        _timeAtLastBlock.set(now);

                        applyBlockTimer.start();
                        _unspentTransactionOutputDatabaseManager.applyBlock(block, blockHeight);
                        applyBlockTimer.stop();

                        miscTimer.start();
                        _headBlockHeaderHeightContainer.value = _blockchain.getHeadBlockHeaderHeight();
                        _headBlockHeightContainer.value = _blockchain.getHeadBlockHeight();

                        _transactionMempool.revalidate();

                        if (_transactionIndexer != null) {
                            _blockchainIndexerWorker.offerTask(_indexBlockTask);
                        }

                        _synchronizationStatusHandler.recalculateState();
                        _blockDownloader.onBlockProcessed();

                        final BlockHeader rpcBlockHeader = blockHeader;
                        _rpcWorkerManager.submitTask(new WorkerManager.Task() {
                            @Override
                            public void run() {
                                _rpcHandler.onNewBlock(rpcBlockHeader);
                            }
                        });
                        miscTimer.stop();

                        Logger.debug("Finished: " + blockHeight + " " + blockHash + " " +
                            "utxoTimer=" + utxoTimer.getMillisecondsElapsed() + " " +
                            "validationTimer=" + validationTimer.getMillisecondsElapsed() + " " +
                            "addBlockTimer=" + addBlockTimer.getMillisecondsElapsed() + " " +
                            "applyBlockTimer=" + applyBlockTimer.getMillisecondsElapsed() + " " +
                            "getBlockTimer=" + promise.getMsElapsed() + " " +
                            "miscTimer=" + miscTimer.getMillisecondsElapsed()
                        );
                    }
                    catch (final InterruptedException exception) {
                        return;
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                        return;
                    }
                    finally {
                        _blockProcessLock.unlock();
                    }

                    blockHeight += 1;
                    blockHeader = _blockchain.getBlockHeader(blockHeight);

                    blockProcessTimer.stop();
                    synchronized (_blockProcessMs) {
                        _blockProcessMs.push(blockProcessTimer.getMillisecondsElapsed());
                    }
                }

                if (! _synchronizationStatusHandler.isBlockchainSynchronized()) {
                    _syncHeaders();
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
                if (_isShuttingDown.get()) { return; }

                Logger.info("Connected to: " + host + ":" + port);
            }

            @Override
            public void onFailure() {
                if (_isShuttingDown.get()) { return; }

                _removeBitcoinNode(bitcoinNode);
                _addNewNodes(1);
            }
        });

        bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                if (_isShuttingDown.get()) { return; }

                _removeBitcoinNode(bitcoinNode);

                _addNewNodes(1);
            }
        });

        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                if (_isShuttingDown.get()) { return; }

                if (bitcoinNode.getBlockHeight() < _blockchain.getHeadBlockHeaderHeight()) {
                    Logger.debug("Disconnecting from behind peer.");
                    bitcoinNode.disconnect();
                    return;
                }

                bitcoinNode.setNodeAddressesReceivedCallback(new Node.NodeAddressesReceivedCallback() {
                    @Override
                    public void onNewNodeAddresses(final List<NodeIpAddress> nodeIpAddresses) {
                        if (_isShuttingDown.get()) { return; }

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

                _syncHeaders();
            }
        });
        bitcoinNode.setUnsolicitedBlockReceivedCallback(new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                if (_isShuttingDown.get()) { return; }

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
                if (_isShuttingDown.get()) { return; }

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
                        final boolean wasAccepted = _transactionMempool.addTransaction(transaction);

                        if (wasAccepted) {
                            final Sha256Hash transactionHash = transaction.getHash();
                            final TransactionWithFee transactionWithFee = _transactionMempool.getTransaction(transactionHash);
                            _rpcHandler.onNewTransaction(transactionWithFee);
                        }
                    }
                });
            }
        });

        bitcoinNode.setRequestDataHandler(new BitcoinNode.RequestDataHandler() {
            @Override
            public void run(final BitcoinNode bitcoinNode, final List<InventoryItem> dataHashes) {
                if (_isShuttingDown.get()) { return; }

                for (final InventoryItem inventoryItem : dataHashes) {
                    final Sha256Hash itemHash = inventoryItem.getItemHash();
                    if (inventoryItem.getItemType() == InventoryItemType.TRANSACTION) {
                        if (_transactionMempool.contains(itemHash)) {
                            final Transaction transaction = _transactionMempool.getTransaction(itemHash).transaction;
                            bitcoinNode.transmitTransaction(transaction);
                        }
                        else {
                            synchronized (_submittedTransactions) {
                                for (final Transaction transaction : _submittedTransactions) {
                                    final Sha256Hash transactionHash = transaction.getHash();
                                    if (Util.areEqual(itemHash, transactionHash)) {
                                        bitcoinNode.transmitTransaction(transaction);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        bitcoinNode.setBlockInventoryMessageHandler(new BitcoinNode.BlockInventoryAnnouncementHandler() {
            @Override
            public void onNewInventory(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
                if (_isShuttingDown.get()) { return; }

                final BitcoinNode.BlockInventoryAnnouncementHandler announcementHandler = this;

                final Sha256Hash blockHash = blockHashes.get(0);
                bitcoinNode.requestBlockHeadersAfter(blockHash, new BitcoinNode.DownloadBlockHeadersCallback() {
                    @Override
                    public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                        announcementHandler.onNewHeaders(bitcoinNode, blockHeaders);
                    }
                });
            }

            @Override
            public void onNewHeaders(final BitcoinNode bitcoinNode, final List<BlockHeader> blockHeaders) {
                if (_isShuttingDown.get()) { return; }

                final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);

                for (final BlockHeader blockHeader : blockHeaders) {
                    if (! blockHeader.isValid()) {
                        bitcoinNode.disconnect();
                        return;
                    }

                    final Long headBlockHeight = _blockchain.getHeadBlockHeaderHeight();
                    final Sha256Hash headBlockHash = _blockchain.getHeadBlockHeaderHash();

                    final Sha256Hash blockHash = blockHeader.getHash();
                    final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
                    if (! Util.areEqual(headBlockHash, previousBlockHash)) { return; }

                    final BlockHeaderValidator.BlockHeaderValidationResult validationResult = blockHeaderValidator.validateBlockHeader(blockHeader, headBlockHeight + 1L);
                    if (! validationResult.isValid) {
                        Logger.debug(validationResult.errorMessage + " " + blockHash);
                        bitcoinNode.disconnect();
                        return;
                    }

                    _blockchain.addBlockHeader(blockHeader);

                    _syncBlocks();
                }
            }
        });

        bitcoinNode.enableNewBlockViaHeaders();

        Logger.info("Connecting to: " + host + ":" + port);
        bitcoinNode.connect();

        synchronized (_bitcoinNodes) {
            _bitcoinNodes.add(bitcoinNode);
        }
    }

    protected synchronized void _addNewNodes(final int numberOfNodesToAttemptConnectionsTo) {
        if (_isShuttingDown.get()) { return; }
        if (_skipNetworking) { return; }

        final Integer networkPort = BitcoinConstants.getNetworkDefaults(_bitcoinProperties.getNetworkType()).defaultNetworkPort;
        while (_availablePeers.isEmpty()) { // Connect to DNS seeded nodes...
            final MutableHashSet<String> uniqueConnectionStrings = new MutableHashSet<>();
            final List<String> dnsSeeds = _bitcoinProperties.getDnsSeeds();
            for (final String seedHost : dnsSeeds) {
                Logger.info("seedHost=" + seedHost);
                final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                if (seedIps == null) { continue; }

                for (final Ip ip : seedIps) {
                    final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, networkPort);

                    synchronized (_previouslyConnectedIps) {
                        if (_previouslyConnectedIps.contains(ip)) {
                            continue;
                        }
                    }

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

        _blockStore = new PendingBlockStoreCore(dataDirectory);
        try {
            Logger.info("Loading BlockStore");
            _blockStore.open();
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }

        final File keyValueFile = new File(dataDirectory, "key-values.dat");
        _keyValueStore = new DiskKeyValueStore(new InputOutputFileCore(keyValueFile));
        try {
            _keyValueStore.open();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        final File utxoDbDirectory = new File(dataDirectory, "utxo");
        _unspentTransactionOutputDatabaseManager = new UnspentTransactionOutputFileDbManager(utxoDbDirectory, true, false);
        try {
            Logger.info("Loading FileDB");
            _unspentTransactionOutputDatabaseManager.open();
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        final NetworkType networkType = bitcoinProperties.getNetworkType();
        final CheckpointConfiguration checkpointConfiguration;
        {
            switch (networkType) {
                case MAIN_NET: { checkpointConfiguration = new CheckpointConfiguration(); } break;
                case CHIP_NET: { checkpointConfiguration = new ChipNetCheckpointConfiguration(); } break;
                case TEST_NET: { checkpointConfiguration = new TestNetCheckpointConfiguration(); } break;
                default: { checkpointConfiguration = new DisabledCheckpointConfiguration(); } break;
            }
        }

        _blockchain = new Blockchain(_blockStore, _unspentTransactionOutputDatabaseManager, checkpointConfiguration);
        try {
            Logger.info("Loading Blockchain");
            final Sha256Hash headBlockHash = Sha256Hash.fromHexString(_keyValueStore.getString(KeyValues.HEAD_BLOCK_HASH));
            _blockchain.load(_blockchainFile, headBlockHash);
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
            _transactionIndexer = new TransactionIndexer(transactionIndexDbDirectory, _blockchain, _keyValueStore);
        }
        catch (final Exception exception) {
            throw new RuntimeException(exception);
        }

        _networkTime = new MutableNetworkTime();

        _transactionMempool = new TransactionMempool(_blockchain, _upgradeSchedule, _networkTime, _unspentTransactionOutputDatabaseManager);
        _submittedTransactions = new CircleBuffer<>(1024);

        _indexBlockTask = new WorkerManager.Task() {
            @Override
            public void run() {
                if (! _bitcoinProperties.isIndexingModeEnabled()) { return; }

                final Function<Boolean> isSynced = () -> {
                    final Long blockHeight = _blockchain.getHeadBlockHeight();
                    final Long headerHeight = _blockchain.getHeadBlockHeaderHeight();
                    return Util.areEqual(blockHeight, headerHeight);
                };

                if (! isSynced.run()) { return; }

                try (final WorkerManager workerManager = new WorkerManager(2, 2)) {
                    workerManager.setName("Indexer BlockLoader");
                    workerManager.start();

                    long lastIndexedBlockHeight = Util.parseLong(_keyValueStore.getString(KeyValues.INDEXED_BLOCK_HEIGHT), 0L);
                    long blockHeight = lastIndexedBlockHeight + 1L;

                    while (true) {
                        if (_isShuttingDown.get()) { return; }

                        // Only index when syncing is complete.
                        if (! isSynced.run()) { return; }

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

                        synchronized (_indexProcessMs) {
                            _indexProcessMs.push(indexTimer.getMillisecondsElapsed());
                        }

                        blockHeight += 1L;
                    }
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
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

        _rpcWorkerManager = new WorkerManager(1, 1024);
        _rpcWorkerManager.setName("RPC Worker");
        _rpcWorkerManager.start();

        _synchronizationStatusHandler = new BlockchainSynchronizationStatusHandler(_blockchain);
        final BlockchainDataHandler blockchainDataHandler = new BlockchainDataHandler(_blockchain, _blockStore, _upgradeSchedule, _transactionIndexer, _transactionMempool, _unspentTransactionOutputDatabaseManager) {
            @Override
            public void submitTransaction(final Transaction transaction) {
                if (transaction == null) { return; }

                synchronized (_submittedTransactions) {
                    _submittedTransactions.push(transaction);
                }

                synchronized (_bitcoinNodes) {
                    final Sha256Hash transactionHash = transaction.getHash();
                    final MutableArrayList<Sha256Hash> transactionHashes = new MutableArrayList<>();
                    transactionHashes.add(transactionHash);

                    for (final BitcoinNode bitcoinNode : _bitcoinNodes) {
                        bitcoinNode.transmitTransactionHashes(transactionHashes);
                    }
                }
            }
        };
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
                final MutableList<BitcoinNode> bitcoinNodes = new MutableArrayList<>();
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
        _rpcHandler.setLogLevelSetter(new NodeRpcHandler.LogLevelSetter() {
            @Override
            public void setLogLevel(final String packageName, final String logLevelString) {
                final LogLevel logLevel = LogLevel.fromString(logLevelString);
                if (logLevel == null) {
                    Logger.debug("Invalid Log Level: " + logLevelString);
                    return;
                }

                if (logLevel == LogLevel.OFF) {
                    Logger.setLog(new Log() {
                        @Override
                        public void write(final Class<?> callingClass, final LogLevel logLevel, final String nullableMessage, final Throwable nullableException) {
                            // Nothing.
                        }
                    });
                }
                else if (Logger.getLogLevel() == LogLevel.OFF) {
                    try {
                        final String logDirectory = bitcoinProperties.getLogDirectory();
                        final Log log = AnnotatedFileLog.newInstance(logDirectory, "node");
                        Logger.setLog(log);
                    }
                    catch (final Exception exception) {
                        // Cannot log.
                    }
                }

                Logger.setLogLevel(packageName, logLevel);
            }
        });
        _rpcHandler.setStatisticsHandler(new NodeRpcHandler.StatisticsHandler() {
            @Override
            public Float getAverageBlocksIndexedPerSecond() {
                if (! _synchronizationStatusHandler.isBlockchainSynchronized()) { return null; }
                if (! _bitcoinProperties.isIndexingModeEnabled()) { return null; }

                synchronized (_indexProcessMs) {
                    double total = 0D;
                    final int count = _indexProcessMs.getCount();
                    if (count == 0) { return 0F; }

                    for (int i = 0; i < count; ++i) {
                        total += _indexProcessMs.get(i);
                    }
                    return (float) ((count * 1000D) / total);
                }
            }

            @Override
            public Float getAverageBlocksPerSecond() {
                if (_synchronizationStatusHandler.isBlockchainSynchronized()) { return null; }

                synchronized (_blockProcessMs) {
                    double total = 0D;
                    final int count = _blockProcessMs.getCount();
                    if (count == 0) { return 0F; }

                    for (int i = 0; i < count; ++i) {
                        total += _blockProcessMs.get(i);
                    }
                    return (float) ((count * 1000D) / total);
                }
            }

            @Override
            public Float getAverageTransactionsPerSecond() {
                if (! _synchronizationStatusHandler.isBlockchainSynchronized()) { return null; }

                final long now = _systemTime.getCurrentTimeInMilliSeconds();
                final long timeAtLastBlock = _timeAtLastBlock.get();
                final long durationMs = (now - timeAtLastBlock);
                if (durationMs < 0.10) { return 0F; }
                return (_transactionMempool.getCount() / ((float) durationMs));
            }

            @Override
            public List<UnfulfilledSha256HashRequest> getActiveBlockDownloads() {
                return new MutableArrayList<>(0); // return _blockDownloader.getPendingDownloads(); // TODO: Can deadlock.
            }

            @Override
            public List<UnfulfilledPublicKeyRequest> getActiveUtxoCommitmentDownloads() { return new MutableArrayList<>(0); }

            @Override
            public List<UnfulfilledSha256HashRequest> getActiveTransactionDownloads() {
                return new MutableArrayList<>(0); // TODO
            }
        });

        _downloadBlockHeadersCallback = new BitcoinNode.DownloadBlockHeadersCallback() {
            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(_upgradeSchedule, _blockchain, _networkTime, _difficultyCalculator);

            @Override
            public synchronized void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final List<BlockHeader> response) {
                if (_isShuttingDown.get()) { return; }
                if (response.isEmpty()) { return; }

                { // Validate headers are sequential.
                    Sha256Hash previousBlockHash;
                    {
                        final BlockHeader blockHeader = response.get(0);
                        previousBlockHash = blockHeader.getPreviousBlockHash();
                        final Long previousBlockHeight = _blockchain.getBlockHeight(previousBlockHash);
                        if (previousBlockHeight == null) {
                            final Sha256Hash blockHash = blockHeader.getHash();
                            Logger.info("Unknown block header received (" + blockHash + ") from " + bitcoinNode);
                            return;
                        }
                    }
                    for (final BlockHeader blockHeader : response) {
                        if (! Util.areEqual(previousBlockHash, blockHeader.getPreviousBlockHash())) {
                            Logger.info("Non-sequential blockHeaders received from " + bitcoinNode + "; disconnecting.");
                            bitcoinNode.disconnect();
                            return;
                        }
                        previousBlockHash = blockHeader.getHash();
                    }
                }

                int count = 0;
                boolean hadInvalid = false;
                for (final BlockHeader blockHeader : response) {
                    final Sha256Hash blockHash = blockHeader.getHash();
                    if (_blockchain.getBlockHeight(blockHash) != null) { continue; } // Already-synced header.

                    final Long headBlockHeaderHeight = _blockchain.getHeadBlockHeaderHeight();
                    final Sha256Hash headBlockHeaderHash = _blockchain.getHeadBlockHeaderHash();
                    final Sha256Hash previousBlockHash = blockHeader.getPreviousBlockHash();
                    if (! Util.areEqual(headBlockHeaderHash, previousBlockHash)) { // Possible alternate chain.
                        // TODO: Reorg triggering logic requires that all reorg headers are sent in a single batch; instead should track alternate headers so they can be received incrementally.
                        final Long sharedParentBlockHeight = _blockchain.getBlockHeight(previousBlockHash);
                        if (sharedParentBlockHeight == null) {
                            Logger.info("Irrelevant header (" + blockHash + ") received from " + bitcoinNode + "; disconnecting.");
                            bitcoinNode.disconnect();
                            return;
                        }

                        final BlockHeaderValidator.BlockHeaderValidationResult validationResult = this.blockHeaderValidator.validateBlockHeader(blockHeader, sharedParentBlockHeight + 1L);
                        if (! validationResult.isValid) {
                            Logger.info("Invalid header (" + blockHash + ") received from " + bitcoinNode + "; disconnecting.");
                            bitcoinNode.disconnect();
                            return;
                        }

                        final ChainWork sharedParentChainWork = _blockchain.getChainWork(sharedParentBlockHeight);
                        final ChainWork currentChainWork = _blockchain.getChainWork(headBlockHeaderHeight);
                        final Difficulty difficulty = blockHeader.getDifficulty();
                        final BlockWork blockWork = difficulty.calculateWork();
                        MutableChainWork altWork = new MutableChainWork(sharedParentChainWork);
                        altWork = altWork.add(blockWork);

                        int nextHeaderIndex = count + 1;
                        while (nextHeaderIndex < response.getCount()) {
                            final BlockHeader nextBlockHeader = response.get(nextHeaderIndex);
                            final Difficulty nextDifficulty = nextBlockHeader.getDifficulty();
                            final BlockWork nextBlockWork = nextDifficulty.calculateWork();
                            altWork = altWork.add(nextBlockWork);
                            nextHeaderIndex += 1;
                        }

                        boolean shouldUndo = false;
                        final int workCompareValue = currentChainWork.compareTo(altWork);
                        if (workCompareValue < 0) {
                            shouldUndo = true;
                        }
                        else if (workCompareValue == 0) {
                            final BlockHeader headBlockHeader = _blockchain.getBlockHeader(headBlockHeaderHeight);
                            final long age = _systemTime.getCurrentTimeInSeconds() - headBlockHeader.getTimestamp();
                            if (age > 3600L) {
                                shouldUndo = true;
                            }
                        }

                        if (shouldUndo) {
                            _undoToHeight(sharedParentBlockHeight);
                        }
                        else {
                            Logger.info("Disregarding alternate chain with insufficient work: " + blockHash + ", altWork=" + altWork + ", currentWork=" + currentChainWork);
                            break;
                        }
                    }

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

                        if (_blockchain.getHeadBlockHeight() < _blockchain.getHeadBlockHeaderHeight()) {
                            _syncBlocks();
                        }
                    }
                    else {
                        final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(_blockchain);
                        final List<Sha256Hash> blockHashes = blockFinderHashesBuilder.createBlockHeaderFinderBlockHashes();
                        bitcoinNode.requestBlockHeadersAfter(blockHashes, this, RequestPriority.NORMAL);
                    }
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                _syncBlocks();
            }
        };

        _addNewNodes(8);

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
            long count = 0L;
            try {
                Thread.sleep(10000L);
                if (count % 3 == 0) {
                    // System.gc();
                }
                count += 1L;
                if (count < 0L) { count = 0L; }
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

            _syncWorker.close();

            _unspentTransactionOutputDatabaseManager.close();

            _blockchainIndexerWorker.close();

            _transactionIndexer.close();

            final Sha256Hash headBlockHash = _blockchain.getHeadBlockHash();
            _blockchain.save(_blockchainFile);

            _undoBlockWorker.close();
            _rpcWorkerManager.close();

            if (headBlockHash != null) {
                _keyValueStore.putString(KeyValues.HEAD_BLOCK_HASH, headBlockHash.toString());
            }
            _keyValueStore.close(); // NOTE: Must be closed after TransactionIndexer.
            _blockStore.close();

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

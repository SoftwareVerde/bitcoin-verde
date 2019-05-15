package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.thin.AssembleThinBlockResult;
import com.softwareverde.bitcoin.block.thin.ThinBlockAssembler;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolFactory;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.manager.NodeManager;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.atomic.AtomicBoolean;

public class BitcoinNodeManager extends NodeManager<BitcoinNode> {
    public static final Integer MINIMUM_THIN_BLOCK_TRANSACTION_COUNT = 64;

    public static class BanCriteria {
        public static final Integer FAILED_CONNECTION_ATTEMPT_COUNT = 3;
    }

    public interface FailableCallback {
        default void onFailure() { }
    }
    public interface BlockInventoryMessageCallback extends BitcoinNode.BlockInventoryMessageCallback, FailableCallback { }
    public interface DownloadBlockCallback extends BitcoinNode.DownloadBlockCallback {
        default void onFailure(Sha256Hash blockHash) { }
    }
    public interface DownloadMerkleBlockCallback extends BitcoinNode.DownloadMerkleBlockCallback {
        default void onFailure(Sha256Hash blockHash) { }
    }
    public interface DownloadBlockHeadersCallback extends BitcoinNode.DownloadBlockHeadersCallback, FailableCallback { }
    public interface DownloadTransactionCallback extends BitcoinNode.DownloadTransactionCallback {
        default void onFailure(List<Sha256Hash> transactionHashes) { }
    }

    public static class Properties {
        public Integer maxNodeCount;
        public DatabaseConnectionFactory databaseConnectionFactory;
        public DatabaseManagerCache databaseManagerCache;
        public MutableNetworkTime networkTime;
        public NodeInitializer nodeInitializer;
        public BanFilter banFilter;
        public MemoryPoolEnquirer memoryPoolEnquirer;
        public SynchronizationStatus synchronizationStatusHandler;
        public ThreadPool threadPool;
        public ThreadPoolFactory threadPoolFactory;
        public LocalNodeFeatures localNodeFeatures;
    }

    protected final DatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;
    protected final SynchronizationStatus _synchronizationStatusHandler;
    protected final ThreadPoolFactory _threadPoolFactory;
    protected final LocalNodeFeatures _localNodeFeatures;
    protected final AtomicBoolean _hasHadActiveConnectionSinceLastDisconnect = new AtomicBoolean(false);

    protected Boolean _transactionRelayIsEnabled = true;
    protected MutableBloomFilter _bloomFilter = null;

    protected final Object _pollForReconnectionThreadMutex = new Object();
    protected final Runnable _pollForReconnection = new Runnable() {
        @Override
        public void run() {
            final long maxWait = (5L * 60L * 1000L); // 5 Minutes...
            long nextWait = 500L;
            while (! Thread.interrupted()) {
                if (_isShuttingDown) { return; }

                try { Thread.sleep(nextWait); }
                catch (final Exception exception) { break; }

                try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);

                    final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableList<NodeFeatures.Feature>();
                    requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                    requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                    final List<BitcoinNodeIpAddress> bitcoinNodeIpAddresses = nodeDatabaseManager.findNodes(requiredFeatures, _maxNodeCount);

                    for (final BitcoinNodeIpAddress bitcoinNodeIpAddress : bitcoinNodeIpAddresses) {
                        final Ip ip = bitcoinNodeIpAddress.getIp();
                        if (ip == null) { continue; }

                        final String host = ip.toString();
                        final Integer port = bitcoinNodeIpAddress.getPort();
                        final BitcoinNode node = new BitcoinNode(host, port, _threadPoolFactory.newThreadPool(), _localNodeFeatures);
                        BitcoinNodeManager.this.addNode(node); // NOTE: _addNotHandshakedNode(BitcoinNode) is not the same as addNode(BitcoinNode)...

                        Logger.log("All nodes disconnected.  Falling back on previously-seen node: " + host + ":" + ip);
                    }
                }
                catch (final DatabaseException databaseException) {
                    Logger.log(databaseException);
                }

                nextWait = Math.min((2L * nextWait), maxWait);
            }
            _pollForReconnectionThread = null;
        }
    };
    protected Thread _pollForReconnectionThread;

    protected Runnable _onNodeListChanged;

    // BitcoinNodeManager::transmitBlockHash is often called in rapid succession with the same BlockHash, therefore a simple cache is used...
    protected BlockHeaderWithTransactionCount _cachedTransmittedBlockHeader = null;

    protected void _pollForReconnection() {
        if (_isShuttingDown) { return; }

        synchronized (_pollForReconnectionThreadMutex) {
            if (_pollForReconnectionThread != null) { return; }

            _pollForReconnectionThread = new Thread(_pollForReconnection);
            _pollForReconnectionThread.start();
        }
    }

    @Override
    protected void _initNode(final BitcoinNode node) {
        node.setTransactionRelayIsEnabled(_transactionRelayIsEnabled);

        super._initNode(node);
        _nodeInitializer.initializeNode(node);
    }

    @Override
    protected void _onAllNodesDisconnected() {
        if (! _hasHadActiveConnectionSinceLastDisconnect.getAndSet(false)) { return; } // Prevent infinitely looping by aborting if no new connections were successful since the last attempt...
        _pollForReconnection();
    }

    @Override
    protected void _onNodeConnected(final BitcoinNode bitcoinNode) {
        _hasHadActiveConnectionSinceLastDisconnect.set(true); // Allow for reconnection attempts after all connections die...

        { // Abort the reconnection Thread, if it is running...
            final Thread pollForReconnectionThread = _pollForReconnectionThread;
            if (pollForReconnectionThread != null) {
                pollForReconnectionThread.interrupt();
            }
        }

        final BloomFilter bloomFilter = _bloomFilter;
        if (bloomFilter != null) {
            bitcoinNode.setBloomFilter(bloomFilter);
        }

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseManagerCache);
            final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();

            bitcoinNode.transmitBlockFinder(blockFinderHashes);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    @Override
    protected void _onNodeDisconnected(final BitcoinNode node) {
        super._onNodeDisconnected(node);

        final Boolean handshakeIsComplete = node.handshakeIsComplete();
        if (! handshakeIsComplete) {
            final Ip ip = node.getIp();

            if (_banFilter.shouldBanIp(ip)) {
                _banFilter.banIp(ip);
            }
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    @Override
    protected void _addHandshakedNode(final BitcoinNode node) {
        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        if (_isShuttingDown) {
            node.disconnect();
            return;
        }

        final MutableList<BitcoinNode> allNodes = new MutableList<BitcoinNode>(_pendingNodes.values());
        allNodes.addAll(_nodes.values());

        for (final BitcoinNode bitcoinNode : allNodes) {
            final Ip existingNodeIp = bitcoinNode.getIp();
            final Integer existingNodePort = bitcoinNode.getPort();

            if (Util.areEqual(ip, existingNodeIp) && Util.areEqual(port, existingNodePort)) {
                return; // Duplicate Node...
            }
        }

        final Boolean blockchainIsEnabled = node.hasFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
        final Boolean blockchainIsSynchronized = _synchronizationStatusHandler.isBlockchainSynchronized();
        if (blockchainIsEnabled == null) {
            Logger.log("NOTICE: Unable to determine feature for node: " + node.getConnectionString());
        }

        if ( (! Util.coalesce(blockchainIsEnabled, false)) && (! blockchainIsSynchronized) ) {
            node.disconnect();
            return; // Reject SPV Nodes during the initial-sync...
        }

        super._addHandshakedNode(node);
    }

    @Override
    protected void _addNotHandshakedNode(final BitcoinNode node) {
        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Boolean isBanned = nodeDatabaseManager.isBanned(ip);
            if (isBanned) {
                _nodes.remove(node.getId());
                _pendingNodes.remove(node.getId());
                node.disconnect();
                return;
            }

            if (_isShuttingDown) {
                _nodes.remove(node.getId());
                _pendingNodes.remove(node.getId());
                node.disconnect();
                return;
            }

            final MutableList<BitcoinNode> allNodes = new MutableList<BitcoinNode>(_pendingNodes.values());
            allNodes.addAll(_nodes.values());

            for (final BitcoinNode bitcoinNode : allNodes) {
                final Ip existingNodeIp = bitcoinNode.getIp();
                final Integer existingNodePort = bitcoinNode.getPort();

                if (Util.areEqual(ip, existingNodeIp) && Util.areEqual(port, existingNodePort)) {
                    return; // Duplicate Node...
                }
            }

            super._addNotHandshakedNode(node);

            nodeDatabaseManager.storeNode(node);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    @Override
    protected void _onNodeHandshakeComplete(final BitcoinNode node) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.updateLastHandshake(node);
            nodeDatabaseManager.updateNodeFeatures(node);
            nodeDatabaseManager.updateUserAgent(node);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    public BitcoinNodeManager(final Properties properties) {
        this(properties.maxNodeCount, properties.databaseConnectionFactory, properties.databaseManagerCache,
            properties.networkTime, properties.nodeInitializer, properties.banFilter, properties.memoryPoolEnquirer,
            properties.synchronizationStatusHandler, properties.threadPool, properties.threadPoolFactory,
            properties.localNodeFeatures
        );
    }

    public BitcoinNodeManager(final Integer maxNodeCount, final DatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final MutableNetworkTime networkTime, final NodeInitializer nodeInitializer, final BanFilter banFilter, final MemoryPoolEnquirer memoryPoolEnquirer, final SynchronizationStatus synchronizationStatusHandler, final ThreadPool threadPool, final ThreadPoolFactory threadPoolFactory, final LocalNodeFeatures localNodeFeatures) {
        super(maxNodeCount, new BitcoinNodeFactory(threadPoolFactory, localNodeFeatures), networkTime, threadPool);
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _nodeInitializer = nodeInitializer;
        _banFilter = banFilter;
        _memoryPoolEnquirer = memoryPoolEnquirer;
        _synchronizationStatusHandler = synchronizationStatusHandler;
        _threadPoolFactory = threadPoolFactory;
        _localNodeFeatures = localNodeFeatures;
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback callback) {
        _selectNodeForRequest(new NodeApiRequest<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode) {
                final NodeApiRequest<BitcoinNode> apiRequest = this;

                bitcoinNode.requestBlockHeaders(blockHashes, new BitcoinNode.DownloadBlockHeadersCallback() {
                    @Override
                    public void onResult(final List<BlockHeader> result) {
                        _onResponseReceived(bitcoinNode, apiRequest);
                        if (apiRequest.didTimeout) { return; }

                        if (callback != null) {
                            callback.onResult(result);
                        }
                    }
                });
            }

            @Override
            public void onFailure() {
                final Sha256Hash firstBlockHash = (blockHashes.isEmpty() ? null : blockHashes.get(0));
                Logger.log("Request failed: BitcoinNodeManager.requestBlockHeader("+ firstBlockHash +")");

                if (callback != null) {
                    callback.onFailure();
                }
            }
        });
    }

    public void broadcastBlockFinder(final List<Sha256Hash> blockHashes) {
        for (final BitcoinNode bitcoinNode : _nodes.values()) {
            bitcoinNode.transmitBlockFinder(blockHashes);
        }
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash) {
        _sendMessage(new NodeApiMessage<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode) {
                bitcoinNode.requestBlockHashesAfter(blockHash);
            }
        });
    }

    /**
     * Finds incomplete PendingBlocks that are not provided by any of the connected Nodes and attempts to locate them based on download-priority.
     */
    public void findNodeInventory() {
        final List<NodeId> connectedNodes = new MutableList<NodeId>(_nodes.keySet());
        if (connectedNodes.isEmpty()) { return; }

        final MutableList<QueryBlocksMessage> queryBlocksMessages = new MutableList<QueryBlocksMessage>();

        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            final List<Tuple<Sha256Hash, Sha256Hash>> inventoryPlan = pendingBlockDatabaseManager.selectPriorityPendingBlocksWithUnknownNodeInventory(connectedNodes);

            int messagesWithoutStopBeforeHashes = 0;
            for (final Tuple<Sha256Hash, Sha256Hash> inventoryHash : inventoryPlan) {
                final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
                queryBlocksMessage.addBlockHash(inventoryHash.first);
                queryBlocksMessage.setStopBeforeBlockHash(inventoryHash.second);

                if (inventoryHash.second == null) {
                    if (messagesWithoutStopBeforeHashes > 0) { break; } // NOTE: Only broadcast one QueryBlocks Message without a stopBeforeHash to support the case when BlockHeaders is not up to date...
                    messagesWithoutStopBeforeHashes += 1;
                }

                queryBlocksMessages.add(queryBlocksMessage);
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        for (final QueryBlocksMessage queryBlocksMessage : queryBlocksMessages) {
            Logger.log("Broadcasting QueryBlocksMessage: " + queryBlocksMessage.getBlockHashes().get(0) + " -> " + queryBlocksMessage.getStopBeforeBlockHash());
            for (final BitcoinNode bitcoinNode : _nodes.values()) {
                bitcoinNode.queueMessage(queryBlocksMessage);
            }
        }
    }

    protected NodeApiRequest<BitcoinNode> _createRequestBlockRequest(final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        return new NodeApiRequest<BitcoinNode>() {
            protected BitcoinNode _bitcoinNode;

            @Override
            public void run(final BitcoinNode bitcoinNode) {
                _bitcoinNode = bitcoinNode;

                final NodeApiRequest<BitcoinNode> apiRequest = this;

                bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                    @Override
                    public void onResult(final Block block) {
                        _onResponseReceived(bitcoinNode, apiRequest);
                        if (apiRequest.didTimeout) { return; }

                        if (callback != null) {
                            Logger.log("Received Block: "+ block.getHash() +" from Node: " + bitcoinNode.getConnectionString());
                            callback.onResult(block);
                        }
                    }

                    @Override
                    public void onFailure(final Sha256Hash blockHash) {
                        if (apiRequest.didTimeout) { return; }

                        _pendingRequestsManager.removePendingRequest(apiRequest);

                        apiRequest.onFailure();
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestBlock("+ blockHash +") " + (_bitcoinNode != null ? _bitcoinNode.getConnectionString() : "null"));

                if (callback != null) {
                    callback.onFailure(blockHash);
                }
            }
        };
    }

    protected void _requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        _selectNodeForRequest(_createRequestBlockRequest(blockHash, callback));
    }

    protected void _requestBlock(final BitcoinNode selectedNode, final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        _selectNodeForRequest(selectedNode, _createRequestBlockRequest(blockHash, callback));
    }

    protected void _requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback callback) {
        final NodeApiRequest<BitcoinNode> downloadMerkleBlockRequest = new NodeApiRequest<BitcoinNode>() {
            protected BitcoinNode _bitcoinNode;

            @Override
            public void run(final BitcoinNode bitcoinNode) {
                _bitcoinNode = bitcoinNode;

                final NodeApiRequest<BitcoinNode> apiRequest = this;

                bitcoinNode.requestMerkleBlock(blockHash, new BitcoinNode.DownloadMerkleBlockCallback() {
                    @Override
                    public void onResult(final BitcoinNode.MerkleBlockParameters merkleBlockParameters) {
                        _onResponseReceived(bitcoinNode, apiRequest);
                        if (apiRequest.didTimeout) { return; }

                        final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
                        if (callback != null) {
                            Logger.log("Received Merkle Block: "+ merkleBlock.getHash() +" from Node: " + bitcoinNode.getConnectionString());
                            callback.onResult(merkleBlockParameters);
                        }
                    }

                    @Override
                    public void onFailure(final Sha256Hash blockHash) {
                        if (apiRequest.didTimeout) { return; }

                        _pendingRequestsManager.removePendingRequest(apiRequest);

                        apiRequest.onFailure();
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestMerkleBlock("+ blockHash +") " + (_bitcoinNode != null ? _bitcoinNode.getConnectionString() : "null"));

                if (callback != null) {
                    callback.onFailure(blockHash);
                }
            }
        };

        _selectNodeForRequest(downloadMerkleBlockRequest);
    }

    protected NodeApiRequest<BitcoinNode> _createRequestTransactionsRequest(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback callback) {
        return new NodeApiRequest<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode) {
                final NodeApiRequest<BitcoinNode> apiRequest = this;

                bitcoinNode.requestTransactions(transactionHashes, new BitcoinNode.DownloadTransactionCallback() {
                    @Override
                    public void onResult(final Transaction result) {
                        _onResponseReceived(bitcoinNode, apiRequest);
                        if (apiRequest.didTimeout) { return; }

                        if (callback != null) {
                            callback.onResult(result);
                        }
                    }

                    @Override
                    public void onFailure(final List<Sha256Hash> transactionHashes) {
                        if (apiRequest.didTimeout) { return; }

                        _pendingRequestsManager.removePendingRequest(apiRequest);

                        apiRequest.onFailure();
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestTransactions("+ transactionHashes.get(0) +" + "+ (transactionHashes.getSize() - 1) +")");

                if (callback != null) {
                    callback.onFailure(transactionHashes);
                }
            }
        };
    }

    public void requestThinBlock(final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        final NodeApiRequest<BitcoinNode> thinBlockApiRequest = new NodeApiRequest<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode) {
                final NodeApiRequest<BitcoinNode> apiRequest = this;

                final BloomFilter bloomFilter = _memoryPoolEnquirer.getBloomFilter(blockHash);

                bitcoinNode.requestThinBlock(blockHash, bloomFilter, new BitcoinNode.DownloadThinBlockCallback() { // TODO: Consider using ExtraThinBlocks... Unsure if the potential round-trip on a TransactionHash collision is worth it, though.
                    @Override
                    public void onResult(final BitcoinNode.ThinBlockParameters extraThinBlockParameters) {
                        _onResponseReceived(bitcoinNode, apiRequest);
                        if (apiRequest.didTimeout) { return; }

                        final BlockHeader blockHeader = extraThinBlockParameters.blockHeader;
                        final List<Sha256Hash> transactionHashes = extraThinBlockParameters.transactionHashes;
                        final List<Transaction> transactions = extraThinBlockParameters.transactions;

                        final ThinBlockAssembler thinBlockAssembler = new ThinBlockAssembler(_memoryPoolEnquirer);

                        final AssembleThinBlockResult assembleThinBlockResult = thinBlockAssembler.assembleThinBlock(blockHeader, transactionHashes, transactions);
                        if (! assembleThinBlockResult.wasSuccessful()) {
                            _selectNodeForRequest(bitcoinNode, new NodeApiRequest<BitcoinNode>() {
                                @Override
                                public void run(final BitcoinNode bitcoinNode) {
                                    final NodeApiRequest<BitcoinNode> apiRequest = this;

                                    bitcoinNode.requestThinTransactions(blockHash, assembleThinBlockResult.missingTransactions, new BitcoinNode.DownloadThinTransactionsCallback() {
                                        @Override
                                        public void onResult(final List<Transaction> missingTransactions) {
                                            _onResponseReceived(bitcoinNode, apiRequest);
                                            if (apiRequest.didTimeout) { return; }

                                            final Block block = thinBlockAssembler.reassembleThinBlock(assembleThinBlockResult, missingTransactions);
                                            if (block == null) {
                                                Logger.log("NOTICE: Falling back to traditional block.");
                                                // Fallback on downloading block traditionally...
                                                _requestBlock(blockHash, callback);
                                            }
                                            else {
                                                Logger.log("NOTICE: Thin block assembled. " + System.currentTimeMillis());
                                                if (callback != null) {
                                                    callback.onResult(assembleThinBlockResult.block);
                                                }
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onFailure() {
                                    Logger.log("NOTICE: Falling back to traditional block.");

                                    _pendingRequestsManager.removePendingRequest(apiRequest);

                                    _requestBlock(blockHash, callback);
                                }
                            });
                        }
                        else {
                            Logger.log("NOTICE: Thin block assembled on first trip. " + System.currentTimeMillis());
                            if (callback != null) {
                                callback.onResult(assembleThinBlockResult.block);
                            }
                        }
                    }
                });
            }

            @Override
            public void onFailure() {
                Logger.log("Request failed: BitcoinNodeManager.requestThinBlock("+ blockHash +")");

                if (callback != null) {
                    callback.onFailure(blockHash);
                }
            }
        };

        final Boolean shouldRequestThinBlocks;
        {
            if (! _synchronizationStatusHandler.isBlockchainSynchronized()) {
                shouldRequestThinBlocks = false;
            }
            else if (_memoryPoolEnquirer == null) {
                shouldRequestThinBlocks = false;
            }
            else {
                final Integer memoryPoolTransactionCount = _memoryPoolEnquirer.getMemoryPoolTransactionCount();
                final Boolean memoryPoolIsTooEmpty = (memoryPoolTransactionCount >= MINIMUM_THIN_BLOCK_TRANSACTION_COUNT);
                shouldRequestThinBlocks = (! memoryPoolIsTooEmpty);
            }
        }

        if (shouldRequestThinBlocks) {
            final NodeFilter<BitcoinNode> nodeFilter = new NodeFilter<BitcoinNode>() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    return bitcoinNode.supportsExtraThinBlocks();
                }
            };

            final BitcoinNode selectedNode = _selectBestNode(nodeFilter);
            if (selectedNode != null) {
                Logger.log("NOTICE: Requesting thin block. " + System.currentTimeMillis());
                _selectNodeForRequest(selectedNode, thinBlockApiRequest);
            }
            else {
                _requestBlock(blockHash, callback);
            }
        }
        else {
            _requestBlock(blockHash, callback);
        }
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        _requestBlock(blockHash, callback);
    }

    public void requestBlock(final BitcoinNode selectedNode, final Sha256Hash blockHash, final DownloadBlockCallback callback) {
        _requestBlock(selectedNode, blockHash, callback);
    }

    public void requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback callback) {
        _requestMerkleBlock(blockHash, callback);
    }

    public void transmitBlockHash(final BitcoinNode bitcoinNode, final Block block) {
        if (bitcoinNode.newBlocksViaHeadersIsEnabled()) {
            bitcoinNode.transmitBlockHeader(block, block.getTransactionCount());
        }
        else {
            final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(1);
            blockHashes.add(block.getHash());
            bitcoinNode.transmitBlockHashes(blockHashes);
        }
    }

    public void transmitBlockHash(final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
        if (bitcoinNode.newBlocksViaHeadersIsEnabled()) {
            final BlockHeaderWithTransactionCount cachedBlockHeader = _cachedTransmittedBlockHeader;
            if ( (cachedBlockHeader != null) && (Util.areEqual(blockHash, cachedBlockHeader.getHash())) ) {
                bitcoinNode.transmitBlockHeader(cachedBlockHeader);
            }
            else {
                try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
                    final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    if (blockId == null) { return; } // Block Hash has not been synchronized...

                    final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
                    final Integer transactionCount = blockDatabaseManager.getTransactionCount(blockId);
                    if (transactionCount == null) { return; } // Block Hash is currently only a header...

                    final BlockHeaderWithTransactionCount blockHeaderWithTransactionCount = new ImmutableBlockHeaderWithTransactionCount(blockHeader, transactionCount);
                    _cachedTransmittedBlockHeader = blockHeaderWithTransactionCount;

                    bitcoinNode.transmitBlockHeader(blockHeaderWithTransactionCount);
                }
                catch (final DatabaseException exception) {
                    Logger.log(exception);
                }
            }
        }
        else {
            final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(1);
            blockHashes.add(blockHash);

            bitcoinNode.transmitBlockHashes(blockHashes);
        }
    }

    public void requestBlockHeadersAfter(final Sha256Hash blockHash, final DownloadBlockHeadersCallback callback) {
        final MutableList<Sha256Hash> blockHashes = new MutableList<Sha256Hash>(1);
        blockHashes.add(blockHash);

        _requestBlockHeaders(blockHashes, callback);
    }

    public void requestBlockHeadersAfter(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback callback) {
        _requestBlockHeaders(blockHashes, callback);
    }

    public void requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback callback) {
        if (transactionHashes.isEmpty()) { return; }

        _selectNodeForRequest(_createRequestTransactionsRequest(transactionHashes, callback));
    }

    public void requestTransactions(final BitcoinNode selectedNode, final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback callback) {
        if (transactionHashes.isEmpty()) { return; }

        _selectNodeForRequest(selectedNode, _createRequestTransactionsRequest(transactionHashes, callback));
    }

    public void setBloomFilter(final MutableBloomFilter bloomFilter) {
        _bloomFilter = bloomFilter;

        for (final BitcoinNode bitcoinNode : _nodes.values()) {
            bitcoinNode.setBloomFilter(_bloomFilter);
        }
    }

    public void banNode(final Ip ip) {
        try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);

            // Ban any nodes from that ip...
            nodeDatabaseManager.setIsBanned(ip, true);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
            // Still continue to disconnect from any nodes at that ip, even upon an error...
        }

        // Disconnect all currently-connected nodes at that ip...
        final MutableList<BitcoinNode> droppedNodes = new MutableList<BitcoinNode>();

        for (final BitcoinNode bitcoinNode : _nodes.values()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        // Disconnect all pending nodes at that ip...
        for (final BitcoinNode bitcoinNode : _pendingNodes.values()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        for (final BitcoinNode bitcoinNode : droppedNodes) {
            _removeNode(bitcoinNode);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    public void unbanNode(final Ip ip) {
        _banFilter.unbanNode(ip);
    }

    public void setOnNodeListChanged(final Runnable callback) {
        _onNodeListChanged = callback;
    }

    public void setTransactionRelayIsEnabled(final Boolean transactionRelayIsEnabled) {
        _transactionRelayIsEnabled = transactionRelayIsEnabled;

        for (final BitcoinNode bitcoinNode : _nodes.values()) {
            bitcoinNode.setTransactionRelayIsEnabled(transactionRelayIsEnabled);
        }
    }

    public Boolean isTransactionRelayEnabled() {
        return _transactionRelayIsEnabled;
    }

    @Override
    public void shutdown() {
        super.shutdown();

        final Thread pollForReconnectionThread = _pollForReconnectionThread;
        if (pollForReconnectionThread != null) {
            pollForReconnectionThread.interrupt();

            try { pollForReconnectionThread.join(5000L); } catch (final Exception exception) { }
        }
    }
}

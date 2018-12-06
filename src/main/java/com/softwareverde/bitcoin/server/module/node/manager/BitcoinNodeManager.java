package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.thin.AssembleThinBlockResult;
import com.softwareverde.bitcoin.block.thin.ThinBlockAssembler;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.manager.NodeManager;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.Util;

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
    public interface DownloadBlockHeadersCallback extends BitcoinNode.DownloadBlockHeadersCallback, FailableCallback { }
    public interface DownloadTransactionCallback extends BitcoinNode.DownloadTransactionCallback {
        default void onFailure(List<Sha256Hash> transactionHashes) { }
    }

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;
    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;
    protected final SynchronizationStatus _synchronizationStatusHandler;

    // BitcoinNodeManager::transmitBlockHash is often called in rapid succession with the same BlockHash, therefore a simple cache is used...
    protected BlockHeaderWithTransactionCount _cachedTransmittedBlockHeader = null;

    @Override
    protected void _initNode(final BitcoinNode node) {
        super._initNode(node);
        _nodeInitializer.initializeNode(node);
    }

    @Override
    protected void _onAllNodesDisconnected() {
        Logger.log("All nodes disconnected.");

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
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
                final BitcoinNode node = new BitcoinNode(host, port);
                this.addNode(node); // NOTE: _addNode(BitcoinNode) is not the same as addNode(BitcoinNode)...

                Logger.log("All nodes disconnected.  Falling back on previously-seen node: " + host + ":" + ip);
            }
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    @Override
    public void _onNodeConnected(final BitcoinNode node) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseConnection, _databaseManagerCache);
            final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();

            node.transmitBlockFinder(blockFinderHashes);
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }
    }

    @Override
    protected void _onNodeDisconnected(final BitcoinNode node) {
        super._onNodeDisconnected(node);

        final Boolean handshakeIsComplete = node.handshakeIsComplete();
        if (! handshakeIsComplete) {
            final String host = node.getHost();

            if (_banFilter.shouldBanHost(host)) {
                _banFilter.banHost(host);
            }
        }
    }

    @Override
    protected void _addNode(final BitcoinNode node) {
        final String host = node.getHost();
        final Integer port = node.getPort();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            final Boolean isBanned = nodeDatabaseManager.isBanned(host);
            if (isBanned) { return; }


            synchronized (_mutex) {
                for (final BitcoinNode bitcoinNode : _nodes.values()) {
                    final String existingNodeHost = bitcoinNode.getHost();
                    final Integer existingNodePort = bitcoinNode.getPort();

                    if (Util.areEqual(host, existingNodeHost) && Util.areEqual(port, existingNodePort)) {
                        return; // Duplicate Node...
                    }
                }

                super._addNode(node);
            }

            nodeDatabaseManager.storeNode(node);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    @Override
    protected void _onNodeHandshakeComplete(final BitcoinNode node) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = new BitcoinNodeDatabaseManager(databaseConnection);
            nodeDatabaseManager.updateLastHandshake(node);
            nodeDatabaseManager.updateNodeFeatures(node);
            nodeDatabaseManager.updateUserAgent(node);
        }
        catch (final DatabaseException databaseException) {
            Logger.log(databaseException);
        }
    }

    public BitcoinNodeManager(final Integer maxNodeCount, final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache, final MutableNetworkTime networkTime, final NodeInitializer nodeInitializer, final BanFilter banFilter, final MemoryPoolEnquirer memoryPoolEnquirer, final SynchronizationStatus synchronizationStatusHandler) {
        super(maxNodeCount, new BitcoinNodeFactory(), networkTime);
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
        _nodeInitializer = nodeInitializer;
        _banFilter = banFilter;
        _memoryPoolEnquirer = memoryPoolEnquirer;
        _synchronizationStatusHandler = synchronizationStatusHandler;
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback callback) {
        _selectNodeForRequest(new NodeApiRequest<BitcoinNode>() {
            @Override
            public void run(final BitcoinNode bitcoinNode) {
                final NodeApiRequest<BitcoinNode> apiRequest = this;

                bitcoinNode.requestBlockHeaders(blockHashes, new BitcoinNode.DownloadBlockHeadersCallback() {
                    @Override
                    public void onResult(final List<BlockHeaderWithTransactionCount> result) {
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
        synchronized (_mutex) {
            for (final BitcoinNode bitcoinNode : _nodes.values()) {
                bitcoinNode.transmitBlockFinder(blockHashes);
            }
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
                            Logger.log("Received Block: "+ block.getHash() +" from Node: " + bitcoinNode.getHost());
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

            final BitcoinNode selectedNode;
            synchronized (_mutex) {
                selectedNode = _selectBestNode(nodeFilter);
            }

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

    public void transmitTransactionHashes(final BitcoinNode bitcoinNode, final List<Sha256Hash> transactionHashes) {
        bitcoinNode.transmitTransactionHashes(transactionHashes);
    }

    public void transmitBlockHashes(final BitcoinNode bitcoinNode, final List<Sha256Hash> blockHashes) {
        bitcoinNode.transmitBlockHashes(blockHashes);
    }

    public void transmitBlockHash(final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
        if (bitcoinNode.newBlocksViaHeadersIsEnabled()) {
            final BlockHeaderWithTransactionCount cachedBlockHeader = _cachedTransmittedBlockHeader;
            if ( (cachedBlockHeader != null) && (Util.areEqual(blockHash, cachedBlockHeader.getHash())) ) {
                bitcoinNode.transmitBlockHeader(cachedBlockHeader);
            }
            else {
                try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
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

    @Override
    public void shutdown() {
        super.shutdown();

        synchronized (_mutex) {
            for (final BitcoinNode node : _nodes.values()) {
                node.disconnect();
            }
            _nodes.clear();
        }
    }
}

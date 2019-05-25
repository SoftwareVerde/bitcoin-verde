package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.callback.Callback;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.clear.ClearTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.set.SetTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.update.UpdateTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.compact.EnableCompactBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.request.RequestPeersMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feefilter.FeeFilterMessage;
import com.softwareverde.bitcoin.server.message.type.node.ping.BitcoinPingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.BitcoinPongMessage;
import com.softwareverde.bitcoin.server.message.type.query.address.QueryAddressBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.merkle.MerkleBlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.block.RequestExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.transaction.RequestExtraThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.transaction.ThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.PingMessage;
import com.softwareverde.network.p2p.message.type.PongMessage;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BitcoinNode extends Node {
    public static Boolean LOGGING_ENABLED = true;

    public interface BlockInventoryMessageCallback {
        void onResult(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes);
    }

    public interface RequestPeersHandler {
        List<BitcoinNodeIpAddress> getConnectedPeers();
    }

    public interface DownloadBlockCallback extends Callback<Block> {
        default void onFailure(Sha256Hash blockHash) { }
    }
    public interface DownloadMerkleBlockCallback extends Callback<MerkleBlockParameters> {
        default void onFailure(Sha256Hash blockHash) { }
    }
    public interface DownloadBlockHeadersCallback extends Callback<List<BlockHeader>> { }
    public interface DownloadTransactionCallback extends Callback<Transaction> {
        default void onFailure(List<Sha256Hash> transactionHashes) { }
    }
    public interface DownloadThinBlockCallback extends Callback<ThinBlockParameters> { }
    public interface DownloadExtraThinBlockCallback extends Callback<ExtraThinBlockParameters> { }
    public interface DownloadThinTransactionsCallback extends Callback<List<Transaction>> { }
    public interface TransactionInventoryMessageCallback extends Callback<List<Sha256Hash>> { }
    public interface SpvBlockInventoryMessageCallback extends Callback<List<Sha256Hash>> { }

    public static SynchronizationStatus DEFAULT_STATUS_CALLBACK = new SynchronizationStatus() {
        @Override
        public State getState() { return State.ONLINE; }

        @Override
        public Boolean isBlockchainSynchronized() { return false; }

        @Override
        public Boolean isReadyForTransactions() { return false; }

        @Override
        public Long getCurrentBlockHeight() { return 0L; }
    };

    public interface QueryBlocksCallback {
        void run(List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash, BitcoinNode bitcoinNode);
    }

    public interface QueryBlockHeadersCallback {
        void run(List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash, BitcoinNode bitcoinNode);
    }

    public interface QueryUnconfirmedTransactionsCallback {
        void run(BitcoinNode bitcoinNode);
    }

    public interface RequestDataCallback {
        void run(List<InventoryItem> dataHashes, BitcoinNode bitcoinNode);
    }

    public interface RequestSpvBlocksCallback {
        void run(List<Address> addresses, BitcoinNode bitcoinNode);
    }

    public interface RequestExtraThinBlockCallback {
        void run(Sha256Hash blockHash, BloomFilter bloomFilter, BitcoinNode bitcoinNode);
    }

    public interface RequestExtraThinTransactionCallback {
        void run(Sha256Hash blockHash, List<ByteArray> transactionShortHashes, BitcoinNode bitcoinNode);
    }

    public static class ThinBlockParameters {
        public final BlockHeader blockHeader;
        public final List<Sha256Hash> transactionHashes;
        public final List<Transaction> transactions;

        public ThinBlockParameters(final BlockHeader blockHeader, final List<Sha256Hash> transactionHashes, final List<Transaction> transactions) {
            this.blockHeader = blockHeader;
            this.transactionHashes = transactionHashes;
            this.transactions = transactions;
        }
    }

    public static class ExtraThinBlockParameters {
        public final BlockHeader blockHeader;
        public final List<ByteArray> transactionHashes;
        public final List<Transaction> transactions;

        public ExtraThinBlockParameters(final BlockHeader blockHeader, final List<ByteArray> transactionHashes, final List<Transaction> transactions) {
            this.blockHeader = blockHeader;
            this.transactionHashes = transactionHashes;
            this.transactions = transactions;
        }
    }

    public static class MerkleBlockParameters {
        protected final MerkleBlock _merkleBlock;
        protected final MutableList<Transaction> _transactions = new MutableList<Transaction>();

        public MerkleBlock getMerkleBlock() {
            return _merkleBlock;
        }

        public List<Transaction> getTransactions() {
            return _transactions;
        }

        public MerkleBlockParameters(final MerkleBlock merkleBlock) {
            _merkleBlock = merkleBlock.asConst();
        }

        protected Boolean hasAllTransactions() {
            return Util.areEqual(_merkleBlock.getTransactionCount(), _transactions.getSize());
        }

        protected void addTransaction(final Transaction transaction) {
            _transactions.add(transaction.asConst());
        }
    }

    protected static <U, T, S extends Callback<U>> void _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value, final ThreadPool threadPool) {
        synchronized (callbackMap) {
            final Set<S> callbackSet = callbackMap.remove(key);
            if ((callbackSet == null) || (callbackSet.isEmpty())) { return; }

            for (final S callback : callbackSet) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(value);
                    }
                });
            }
        }
    }

    protected static <T, S> void _storeInMapSet(final Map<T, Set<S>> destinationMap, final T key, final S value) {
        synchronized (destinationMap) {
            Set<S> destinationSet = destinationMap.get(key);
            if (destinationSet == null) {
                destinationSet = new HashSet<S>();
                destinationMap.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    protected static <T, S> void _storeInMapList(final Map<T, MutableList<S>> destinationList, final T key, final S value) {
        synchronized (destinationList) {
            MutableList<S> destinationSet = destinationList.get(key);
            if (destinationSet == null) {
                destinationSet = new MutableList<S>();
                destinationList.put(key, destinationSet);
            }
            destinationSet.add(value);
        }
    }

    protected SynchronizationStatus _synchronizationStatus = DEFAULT_STATUS_CALLBACK;

    protected QueryBlocksCallback _queryBlocksCallback = null;
    protected QueryBlockHeadersCallback _queryBlockHeadersCallback = null;
    protected RequestDataCallback _requestDataMessageCallback = null;
    protected BlockInventoryMessageCallback _blockInventoryMessageHandler = null;
    protected RequestPeersHandler _requestPeersHandler = null;
    protected QueryUnconfirmedTransactionsCallback _queryUnconfirmedTransactionsCallback = null;
    protected RequestSpvBlocksCallback _requestSpvBlocksCallback = null;

    protected RequestExtraThinBlockCallback _requestExtraThinBlockCallback = null;
    protected RequestExtraThinTransactionCallback _requestExtraThinTransactionCallback = null;

    protected BitcoinSynchronizeVersionMessage _synchronizeVersionMessage = null;

    protected TransactionInventoryMessageCallback _transactionsAnnouncementCallback = null;
    protected SpvBlockInventoryMessageCallback _spvBlockInventoryMessageCallback = null;

    protected final Map<Sha256Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Sha256Hash, Set<DownloadBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadMerkleBlockCallback>> _downloadMerkleBlockRequests = new HashMap<Sha256Hash, Set<DownloadMerkleBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockHeadersCallback>> _downloadBlockHeadersRequests = new HashMap<Sha256Hash, Set<DownloadBlockHeadersCallback>>();
    protected final Map<Sha256Hash, Set<DownloadTransactionCallback>> _downloadTransactionRequests = new HashMap<Sha256Hash, Set<DownloadTransactionCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinBlockCallback>> _downloadThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadExtraThinBlockCallback>> _downloadExtraThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadExtraThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinTransactionsCallback>> _downloadThinTransactionsRequests = new HashMap<Sha256Hash, Set<DownloadThinTransactionsCallback>>();

    protected final LocalNodeFeatures _localNodeFeatures;

    protected Boolean _announceNewBlocksViaHeadersIsEnabled = false;
    protected Integer _compactBlocksVersion = null;

    protected Boolean _transactionRelayIsEnabled = true;

    protected MutableBloomFilter _bloomFilter = null;
    protected Sha256Hash _batchContinueHash = null; // https://en.bitcoin.it/wiki/Satoshi_Client_Block_Exchange#Batch_Continue_Mechanism

    protected MerkleBlockParameters _currentMerkleBlockBeingTransmitted = null; // Represents the currently MerkleBlock being transmitted from the node. Becomes unset after a non-transaction message is received.

    @Override
    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
        if (synchronizeVersionMessage instanceof BitcoinSynchronizeVersionMessage) {
            _synchronizeVersionMessage = (BitcoinSynchronizeVersionMessage) synchronizeVersionMessage;
        }
        else {
            Logger.log("NOTICE: Invalid SynchronizeVersionMessage type provided to BitcoinNode::_onSynchronizeVersion.");
        }

        super._onSynchronizeVersion(synchronizeVersionMessage);
    }

    @Override
    protected BitcoinPingMessage _createPingMessage() {
        return new BitcoinPingMessage();
    }

    @Override
    protected BitcoinPongMessage _createPongMessage(final PingMessage pingMessage) {
        final BitcoinPongMessage pongMessage = new BitcoinPongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
        return pongMessage;
    }

    @Override
    protected void _disconnect() {
        { // Unset all callback and handlers...
            _queryBlocksCallback = null;
            _queryBlockHeadersCallback = null;
            _requestDataMessageCallback = null;
            _requestSpvBlocksCallback = null;
            _blockInventoryMessageHandler = null;
            _requestExtraThinBlockCallback = null;
            _requestExtraThinTransactionCallback = null;
            _transactionsAnnouncementCallback = null;
            _spvBlockInventoryMessageCallback = null;
        }

        synchronized (_downloadBlockRequests) { _downloadBlockRequests.clear(); }
        synchronized (_downloadMerkleBlockRequests) { _downloadMerkleBlockRequests.clear(); }
        synchronized (_downloadBlockHeadersRequests) { _downloadBlockHeadersRequests.clear(); }
        synchronized (_downloadTransactionRequests) { _downloadTransactionRequests.clear(); }
        synchronized (_downloadThinBlockRequests) { _downloadThinBlockRequests.clear(); }
        synchronized (_downloadExtraThinBlockRequests) { _downloadExtraThinBlockRequests.clear(); }
        synchronized (_downloadThinTransactionsRequests) { _downloadThinTransactionsRequests.clear(); }

        super._disconnect();
    }

    @Override
    protected BitcoinSynchronizeVersionMessage _createSynchronizeVersionMessage() {
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = new BitcoinSynchronizeVersionMessage();

        final NodeFeatures nodeFeatures = _localNodeFeatures.getNodeFeatures();
        synchronizeVersionMessage.setNodeFeatures(nodeFeatures);

        synchronizeVersionMessage.setTransactionRelayIsEnabled(_synchronizationStatus.isReadyForTransactions() && _transactionRelayIsEnabled);
        synchronizeVersionMessage.setCurrentBlockHeight(_synchronizationStatus.getCurrentBlockHeight());

        { // Set Remote NodeIpAddress...
            final BitcoinNodeIpAddress remoteNodeIpAddress = new BitcoinNodeIpAddress();
            remoteNodeIpAddress.setIp(_connection.getIp());
            remoteNodeIpAddress.setPort(_connection.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        }

        { // Set Local NodeIpAddress...
            // TODO
        }

        return synchronizeVersionMessage;
    }

    @Override
    protected BitcoinAcknowledgeVersionMessage _createAcknowledgeVersionMessage(final SynchronizeVersionMessage synchronizeVersionMessage) {
        return new BitcoinAcknowledgeVersionMessage();
    }

    @Override
    protected BitcoinNodeIpAddressMessage _createNodeIpAddressMessage() {
        return new BitcoinNodeIpAddressMessage();
    }

    protected void _initConnection() {
        _connection.setMessageReceivedCallback(new NodeConnection.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(final ProtocolMessage protocolMessage) {
                if (! (protocolMessage instanceof BitcoinProtocolMessage)) {
                    Logger.log("NOTICE: Disregarding Non-Bitcoin ProtocolMessage.");
                    return;
                }

                final BitcoinProtocolMessage message = (BitcoinProtocolMessage) protocolMessage;

                if (LOGGING_ENABLED) {
                    Logger.log("Received: " + message.getCommand() + " from " + BitcoinNode.this.getConnectionString());
                }

                _lastMessageReceivedTimestamp = _systemTime.getCurrentTimeInMilliSeconds();

                // If a MerkleBlock was requested, trigger the MerkleBlock completion when a non-Transaction message is received.
                if (message.getCommand() != MessageType.TRANSACTION) {
                    final MerkleBlockParameters merkleBlockParameters = _currentMerkleBlockBeingTransmitted;
                    _currentMerkleBlockBeingTransmitted = null;

                    if (merkleBlockParameters != null) {
                        final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
                        final Sha256Hash blockHash = merkleBlock.getHash();
                        _executeAndClearCallbacks(_downloadMerkleBlockRequests, blockHash, merkleBlockParameters, _threadPool);
                    }
                }

                switch (message.getCommand()) {
                    case PING: {
                        _onPingReceived((BitcoinPingMessage) message);
                    } break;

                    case PONG: {
                        _onPongReceived((PongMessage) message);
                    } break;

                    case SYNCHRONIZE_VERSION: {
                        _onSynchronizeVersion((SynchronizeVersionMessage) message);
                    } break;

                    case ACKNOWLEDGE_VERSION: {
                        _onAcknowledgeVersionMessageReceived((BitcoinAcknowledgeVersionMessage) message);
                    } break;

                    case NODE_ADDRESSES: {
                        _onNodeAddressesReceived((BitcoinNodeIpAddressMessage) message);
                    } break;

                    case ERROR: {
                        _onErrorMessageReceived((ErrorMessage) message);
                    } break;

                    case INVENTORY: {
                        _onInventoryMessageReceived((InventoryMessage) message);
                    } break;

                    case REQUEST_DATA: {
                        _onRequestDataMessageReceived((RequestDataMessage) message);
                    } break;

                    case BLOCK: {
                        _onBlockMessageReceived((BlockMessage) message);
                    } break;

                    case TRANSACTION: {
                        _onTransactionMessageReceived((TransactionMessage) message);
                    } break;

                    case MERKLE_BLOCK: {
                        _onMerkleBlockReceived((MerkleBlockMessage) message);
                    } break;

                    case BLOCK_HEADERS: {
                        _onBlockHeadersMessageReceived((BlockHeadersMessage) message);
                    } break;

                    case QUERY_BLOCKS: {
                        _onQueryBlocksMessageReceived((QueryBlocksMessage) message);
                    } break;

                    case QUERY_UNCONFIRMED_TRANSACTIONS: {
                        _onQueryUnconfirmedTransactionsReceived();
                    } break;

                    case REQUEST_BLOCK_HEADERS: {
                        _onQueryBlockHeadersMessageReceived((RequestBlockHeadersMessage) message);
                    } break;

                    case ENABLE_NEW_BLOCKS_VIA_HEADERS: {
                        _announceNewBlocksViaHeadersIsEnabled = true;
                    } break;

                    case ENABLE_COMPACT_BLOCKS: {
                        final EnableCompactBlocksMessage enableCompactBlocksMessage = (EnableCompactBlocksMessage) message;
                        _compactBlocksVersion = (enableCompactBlocksMessage.isEnabled() ? enableCompactBlocksMessage.getVersion() : null);
                    } break;

                    case REQUEST_EXTRA_THIN_BLOCK: {
                        _onRequestExtraThinBlockMessageReceived((RequestExtraThinBlockMessage) message);
                    } break;

                    case EXTRA_THIN_BLOCK: {
                        _onExtraThinBlockMessageReceived((ExtraThinBlockMessage) message);
                    } break;

                    case THIN_BLOCK: {
                        _onThinBlockMessageReceived((ThinBlockMessage) message);
                    } break;

                    case REQUEST_EXTRA_THIN_TRANSACTIONS: {
                        _onRequestExtraThinTransactionsMessageReceived((RequestExtraThinTransactionsMessage) message);
                    } break;

                    case THIN_TRANSACTIONS: {
                        _onThinTransactionsMessageReceived((ThinTransactionsMessage) message);
                    } break;

                    case NOT_FOUND: {
                        _onNotFoundMessageReceived((NotFoundResponseMessage) message);
                    } break;

                    case FEE_FILTER: {
                        _onFeeFilterMessageReceived((FeeFilterMessage) message);
                    } break;

                    case REQUEST_PEERS: {
                        _onRequestPeersMessageReceived((RequestPeersMessage) message);
                    } break;

                    case SET_TRANSACTION_BLOOM_FILTER: {
                        _onSetTransactionBloomFilterMessageReceived((SetTransactionBloomFilterMessage) message);
                    } break;

                    case UPDATE_TRANSACTION_BLOOM_FILTER: {
                        _onUpdateTransactionBloomFilterMessageReceived((UpdateTransactionBloomFilterMessage) message);
                    } break;

                    case CLEAR_TRANSACTION_BLOOM_FILTER: {
                        _onClearTransactionBloomFilterMessageReceived((ClearTransactionBloomFilterMessage) message);
                    } break;

                    case QUERY_ADDRESS_BLOCKS: {
                        _onQueryAddressBlocks((QueryAddressBlocksMessage) message);
                    }

                    default: {
                        Logger.log("NOTICE: Unhandled Message Command: "+ message.getCommand() +": 0x"+ HexUtil.toHexString(message.getHeaderBytes()));
                    } break;
                }
            }
        });

        _connection.setOnConnectFailureCallback(new Runnable() {
            @Override
            public void run() {
                _disconnect();
            }
        });

        _connection.setOnDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                _disconnect();
            }
        });

        _connection.setOnConnectCallback(new Runnable() {
            @Override
            public void run() {
                _onConnect();
            }
        });
    }

    public BitcoinNode(final String host, final Integer port, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        super(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPool);
        _localNodeFeatures = localNodeFeatures;

        _initConnection();
    }

    public BitcoinNode(final BinarySocket binarySocket, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        super(binarySocket, threadPool);
        _localNodeFeatures = localNodeFeatures;

        _initConnection();
    }

    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        Logger.log("RECEIVED ERROR:"+ rejectCode.getRejectMessageType().getValue() +" "+ HexUtil.toHexString(new byte[] { rejectCode.getCode() }) +" "+ errorMessage.getRejectDescription() +" "+ HexUtil.toHexString(errorMessage.getExtraData()) + " " + this.getUserAgent() + " " + this.getConnectionString());
    }

    protected void _onRequestDataMessageReceived(final RequestDataMessage requestDataMessage) {
        final RequestDataCallback requestDataCallback = _requestDataMessageCallback;

        if (requestDataCallback != null) {
            final List<InventoryItem> dataHashes = new ImmutableList<InventoryItem>(requestDataMessage.getInventoryItems());
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    requestDataCallback.run(dataHashes, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for RequestData message.");
        }
    }

    protected void _onInventoryMessageReceived(final InventoryMessage inventoryMessage) {
        final Map<InventoryItemType, MutableList<Sha256Hash>> dataHashesMap = new HashMap<InventoryItemType, MutableList<Sha256Hash>>();

        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        for (final InventoryItem inventoryItem : dataHashes) {
            final InventoryItemType inventoryItemType = inventoryItem.getItemType();
            _storeInMapList(dataHashesMap, inventoryItemType, inventoryItem.getItemHash());
        }

        for (final InventoryItemType inventoryItemType : dataHashesMap.keySet()) {
            final List<Sha256Hash> objectHashes = dataHashesMap.get(inventoryItemType);

            if (objectHashes.isEmpty()) { continue; }

            switch (inventoryItemType) {
                case BLOCK: {
                    final BlockInventoryMessageCallback blockInventoryMessageHandler = _blockInventoryMessageHandler;
                    if (blockInventoryMessageHandler != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                blockInventoryMessageHandler.onResult(BitcoinNode.this, objectHashes);
                            }
                        });
                    }
                    else {
                        Logger.log("NOTICE: No handler set for BlockInventoryMessageHandler.");
                    }
                } break;

                case TRANSACTION: {
                    final TransactionInventoryMessageCallback transactionsAnnouncementCallback = _transactionsAnnouncementCallback;
                    if (transactionsAnnouncementCallback != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                transactionsAnnouncementCallback.onResult(objectHashes);
                            }
                        });
                    }
                    else {
                        Logger.log("NOTICE: No handler set for TransactionInventoryMessageCallback.");
                    }
                } break;

                case SPV_BLOCK: {
                    final SpvBlockInventoryMessageCallback spvBlockInventoryMessageCallback = _spvBlockInventoryMessageCallback;
                    if (spvBlockInventoryMessageCallback != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                spvBlockInventoryMessageCallback.onResult(objectHashes);
                            }
                        });
                    }
                    else {
                        Logger.log("NOTICE: No handler set for SpvBlockInventoryMessageCallback.");
                    }
                }
            }
        }
    }

    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        final Boolean blockHeaderIsValid = block.isValid();

        final Sha256Hash blockHash = block.getHash();
        _executeAndClearCallbacks(_downloadBlockRequests, blockHash, (blockHeaderIsValid ? block : null), _threadPool);
    }

    protected void _onTransactionMessageReceived(final TransactionMessage transactionMessage) {
        final Transaction transaction = transactionMessage.getTransaction();

        final Sha256Hash transactionHash = transaction.getHash();
        _executeAndClearCallbacks(_downloadTransactionRequests, transactionHash, transaction, _threadPool);

        final MerkleBlockParameters merkleBlockParameters = _currentMerkleBlockBeingTransmitted;
        if (merkleBlockParameters != null) {
            final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
            if (merkleBlock.containsTransaction(transactionHash)) {
                merkleBlockParameters.addTransaction(transaction);
            }

            if (merkleBlockParameters.hasAllTransactions()) {
                _currentMerkleBlockBeingTransmitted = null;
                _executeAndClearCallbacks(_downloadMerkleBlockRequests, merkleBlock.getHash(), merkleBlockParameters, _threadPool);
            }
        }
    }

    protected void _onMerkleBlockReceived(final MerkleBlockMessage merkleBlockMessage) {
        final MerkleBlock merkleBlock = merkleBlockMessage.getMerkleBlock();
        final Boolean merkleBlockIsValid = merkleBlock.isValid();

        final Sha256Hash blockHash = merkleBlock.getHash();

        if (! merkleBlockIsValid) {
            _executeAndClearCallbacks(_downloadMerkleBlockRequests, blockHash, null, _threadPool);
            return;
        }

        final PartialMerkleTree partialMerkleTree = merkleBlock.getPartialMerkleTree();
        final List<Sha256Hash> merkleTreeTransactionHashes = partialMerkleTree.getTransactionHashes();
        final int transactionCount = merkleTreeTransactionHashes.getSize();

        if (transactionCount == 0) {
            // No Transactions should be transmitted alongside this MerkleBlock, so execute any callbacks and return early.
            _executeAndClearCallbacks(_downloadMerkleBlockRequests, blockHash, new MerkleBlockParameters(merkleBlock), _threadPool);
            return;
        }

        // Wait for additional Transactions to be transmitted.
        //  NOTE: Not all Transactions listed within the MerkleTree will be broadcast, so receiving non-Transaction message will also trigger the completion of the MerkleBlock.
        _currentMerkleBlockBeingTransmitted = new MerkleBlockParameters(merkleBlock);
    }

    protected void _onBlockHeadersMessageReceived(final BlockHeadersMessage blockHeadersMessage) {
        final List<BlockHeader> blockHeaders = blockHeadersMessage.getBlockHeaders();

        final Boolean allBlockHeadersAreValid;
        {
            Boolean isValid = true;
            for (final BlockHeader blockHeader : blockHeaders) {
                if (! blockHeader.isValid()) {
                    isValid = false;
                    break;
                }
            }
            allBlockHeadersAreValid = isValid;
        }

        if (blockHeaders.isEmpty()) { return; }

        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        _executeAndClearCallbacks(_downloadBlockHeadersRequests, firstBlockHeader.getPreviousBlockHash(), (allBlockHeadersAreValid ? blockHeaders : null), _threadPool);
    }

    protected void _onQueryBlocksMessageReceived(final QueryBlocksMessage queryBlocksMessage) {
        final QueryBlocksCallback queryBlocksCallback = _queryBlocksCallback;

        if (queryBlocksCallback != null) {
            final MutableList<Sha256Hash> blockHeaderHashes = new MutableList<Sha256Hash>(queryBlocksMessage.getBlockHashes());
            final Sha256Hash desiredBlockHeaderHash = queryBlocksMessage.getStopBeforeBlockHash();
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    queryBlocksCallback.run(blockHeaderHashes, desiredBlockHeaderHash, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for QueryBlocks message.");
        }
    }

    protected void _onQueryUnconfirmedTransactionsReceived() {
        final QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback = _queryUnconfirmedTransactionsCallback;
        if (queryUnconfirmedTransactionsCallback == null) {
            Logger.log("NOTICE: No handler set for QueryUnconfirmedTransactions (Mempool) message.");
            return;
        }

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                queryUnconfirmedTransactionsCallback.run(BitcoinNode.this);
            }
        });
    }

    protected void _onQueryBlockHeadersMessageReceived(final RequestBlockHeadersMessage requestBlockHeadersMessage) {
        final QueryBlockHeadersCallback queryBlockHeadersCallback = _queryBlockHeadersCallback;

        if (queryBlockHeadersCallback != null) {
            final List<Sha256Hash> blockHeaderHashes = requestBlockHeadersMessage.getBlockHeaderHashes();
            final Sha256Hash desiredBlockHeaderHash = requestBlockHeadersMessage.getStopBeforeBlockHash();
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    queryBlockHeadersCallback.run(blockHeaderHashes, desiredBlockHeaderHash, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for QueryBlockHeaders message.");
        }
    }

    protected void _onRequestExtraThinBlockMessageReceived(final RequestExtraThinBlockMessage requestExtraThinBlockMessage) {
        final RequestExtraThinBlockCallback requestExtraThinBlockCallback = _requestExtraThinBlockCallback;

        if (requestExtraThinBlockCallback != null) {
            final InventoryItem inventoryItem = requestExtraThinBlockMessage.getInventoryItem();
            if (inventoryItem.getItemType() != InventoryItemType.EXTRA_THIN_BLOCK) { return; }

            final Sha256Hash blockHash = inventoryItem.getItemHash();
            final BloomFilter bloomFilter = requestExtraThinBlockMessage.getBloomFilter();
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    requestExtraThinBlockCallback.run(blockHash, bloomFilter, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for RequestExtraThinBlock message.");
        }
    }

    protected void _onRequestExtraThinTransactionsMessageReceived(final RequestExtraThinTransactionsMessage requestExtraThinTransactionsMessage) {
        final RequestExtraThinTransactionCallback requestExtraThinTransactionCallback = _requestExtraThinTransactionCallback;

        if (requestExtraThinTransactionCallback != null) {
            final Sha256Hash blockHash = requestExtraThinTransactionsMessage.getBlockHash();
            final List<ByteArray> transactionShortHashes = requestExtraThinTransactionsMessage.getTransactionShortHashes();

            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    requestExtraThinTransactionCallback.run(blockHash, transactionShortHashes, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for RequestExtraThinBlock message.");
        }
    }

    protected void _onThinBlockMessageReceived(final ThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<Sha256Hash> transactionHashes = blockMessage.getTransactionHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ThinBlockParameters thinBlockParameters = new ThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        _executeAndClearCallbacks(_downloadThinBlockRequests, blockHash, (blockHeaderIsValid ? thinBlockParameters : null), _threadPool);
    }

    protected void _onExtraThinBlockMessageReceived(final ExtraThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<ByteArray> transactionHashes = blockMessage.getTransactionShortHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ExtraThinBlockParameters extraThinBlockParameters = new ExtraThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        _executeAndClearCallbacks(_downloadExtraThinBlockRequests, blockHash, (blockHeaderIsValid ? extraThinBlockParameters : null), _threadPool);
    }

    protected void _onThinTransactionsMessageReceived(final ThinTransactionsMessage transactionsMessage) {
        final Sha256Hash blockHash = transactionsMessage.getBlockHash();
        final List<Transaction> transactions = transactionsMessage.getTransactions();

        _executeAndClearCallbacks(_downloadThinTransactionsRequests, blockHash, transactions, _threadPool);
    }

    protected void _onNotFoundMessageReceived(final NotFoundResponseMessage notFoundResponseMessage) {
        for (final InventoryItem inventoryItem : notFoundResponseMessage.getInventoryItems()) {
            final Sha256Hash itemHash = inventoryItem.getItemHash();
            switch (inventoryItem.getItemType()) {
                case BLOCK: {
                    synchronized (_downloadBlockRequests) {
                        final Set<DownloadBlockCallback> downloadBlockCallbacks = _downloadBlockRequests.remove(itemHash);
                        if (downloadBlockCallbacks == null) { return; }

                        for (final DownloadBlockCallback downloadBlockCallback : downloadBlockCallbacks) {
                            _threadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    downloadBlockCallback.onFailure(itemHash);
                                }
                            });
                        }
                    }
                } break;

                case TRANSACTION: {
                    synchronized (_downloadTransactionRequests) {
                        final Set<DownloadTransactionCallback> downloadTransactionCallbacks = _downloadTransactionRequests.remove(itemHash);
                        if (downloadTransactionCallbacks == null) { return; }

                        for (final DownloadTransactionCallback downloadTransactionCallback : downloadTransactionCallbacks) {
                            _threadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    final MutableList<Sha256Hash> transactionHashes = new MutableList<Sha256Hash>();
                                    transactionHashes.add(itemHash); // TODO: Consider batching failure message...
                                    downloadTransactionCallback.onFailure(transactionHashes);
                                }
                            });
                        }
                    }
                } break;

                case MERKLE_BLOCK: {
                    synchronized (_downloadMerkleBlockRequests) {
                        final Set<DownloadMerkleBlockCallback> downloadMerkleBlockCallbacks = _downloadMerkleBlockRequests.remove(itemHash);
                        if (downloadMerkleBlockCallbacks == null) { return; }

                        for (final DownloadMerkleBlockCallback downloadMerkleBlockCallback : downloadMerkleBlockCallbacks) {
                            _threadPool.execute(new Runnable() {
                                @Override
                                public void run() {
                                    downloadMerkleBlockCallback.onFailure(itemHash);
                                }
                            });
                        }
                    }
                } break;

                default: {
                    Logger.log("NOTICE: Unsolicited NOT_FOUND Message: " + inventoryItem.getItemType() + " : " + inventoryItem.getItemHash());
                }
            }
        }
    }

    protected void _onFeeFilterMessageReceived(final FeeFilterMessage feeFilterMessage) {
        // TODO: Store feeFilter in NodeDatabase...
    }

    protected void _onRequestPeersMessageReceived(final RequestPeersMessage requestPeersMessage) {
        final RequestPeersHandler requestPeersHandler = _requestPeersHandler;
        if (requestPeersHandler == null) { return; }

        final List<BitcoinNodeIpAddress> connectedPeers = requestPeersHandler.getConnectedPeers();
        final BitcoinNodeIpAddressMessage nodeIpAddressMessage = new BitcoinNodeIpAddressMessage();
        for (final BitcoinNodeIpAddress nodeIpAddress : connectedPeers) {
            nodeIpAddressMessage.addAddress(nodeIpAddress);
        }
        _queueMessage(nodeIpAddressMessage);
    }

    protected void _onSetTransactionBloomFilterMessageReceived(final SetTransactionBloomFilterMessage setTransactionBloomFilterMessage) {
        _bloomFilter = MutableBloomFilter.copyOf(setTransactionBloomFilterMessage.getBloomFilter());
    }

    protected void _onUpdateTransactionBloomFilterMessageReceived(final UpdateTransactionBloomFilterMessage updateTransactionBloomFilterMessage) {
        if (_bloomFilter != null) {
            _bloomFilter.addItem(updateTransactionBloomFilterMessage.getItem());
        }
    }

    protected void _onClearTransactionBloomFilterMessageReceived(final ClearTransactionBloomFilterMessage clearTransactionBloomFilterMessage) {
        _bloomFilter = null;
    }

    protected void _onQueryAddressBlocks(final QueryAddressBlocksMessage queryAddressBlocksMessage) {
        final RequestSpvBlocksCallback requestDataCallback = _requestSpvBlocksCallback;
        if (requestDataCallback != null) {
            final List<Address> addresses = queryAddressBlocksMessage.getAddresses().asConst();
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    requestDataCallback.run(addresses, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.log("NOTICE: No handler set for RequestSpvBlocks message.");
        }
    }

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        queryBlocksMessage.addBlockHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
    }

    protected void _requestMerkleBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.MERKLE_BLOCK, blockHash));

        final MutableList<BitcoinProtocolMessage> messages = new MutableList<BitcoinProtocolMessage>(2);
        messages.add(requestDataMessage);
        messages.add(new BitcoinPingMessage()); // A ping message is sent to ensure the remote node responds with a non-transaction message to close out the MerkleBlockMessage transmission.
        _queueMessages(messages);
    }

    protected void _requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.COMPACT_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.EXTRA_THIN_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestThinTransactions(final Sha256Hash blockHash, final List<ByteArray> transactionShortHashes) {
        final RequestExtraThinTransactionsMessage requestThinTransactionsMessage = new RequestExtraThinTransactionsMessage();
        requestThinTransactionsMessage.setBlockHash(blockHash);
        requestThinTransactionsMessage.setTransactionShortHashes(transactionShortHashes);

        _queueMessage(requestThinTransactionsMessage);
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes) {
        final RequestBlockHeadersMessage requestBlockHeadersMessage = new RequestBlockHeadersMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            requestBlockHeadersMessage.addBlockHeaderHash(blockHash);
        }
        _queueMessage(requestBlockHeadersMessage);
    }

    protected void _requestTransactions(final List<Sha256Hash> transactionHashes) {
        final RequestDataMessage requestTransactionMessage = new RequestDataMessage();
        for (final Sha256Hash transactionHash: transactionHashes) {
            requestTransactionMessage.addInventoryItem(new InventoryItem(InventoryItemType.TRANSACTION, transactionHash));
        }
        _queueMessage(requestTransactionMessage);
    }

    public void transmitBlockFinder(final List<Sha256Hash> blockHashes) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            queryBlocksMessage.addBlockHash(blockHash);
        }

        _queueMessage(queryBlocksMessage);
    }

    public void transmitTransaction(final Transaction transaction) {
        final TransactionMessage transactionMessage = new TransactionMessage();
        transactionMessage.setTransaction(transaction);
        _queueMessage(transactionMessage);
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash) {
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);
        _requestBlock(blockHash);
    }

    public void requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback downloadMerkleBlockCallback) {
        _storeInMapSet(_downloadMerkleBlockRequests, blockHash, downloadMerkleBlockCallback);
        _requestMerkleBlock(blockHash);
    }

    public void requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadExtraThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadExtraThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestExtraThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestThinTransactions(final Sha256Hash blockHash, final List<Sha256Hash> transactionHashes, final DownloadThinTransactionsCallback downloadThinBlockCallback) {
        final ImmutableListBuilder<ByteArray> shortTransactionHashesBuilder = new ImmutableListBuilder<ByteArray>(transactionHashes.getSize());
        for (final Sha256Hash transactionHash : transactionHashes) {
            final ByteArray shortTransactionHash = MutableByteArray.wrap(transactionHash.getBytes(0, 8));
            shortTransactionHashesBuilder.add(shortTransactionHash);
        }
        final List<ByteArray> shortTransactionHashes = shortTransactionHashesBuilder.build();

        _storeInMapSet(_downloadThinTransactionsRequests, blockHash, downloadThinBlockCallback);
        _requestThinTransactions(blockHash, shortTransactionHashes);
    }

    public void requestBlockHeaders(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback downloadBlockHeaderCallback) {
        if (blockHashes.isEmpty()) { return; }

        final Sha256Hash firstBlockHash = blockHashes.get(0);
        _storeInMapSet(_downloadBlockHeadersRequests, firstBlockHash, downloadBlockHeaderCallback);
        _requestBlockHeaders(blockHashes);
    }

    public void requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback downloadTransactionCallback) {
        if (transactionHashes.isEmpty()) { return; }

        for (final Sha256Hash transactionHash : transactionHashes) {
            _storeInMapSet(_downloadTransactionRequests, transactionHash, downloadTransactionCallback);
        }
        _requestTransactions(transactionHashes);
    }

    public void transmitTransactionHashes(final List<Sha256Hash> transactionHashes) {
        final InventoryMessage inventoryMessage = new InventoryMessage();
        for (final Sha256Hash transactionHash : transactionHashes) {
            final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.TRANSACTION, transactionHash);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        _queueMessage(inventoryMessage);
    }

    public void transmitBlockHashes(final List<Sha256Hash> blockHashes) {
        final InventoryMessage inventoryMessage = new InventoryMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.BLOCK, blockHash);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        _queueMessage(inventoryMessage);
    }

    public void transmitBlockHeader(final BlockHeader blockHeader, final Integer transactionCount) {
        final BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage();

        final BlockHeaderWithTransactionCount blockHeaderWithTransactionCount = new ImmutableBlockHeaderWithTransactionCount(blockHeader, transactionCount);
        blockHeadersMessage.addBlockHeader(blockHeaderWithTransactionCount);

        _queueMessage(blockHeadersMessage);
    }

    public void transmitBlockHeader(final BlockHeaderWithTransactionCount blockHeader) {
        final BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage();
        blockHeadersMessage.addBlockHeader(blockHeader);
        _queueMessage(blockHeadersMessage);
    }

    public void setBloomFilter(final BloomFilter bloomFilter) {
        final SetTransactionBloomFilterMessage bloomFilterMessage = new SetTransactionBloomFilterMessage();
        bloomFilterMessage.setBloomFilter(bloomFilter);
        _queueMessage(bloomFilterMessage);
    }

    public void transmitBlockHeaders(final List<BlockHeader> blockHeaders) {
        final BlockHeadersMessage blockHeadersMessage = new BlockHeadersMessage();
        for (final BlockHeader blockHeader : blockHeaders) {
            blockHeadersMessage.addBlockHeader(blockHeader);
        }
        _queueMessage(blockHeadersMessage);
    }

    public void transmitBlock(final Block block) {
        final BlockMessage blockMessage = new BlockMessage();
        blockMessage.setBlock(block);
        _queueMessage(blockMessage);
    }

    public void transmitMerkleBlock(final Block block) {
        final MutableBloomFilter bloomFilter = _bloomFilter;
        if (bloomFilter == null) {
            // NOTE: When a MerkleBlock is requested without a BloomFilter set, Bitcoin XT sends a MerkleBlock w/ BloomFilter.MATCH_ALL.
            Logger.log("NOTICE: Attempting to Transmit MerkleBlock when no BloomFilter is available.");
            final BlockMessage blockMessage = new BlockMessage();
            blockMessage.setBlock(block);
            _queueMessage(blockMessage);
        }
        else {
            // The response to a MerkleBlock request is a combination of messages.
            //  1. The first message should be the MerkleBlock itself.
            //  2. Immediately following should be the any transactions that match the Node's bloomFilter.
            //  3. Finally, since the receiving node has no way to determine if the transaction stream is complete, a ping message is sent to interrupt the flow.
            final MutableList<BitcoinProtocolMessage> messages = new MutableList<BitcoinProtocolMessage>();

            final MerkleBlockMessage merkleBlockMessage = new MerkleBlockMessage();
            merkleBlockMessage.setBlockHeader(block);
            merkleBlockMessage.setPartialMerkleTree(block.getPartialMerkleTree(bloomFilter));
            messages.add(merkleBlockMessage);

            // BIP37 dictates that matched transactions be separately relayed...
            //  "In addition, because a merkleblock message contains only a list of transaction hashes, transactions
            //      matching the filter should also be sent in separate tx messages after the merkleblock is sent. This
            //      avoids a slow roundtrip that would otherwise be required (receive hashes, didn't see some of these
            //      transactions yet, ask for them)."

            final UpdateBloomFilterMode updateBloomFilterMode = Util.coalesce(UpdateBloomFilterMode.valueOf(bloomFilter.getUpdateMode()), UpdateBloomFilterMode.READ_ONLY);
            final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode);
            final List<Transaction> transactions = block.getTransactions();
            for (final Transaction transaction : transactions) {
                final Boolean transactionMatches = transactionBloomFilterMatcher.shouldInclude(transaction);
                if (transactionMatches) {
                    final TransactionMessage transactionMessage = new TransactionMessage();
                    transactionMessage.setTransaction(transaction);
                    messages.add(transactionMessage);
                }
            }

            // NOTE: A ping message is queued to inform the node that no more transactions follow...
            //  This isn't directly called out in the specification, but is a logical convention.
            messages.add(_createPingMessage());

            _queueMessages(messages);
        }
    }

    public void setSynchronizationStatusHandler(final SynchronizationStatus synchronizationStatus) {
        _synchronizationStatus = synchronizationStatus;
    }

    public void setQueryBlocksCallback(final QueryBlocksCallback queryBlocksCallback) {
        _queryBlocksCallback = queryBlocksCallback;
    }

    public void setQueryBlockHeadersCallback(final QueryBlockHeadersCallback queryBlockHeadersCallback) {
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
    }

    public void setRequestDataCallback(final RequestDataCallback requestDataCallback) {
        _requestDataMessageCallback = requestDataCallback;
    }

    public void setRequestSpvBlocksCallback(final RequestSpvBlocksCallback requestSpvBlocksCallback) {
        _requestSpvBlocksCallback = requestSpvBlocksCallback;
    }

    public void setBlockInventoryMessageHandler(final BlockInventoryMessageCallback blockInventoryMessageHandler) {
        _blockInventoryMessageHandler = blockInventoryMessageHandler;
    }

    public void setRequestPeersHandler(final RequestPeersHandler requestPeersHandler) {
        _requestPeersHandler = requestPeersHandler;
    }

    public void setQueryUnconfirmedTransactionsCallback(final QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback) {
        _queryUnconfirmedTransactionsCallback = queryUnconfirmedTransactionsCallback;
    }

    public void setRequestExtraThinBlockCallback(final RequestExtraThinBlockCallback requestExtraThinBlockCallback) {
        _requestExtraThinBlockCallback = requestExtraThinBlockCallback;
    }

    public void setTransactionsAnnouncementCallback(final TransactionInventoryMessageCallback transactionsAnnouncementCallback) {
        _transactionsAnnouncementCallback = transactionsAnnouncementCallback;
    }

    public void setSpvBlockInventoryMessageCallback(final SpvBlockInventoryMessageCallback spvBlockInventoryMessageCallback) {
        _spvBlockInventoryMessageCallback = spvBlockInventoryMessageCallback;
    }

    public Boolean newBlocksViaHeadersIsEnabled() {
        return _announceNewBlocksViaHeadersIsEnabled;
    }

    public Boolean supportsExtraThinBlocks() {
        if (_synchronizeVersionMessage == null) { return false; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        return nodeFeatures.hasFeatureFlagEnabled(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED);
    }

    public String getUserAgent() {
        if (_synchronizeVersionMessage == null) { return null; }
        return _synchronizeVersionMessage.getUserAgent();
    }

    public Boolean hasFeatureEnabled(final NodeFeatures.Feature feature) {
        if (_synchronizeVersionMessage == null) { return null; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        return nodeFeatures.hasFeatureFlagEnabled(feature);
    }

    /**
     * Tells the remote peer to not send new transactions to this node.
     *  This function must be set before the handshake is started in order to have an affect.
     */
    public void enableTransactionRelay(final Boolean transactionRelayIsEnabled) {
        _transactionRelayIsEnabled = transactionRelayIsEnabled;
        // TODO: Consider initializing a new handshake to update the relay preference...
    }

    /**
     * Returns if the remote peer has enabled transaction relay.
     *  If the node has not completed its handshake, null is returned.
     */
    public Boolean isTransactionRelayEnabled() {
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = _synchronizeVersionMessage;
        if (synchronizeVersionMessage == null) { return null; }

        return (synchronizeVersionMessage.transactionRelayIsEnabled());
    }

    public void queueMessage(final BitcoinProtocolMessage protocolMessage) {
        _queueMessage(protocolMessage);
    }

    public Boolean hasBloomFilter() {
        return (_bloomFilter != null);
    }

    /**
     * Returns true if the Transaction matches the BitcoinNode's BloomFilter, or if a BloomFilter has not been set.
     *  The BitcoinNode's BloomFilter is updated as necessary, as specified by the peer.
     */
    public Boolean matchesFilter(final Transaction transaction) {
        final MutableBloomFilter bloomFilter = _bloomFilter;
        if (bloomFilter == null) { return true; }

        final UpdateBloomFilterMode updateBloomFilterMode = Util.coalesce(UpdateBloomFilterMode.valueOf(bloomFilter.getUpdateMode()), UpdateBloomFilterMode.READ_ONLY);
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode);
        return transactionBloomFilterMatcher.shouldInclude(transaction);
    }

    /**
     * Returns true if the Transaction matches the BitcoinNode's BloomFilter, or if a BloomFilter has not been set.
     */
    public Boolean matchesFilter(final Transaction transaction, final UpdateBloomFilterMode updateBloomFilterMode) {
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(_bloomFilter, updateBloomFilterMode);
        return transactionBloomFilterMatcher.shouldInclude(transaction);
    }

    public void setBatchContinueHash(final Sha256Hash hash) {
        _batchContinueHash = (hash != null ? hash.asConst() : null);
    }

    public Sha256Hash getBatchContinueHash() {
        return _batchContinueHash;
    }

    // https://en.bitcoin.it/wiki/Satoshi_Client_Block_Exchange#Batch_Continue_Mechanism
    public void transmitBatchContinueHash(final Sha256Hash headBlockHash) {
        final InventoryMessage inventoryMessage = new InventoryMessage();
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.BLOCK, headBlockHash);
        inventoryMessage.addInventoryItem(inventoryItem);
        _queueMessage(inventoryMessage);
    }

    public void getAddressBlocks(final List<Address> addresses) {
        final QueryAddressBlocksMessage queryAddressBlocksMessage = new QueryAddressBlocksMessage();
        for (final Address address : addresses) {
            queryAddressBlocksMessage.addAddress(address);
        }
        _queueMessage(queryAddressBlocksMessage);
    }

    @Override
    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        if (_localNodeIpAddress == null) { return null; }
        return ((BitcoinNodeIpAddress) _localNodeIpAddress).copy();
    }

    @Override
    public BitcoinNodeIpAddress getRemoteNodeIpAddress() {
        final NodeIpAddress nodeIpAddress = super.getRemoteNodeIpAddress();
        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
        if (_synchronizeVersionMessage != null) {
            bitcoinNodeIpAddress.setNodeFeatures(_synchronizeVersionMessage.getNodeFeatures());
        }

        return bitcoinNodeIpAddress;
    }

    public NodeFeatures getNodeFeatures() {
        if (_synchronizeVersionMessage == null) { return null; }
        return _synchronizeVersionMessage.getNodeFeatures();
    }
}

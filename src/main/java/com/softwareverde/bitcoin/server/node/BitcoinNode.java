package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.callback.Callback;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageFactory;
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
import com.softwareverde.bitcoin.server.message.type.node.feature.NewBlocksViaHeadersMessage;
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
import com.softwareverde.bitcoin.server.message.type.query.slp.QuerySlpStatusMessage;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.request.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.slp.EnableSlpTransactionsMessage;
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
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.AcknowledgeVersionMessage;
import com.softwareverde.network.p2p.message.type.NodeIpAddressMessage;
import com.softwareverde.network.p2p.message.type.PingMessage;
import com.softwareverde.network.p2p.message.type.PongMessage;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeConnection;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BitcoinNode extends Node {
    public static final Long MIN_MEGABYTES_PER_SECOND = (ByteUtil.Unit.Binary.MEBIBYTES / 8L); // 1mpbs, slower than 3G.

    protected static final AddressInflater DEFAULT_ADDRESS_INFLATER = new AddressInflater();

    public interface BitcoinNodeCallback { }

    public interface RequestPeersHandler {
        List<BitcoinNodeIpAddress> getConnectedPeers();
    }

    public interface BlockInventoryMessageCallback extends BitcoinNodeCallback {
        void onNewInventory(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes);
        void onNewHeaders(BitcoinNode bitcoinNode, List<BlockHeader> blockHeaders);
    }

    public interface DownloadBlockCallback extends Callback<Block>, BitcoinNodeCallback {
        default void onFailure(Sha256Hash blockHash) { }
    }

    public interface DownloadMerkleBlockCallback extends Callback<MerkleBlockParameters>, BitcoinNodeCallback {
        default void onFailure(Sha256Hash blockHash) { }
    }

    public interface DownloadBlockHeadersCallback extends Callback<List<BlockHeader>>, BitcoinNodeCallback { }

    public interface DownloadTransactionCallback extends Callback<Transaction>, BitcoinNodeCallback {
        default void onFailure(Sha256Hash transactionHash) { }
    }

    public interface DownloadThinBlockCallback extends Callback<ThinBlockParameters>, BitcoinNodeCallback { }

    public interface DownloadExtraThinBlockCallback extends Callback<ExtraThinBlockParameters>, BitcoinNodeCallback { }

    public interface DownloadThinTransactionsCallback extends Callback<List<Transaction>>, BitcoinNodeCallback { }

    public interface TransactionInventoryMessageCallback extends Callback<List<Sha256Hash>>, BitcoinNodeCallback {
        default void onResult(final List<Sha256Hash> transactionHashes, final Boolean isValid) {
            this.onResult(transactionHashes);
        }
    }

    public interface SpvBlockInventoryMessageCallback extends Callback<List<Sha256Hash>>, BitcoinNodeCallback { }

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

    public interface RequestSlpTransactionsCallback {
        void run(List<Sha256Hash> transactionHashes, BitcoinNode bitcoinNode);
        Boolean getSlpStatus(Sha256Hash transactionHash);
    }

    public interface RequestExtraThinBlockCallback {
        void run(Sha256Hash blockHash, BloomFilter bloomFilter, BitcoinNode bitcoinNode);
    }

    public interface RequestExtraThinTransactionCallback {
        void run(Sha256Hash blockHash, List<ByteArray> transactionShortHashes, BitcoinNode bitcoinNode);
    }

    public interface OnNewBloomFilterCallback {
        void run(BitcoinNode bitcoinNode);
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
            return Util.areEqual(_merkleBlock.getTransactionCount(), _transactions.getCount());
        }

        protected void addTransaction(final Transaction transaction) {
            _transactions.add(transaction.asConst());
        }
    }

    /**
     * Returns true iff a callback was executed.
     */
    protected static <U, T, S extends Callback<U>> Boolean _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value, final ThreadPool threadPool) {
        synchronized (callbackMap) {
            final Set<S> callbackSet = callbackMap.remove(key);
            if ((callbackSet == null) || (callbackSet.isEmpty())) { return false; }

            for (final S callback : callbackSet) {
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(value);
                    }
                });
            }
            return true;
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

    protected static <T, S extends Callback<?>> void _removeValueFromMapSet(final Map<T, Set<S>> sourceMap, final BitcoinNodeCallback callback) {
        synchronized (sourceMap) {
            final Iterator<Map.Entry<T, Set<S>>> iterator = sourceMap.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<T, Set<S>> entry = iterator.next();
                final Set<S> set = entry.getValue();
                set.remove(callback);

                if (set.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    public static SynchronizationStatus DEFAULT_STATUS_CALLBACK = new SynchronizationStatus() {
        @Override
        public State getState() { return State.ONLINE; }

        @Override
        public Boolean isBlockchainSynchronized() { return false; }

        @Override
        public Boolean isReadyForTransactions() { return false; }

        @Override
        public Boolean isShuttingDown() { return false; }

        @Override
        public Long getCurrentBlockHeight() { return 0L; }
    };

    protected final Runnable _requestMonitor;
    protected Thread _requestMonitorThread;

    protected final AddressInflater _addressInflater;
    protected final MessageRouter _messageRouter = new MessageRouter();

    // Requests Maps
    protected final ConcurrentLinkedQueue<FailableRequest> _requestTimers = new ConcurrentLinkedQueue<FailableRequest>();
    protected final Map<Sha256Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Sha256Hash, Set<DownloadBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadMerkleBlockCallback>> _downloadMerkleBlockRequests = new HashMap<Sha256Hash, Set<DownloadMerkleBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockHeadersCallback>> _downloadBlockHeadersRequests = new HashMap<Sha256Hash, Set<DownloadBlockHeadersCallback>>();
    protected final Map<Sha256Hash, Set<DownloadTransactionCallback>> _downloadTransactionRequests = new HashMap<Sha256Hash, Set<DownloadTransactionCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinBlockCallback>> _downloadThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadExtraThinBlockCallback>> _downloadExtraThinBlockRequests = new HashMap<Sha256Hash, Set<DownloadExtraThinBlockCallback>>();
    protected final Map<Sha256Hash, Set<DownloadThinTransactionsCallback>> _downloadThinTransactionsRequests = new HashMap<Sha256Hash, Set<DownloadThinTransactionsCallback>>();
    protected final Set<BlockInventoryMessageCallback> _downloadAddressBlocksRequests = new HashSet<BlockInventoryMessageCallback>();

    protected final BitcoinProtocolMessageFactory _protocolMessageFactory;
    protected final LocalNodeFeatures _localNodeFeatures;

    protected SynchronizationStatus _synchronizationStatus = DEFAULT_STATUS_CALLBACK;

    protected QueryBlocksCallback _queryBlocksCallback = null;
    protected QueryBlockHeadersCallback _queryBlockHeadersCallback = null;
    protected RequestDataCallback _requestDataMessageCallback = null;
    protected BlockInventoryMessageCallback _blockInventoryMessageHandler = null;
    protected RequestPeersHandler _requestPeersHandler = null;
    protected QueryUnconfirmedTransactionsCallback _queryUnconfirmedTransactionsCallback = null;
    protected RequestSpvBlocksCallback _requestSpvBlocksCallback = null;
    protected RequestSlpTransactionsCallback _requestSlpTransactionsCallback = null;

    protected RequestExtraThinBlockCallback _requestExtraThinBlockCallback = null;
    protected RequestExtraThinTransactionCallback _requestExtraThinTransactionCallback = null;

    protected BitcoinSynchronizeVersionMessage _synchronizeVersionMessage = null;

    protected TransactionInventoryMessageCallback _transactionsAnnouncementCallback = null;
    protected SpvBlockInventoryMessageCallback _spvBlockInventoryMessageCallback = null;

    protected Boolean _announceNewBlocksViaHeadersIsEnabled = false;
    protected Integer _compactBlocksVersion = null;
    protected Boolean _slpTransactionsIsEnabled = false;

    protected OnNewBloomFilterCallback _onNewBloomFilterCallback = null;
    protected Boolean _transactionRelayIsEnabled = true;
    protected Boolean _slpValidityCheckingIsEnabled = false;

    protected MutableBloomFilter _bloomFilter = null;
    protected Sha256Hash _batchContinueHash = null; // https://en.bitcoin.it/wiki/Satoshi_Client_Block_Exchange#Batch_Continue_Mechanism

    protected MerkleBlockParameters _currentMerkleBlockBeingTransmitted = null; // Represents the currently MerkleBlock being transmitted from the node. Becomes unset after a non-transaction message is received.

    protected Long _blockHeight = null; // TODO: Update blockHeight as new blocks are advertised by the Node...

    protected void _removeCallback(final BitcoinNodeCallback callback) {
        _removeValueFromMapSet(_downloadBlockRequests, callback);
        _removeValueFromMapSet(_downloadMerkleBlockRequests, callback);
        _removeValueFromMapSet(_downloadBlockHeadersRequests, callback);
        _removeValueFromMapSet(_downloadTransactionRequests, callback);
        _removeValueFromMapSet(_downloadThinBlockRequests, callback);
        _removeValueFromMapSet(_downloadExtraThinBlockRequests, callback);
        _removeValueFromMapSet(_downloadThinTransactionsRequests, callback);

        synchronized (_downloadAddressBlocksRequests) { _downloadAddressBlocksRequests.remove(callback); }
    }

    protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
        if (callback instanceof DownloadBlockCallback) {
            final float buffer = 2.0F;
            return (long) ((BlockInflater.MAX_BYTE_COUNT / BitcoinNode.MIN_MEGABYTES_PER_SECOND) * buffer * 1000L);
        }

        return (30L * 1000L); // 30 seconds...
    }

    protected void _requestAddressBlocks(final List<Address> addresses) {
        final QueryAddressBlocksMessage queryAddressBlocksMessage = _protocolMessageFactory.newQueryAddressBlocksMessage();
        for (final Address address : addresses) {
            queryAddressBlocksMessage.addAddress(address);
        }
        _queueMessage(queryAddressBlocksMessage);
    }

    @Override
    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
        if (synchronizeVersionMessage instanceof BitcoinSynchronizeVersionMessage) {
            _synchronizeVersionMessage = (BitcoinSynchronizeVersionMessage) synchronizeVersionMessage;
            _blockHeight = _synchronizeVersionMessage.getCurrentBlockHeight();
        }
        else {
            Logger.warn("Invalid SynchronizeVersionMessage type provided to BitcoinNode.");
        }

        super._onSynchronizeVersion(synchronizeVersionMessage);
    }

    @Override
    protected PingMessage _createPingMessage() {
        return _protocolMessageFactory.newPingMessage();
    }

    @Override
    protected PongMessage _createPongMessage(final PingMessage pingMessage) {
        final BitcoinPongMessage pongMessage = _protocolMessageFactory.newPongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
        return pongMessage;
    }

    @Override
    protected void _disconnect() {
        synchronized (this) {
            if (_requestMonitorThread != null) {
                _requestMonitorThread.interrupt();
                _requestMonitorThread = null;
            }
        }

        { // Unset all callback and handlers...
            _queryBlocksCallback = null;
            _queryBlockHeadersCallback = null;
            _requestDataMessageCallback = null;
            _requestSpvBlocksCallback = null;
            _requestSlpTransactionsCallback = null;
            _blockInventoryMessageHandler = null;
            _requestExtraThinBlockCallback = null;
            _requestExtraThinTransactionCallback = null;
            _transactionsAnnouncementCallback = null;
            _spvBlockInventoryMessageCallback = null;
        }

        super._disconnect();

        synchronized (_downloadBlockRequests) {
            for (final Sha256Hash sha256Hash : _downloadBlockRequests.keySet()) {
                for (final DownloadBlockCallback callback : _downloadBlockRequests.get(sha256Hash)) {
                    callback.onFailure(sha256Hash);
                }
            }
            _downloadBlockRequests.clear();
        }

        synchronized (_downloadMerkleBlockRequests) {
            for (final Sha256Hash sha256Hash : _downloadMerkleBlockRequests.keySet()) {
                for (final DownloadMerkleBlockCallback callback : _downloadMerkleBlockRequests.get(sha256Hash)) {
                    callback.onFailure(sha256Hash);
                }
            }

            _downloadMerkleBlockRequests.clear();
        }

        synchronized (_downloadTransactionRequests) {
            for (final Sha256Hash transactionHash : _downloadTransactionRequests.keySet()) {
                for (final DownloadTransactionCallback callback : _downloadTransactionRequests.get(transactionHash)) {
                    callback.onFailure(transactionHash);
                }
            }

            _downloadTransactionRequests.clear();
        }

        synchronized (_downloadBlockHeadersRequests) { _downloadBlockHeadersRequests.clear(); }
        synchronized (_downloadThinBlockRequests) { _downloadThinBlockRequests.clear(); }
        synchronized (_downloadExtraThinBlockRequests) { _downloadExtraThinBlockRequests.clear(); }
        synchronized (_downloadThinTransactionsRequests) { _downloadThinTransactionsRequests.clear(); }
    }

    @Override
    protected void _onConnect() {
        synchronized (this) {
            final Thread existingRequestMonitorThread = _requestMonitorThread;
            if (existingRequestMonitorThread != null) {
                existingRequestMonitorThread.interrupt();
            }

            _requestMonitorThread = new Thread(_requestMonitor);
            _requestMonitorThread.setName("Bitcoin Node - Request Monitor - " + _connection.toString());
            _requestMonitorThread.setDaemon(true); // Ensure the thread is closed when the process dies (unnecessary, but proper).
            _requestMonitorThread.start();
        }
        super._onConnect();
    }

    @Override
    protected SynchronizeVersionMessage _createSynchronizeVersionMessage() {
        final BitcoinSynchronizeVersionMessage synchronizeVersionMessage = _protocolMessageFactory.newSynchronizeVersionMessage();

        final NodeFeatures nodeFeatures = _localNodeFeatures.getNodeFeatures();
        synchronizeVersionMessage.setNodeFeatures(nodeFeatures);

        synchronizeVersionMessage.setTransactionRelayIsEnabled(_transactionRelayIsEnabled);
        synchronizeVersionMessage.setCurrentBlockHeight(_synchronizationStatus.getCurrentBlockHeight());

        { // Set Remote NodeIpAddress...
            final BitcoinNodeIpAddress remoteNodeIpAddress = new BitcoinNodeIpAddress();
            remoteNodeIpAddress.setIp(_connection.getIp());
            remoteNodeIpAddress.setPort(_connection.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        }

        if (_localNodeIpAddress != null) { // Set Local NodeIpAddress...
            final BitcoinNodeIpAddress remoteNodeIpAddress = new BitcoinNodeIpAddress();
            remoteNodeIpAddress.setIp(_localNodeIpAddress.getIp());
            remoteNodeIpAddress.setPort(_localNodeIpAddress.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setLocalAddress(remoteNodeIpAddress);
        }

        return synchronizeVersionMessage;
    }

    @Override
    protected AcknowledgeVersionMessage _createAcknowledgeVersionMessage(final SynchronizeVersionMessage synchronizeVersionMessage) {
        return _protocolMessageFactory.newAcknowledgeVersionMessage();
    }

    @Override
    protected NodeIpAddressMessage _createNodeIpAddressMessage() {
        return _protocolMessageFactory.newNodeIpAddressMessage();
    }

    protected void _checkForFailedRequests() {
        final Long nowMs = _systemTime.getCurrentTimeInMilliSeconds();

        final Iterator<FailableRequest> iterator = _requestTimers.iterator();
        while (iterator.hasNext()) {
            final FailableRequest failableRequest = iterator.next();
            final Long maxRequestAgeMs = _getMaximumTimeoutMs(failableRequest.callback);
            final long requestAgeMs = (nowMs - failableRequest.requestStartTimeMs);

            if (requestAgeMs > maxRequestAgeMs) {
                iterator.remove();

                _removeCallback(failableRequest.callback);
                _threadPool.execute(failableRequest.onFailure);
            }
        }
    }

    protected Runnable _createRequestMonitor() {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    final Thread currentThread = Thread.currentThread();
                    while ((! currentThread.isInterrupted()) && BitcoinNode.this.isConnected()) {
                        try { Thread.sleep(1000L); }
                        catch (final Exception exception) { break; }

                        _checkForFailedRequests();
                    }
                }
                finally {
                    synchronized (BitcoinNode.this) {
                        if (_requestMonitorThread == Thread.currentThread()) {
                            _requestMonitorThread = null;
                        }
                    }
                }
            }
        };
    }

    @Override
    protected void _initConnection() {
        _connection.setMessageReceivedCallback(new NodeConnection.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(final ProtocolMessage protocolMessage) {
                if (! (protocolMessage instanceof BitcoinProtocolMessage)) {
                    Logger.info("NOTICE: Disregarding Non-Bitcoin ProtocolMessage.");
                    return;
                }

                final BitcoinProtocolMessage message = (BitcoinProtocolMessage) protocolMessage;

                final MessageType messageType = message.getCommand();
                if (messageType != MessageType.INVENTORY) {
                    Logger.debug("Received: " + message.getCommand() + " from " + BitcoinNode.this.getConnectionString());
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

                _messageRouter.route(message.getCommand(), message);
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

    protected void _defineRoutes() {
        _messageRouter.addRoute(MessageType.PING,                           (final ProtocolMessage message) -> { _onPingReceived((BitcoinPingMessage) message); });
        _messageRouter.addRoute(MessageType.PONG,                           (final ProtocolMessage message) -> { _onPongReceived((BitcoinPongMessage) message); });
        _messageRouter.addRoute(MessageType.SYNCHRONIZE_VERSION,            (final ProtocolMessage message) -> { _onSynchronizeVersion((SynchronizeVersionMessage) message); });
        _messageRouter.addRoute(MessageType.ACKNOWLEDGE_VERSION,            (final ProtocolMessage message) -> { _onAcknowledgeVersionMessageReceived((BitcoinAcknowledgeVersionMessage) message); });
        _messageRouter.addRoute(MessageType.NODE_ADDRESSES,                 (final ProtocolMessage message) -> { _onNodeAddressesReceived((BitcoinNodeIpAddressMessage) message); });
        _messageRouter.addRoute(MessageType.ERROR,                          (final ProtocolMessage message) -> { _onErrorMessageReceived((ErrorMessage) message); });
        _messageRouter.addRoute(MessageType.INVENTORY,                      (final ProtocolMessage message) -> { _onInventoryMessageReceived((InventoryMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_DATA,                   (final ProtocolMessage message) -> { _onRequestDataMessageReceived((RequestDataMessage) message); });
        _messageRouter.addRoute(MessageType.BLOCK,                          (final ProtocolMessage message) -> { _onBlockMessageReceived((BlockMessage) message); });
        _messageRouter.addRoute(MessageType.TRANSACTION,                    (final ProtocolMessage message) -> { _onTransactionMessageReceived((TransactionMessage) message); });
        _messageRouter.addRoute(MessageType.MERKLE_BLOCK,                   (final ProtocolMessage message) -> { _onMerkleBlockReceived((MerkleBlockMessage) message); });
        _messageRouter.addRoute(MessageType.BLOCK_HEADERS,                  (final ProtocolMessage message) -> { _onBlockHeadersMessageReceived((BlockHeadersMessage) message); });
        _messageRouter.addRoute(MessageType.QUERY_BLOCKS,                   (final ProtocolMessage message) -> { _onQueryBlocksMessageReceived((QueryBlocksMessage) message); });
        _messageRouter.addRoute(MessageType.QUERY_UNCONFIRMED_TRANSACTIONS, (final ProtocolMessage message) -> { _onQueryUnconfirmedTransactionsReceived(); });
        _messageRouter.addRoute(MessageType.REQUEST_BLOCK_HEADERS,          (final ProtocolMessage message) -> { _onQueryBlockHeadersMessageReceived((RequestBlockHeadersMessage) message); });
        _messageRouter.addRoute(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS,  (final ProtocolMessage message) -> { _announceNewBlocksViaHeadersIsEnabled = true; });
        _messageRouter.addRoute(MessageType.ENABLE_COMPACT_BLOCKS,          (final ProtocolMessage message) -> {
            final EnableCompactBlocksMessage enableCompactBlocksMessage = (EnableCompactBlocksMessage) message;
            _compactBlocksVersion = (enableCompactBlocksMessage.isEnabled() ? enableCompactBlocksMessage.getVersion() : null);
        });
        _messageRouter.addRoute(MessageType.REQUEST_EXTRA_THIN_BLOCK,       (final ProtocolMessage message) -> { _onRequestExtraThinBlockMessageReceived((RequestExtraThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.EXTRA_THIN_BLOCK,               (final ProtocolMessage message) -> { _onExtraThinBlockMessageReceived((ExtraThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.THIN_BLOCK,                     (final ProtocolMessage message) -> { _onThinBlockMessageReceived((ThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS,(final ProtocolMessage message) -> { _onRequestExtraThinTransactionsMessageReceived((RequestExtraThinTransactionsMessage) message); });
        _messageRouter.addRoute(MessageType.THIN_TRANSACTIONS,              (final ProtocolMessage message) -> { _onThinTransactionsMessageReceived((ThinTransactionsMessage) message); });
        _messageRouter.addRoute(MessageType.NOT_FOUND,                      (final ProtocolMessage message) -> { _onNotFoundMessageReceived((NotFoundResponseMessage) message); });
        _messageRouter.addRoute(MessageType.FEE_FILTER,                     (final ProtocolMessage message) -> { _onFeeFilterMessageReceived((FeeFilterMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_PEERS,                  (final ProtocolMessage message) -> { _onRequestPeersMessageReceived((RequestPeersMessage) message); });
        _messageRouter.addRoute(MessageType.SET_TRANSACTION_BLOOM_FILTER,   (final ProtocolMessage message) -> { _onSetTransactionBloomFilterMessageReceived((SetTransactionBloomFilterMessage) message); });
        _messageRouter.addRoute(MessageType.UPDATE_TRANSACTION_BLOOM_FILTER,(final ProtocolMessage message) -> { _onUpdateTransactionBloomFilterMessageReceived((UpdateTransactionBloomFilterMessage) message); });
        _messageRouter.addRoute(MessageType.CLEAR_TRANSACTION_BLOOM_FILTER, (final ProtocolMessage message) -> { _onClearTransactionBloomFilterMessageReceived((ClearTransactionBloomFilterMessage) message); });
        _messageRouter.addRoute(MessageType.QUERY_ADDRESS_BLOCKS,           (final ProtocolMessage message) -> { _onQueryAddressBlocks((QueryAddressBlocksMessage) message); });
        _messageRouter.addRoute(MessageType.ENABLE_SLP_TRANSACTIONS,        (final ProtocolMessage message) -> {
            final EnableSlpTransactionsMessage enableSlpTransactionsMessage = (EnableSlpTransactionsMessage) message;
            _slpTransactionsIsEnabled = enableSlpTransactionsMessage.isEnabled();
        });
        _messageRouter.addRoute(MessageType.QUERY_SLP_STATUS,               (final ProtocolMessage message) -> { _onQuerySlpStatus((QuerySlpStatusMessage) message); });

        _messageRouter.setUnknownRouteHandler(new MessageRouter.UnknownRouteHandler() {
            @Override
            public void run(final MessageType messageType, final ProtocolMessage message) {
                final BitcoinProtocolMessage bitcoinProtocolMessage = (BitcoinProtocolMessage) message;
                Logger.warn("Unhandled Message Command: "+ messageType +": 0x"+ HexUtil.toHexString(bitcoinProtocolMessage.getHeaderBytes()));
            }
        });
    }

    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        Logger.info("RECEIVED ERROR:" + rejectCode.getRejectMessageType().getValue() + " " + HexUtil.toHexString(new byte[] { rejectCode.getCode() }) + " " + errorMessage.getRejectDescription() + " " + HexUtil.toHexString(errorMessage.getExtraData()) + " " + this.getUserAgent() + " " + this.getConnectionString());
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
            Logger.debug("No handler set for RequestData message.");
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
                                blockInventoryMessageHandler.onNewInventory(BitcoinNode.this, objectHashes);
                            }
                        });
                    }
                    else {
                        Logger.debug("No handler set for BlockInventoryMessageHandler.");
                    }
                } break;

                case VALID_SLP_TRANSACTION:
                case INVALID_SLP_TRANSACTION:
                case TRANSACTION: {
                    final boolean isSlp = (inventoryItemType != InventoryItemType.TRANSACTION);

                    final TransactionInventoryMessageCallback transactionsAnnouncementCallback = _transactionsAnnouncementCallback;
                    if (transactionsAnnouncementCallback != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                if (isSlp) {
                                    final boolean isValid = (inventoryItemType == InventoryItemType.VALID_SLP_TRANSACTION);
                                    transactionsAnnouncementCallback.onResult(objectHashes, isValid);
                                }
                                else {
                                    transactionsAnnouncementCallback.onResult(objectHashes);
                                }
                            }
                        });
                    }
                    else {
                        Logger.debug("No handler set for TransactionInventoryMessageCallback.");
                    }
                } break;

                case MERKLE_BLOCK: {
                    if (Logger.isDebugEnabled()) {
                        for (final Sha256Hash objectHash : objectHashes) {
                            Logger.debug("Received AddressBlock: " + objectHash + " from " + _connection);
                        }
                    }

                    final Set<BlockInventoryMessageCallback> addressBlocksCallbacks;
                    synchronized (_downloadAddressBlocksRequests) {
                        addressBlocksCallbacks = new HashSet<BlockInventoryMessageCallback>(_downloadAddressBlocksRequests);
                        _downloadAddressBlocksRequests.clear();
                    }

                    final SpvBlockInventoryMessageCallback spvBlockInventoryMessageCallback = _spvBlockInventoryMessageCallback;
                    if (spvBlockInventoryMessageCallback != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                for (final BlockInventoryMessageCallback blockInventoryMessageCallback : addressBlocksCallbacks) {
                                    blockInventoryMessageCallback.onNewInventory(BitcoinNode.this, objectHashes);
                                }

                                spvBlockInventoryMessageCallback.onResult(objectHashes);
                            }
                        });
                    }
                    else {
                        Logger.debug("No handler set for SpvBlockInventoryMessageCallback.");
                    }
                } break;
            }
        }
    }

    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        if (block == null) {
            Logger.debug("Received invalid block message. " + blockMessage.getBytes());
            return;
        }

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
            final Set<DownloadMerkleBlockCallback> callbacks;
            synchronized (_downloadMerkleBlockRequests) {
                callbacks = _downloadMerkleBlockRequests.remove(blockHash);
            }
            if (callbacks != null) {
                for (final DownloadMerkleBlockCallback callback : callbacks) {
                    callback.onFailure(blockHash);
                }
            }
            return;
        }

        final PartialMerkleTree partialMerkleTree = merkleBlock.getPartialMerkleTree();
        final List<Sha256Hash> merkleTreeTransactionHashes = partialMerkleTree.getTransactionHashes();
        final int transactionCount = merkleTreeTransactionHashes.getCount();

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
        Logger.trace(this.getConnectionString() + " _onBlockHeadersMessageReceived: " + blockHeaders.getCount());

        final boolean announceNewBlocksViaHeadersIsEnabled = _announceNewBlocksViaHeadersIsEnabled;
        final boolean allBlockHeadersAreValid;
        {
            boolean isValid = true;
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
        final Boolean wasRequested = _executeAndClearCallbacks(_downloadBlockHeadersRequests, firstBlockHeader.getPreviousBlockHash(), (allBlockHeadersAreValid ? blockHeaders : null), _threadPool);

        Logger.trace(firstBlockHeader.getHash() + " was announced: " + (! wasRequested));
        if ( (! wasRequested) && announceNewBlocksViaHeadersIsEnabled ) {
            final BlockInventoryMessageCallback blockInventoryMessageHandler = _blockInventoryMessageHandler;
            if (blockInventoryMessageHandler != null) {
                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        blockInventoryMessageHandler.onNewHeaders(BitcoinNode.this, blockHeaders);
                    }
                });
            }
        }
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
            Logger.debug("No handler set for QueryBlocks message.");
        }
    }

    protected void _onQueryUnconfirmedTransactionsReceived() {
        final QueryUnconfirmedTransactionsCallback queryUnconfirmedTransactionsCallback = _queryUnconfirmedTransactionsCallback;
        if (queryUnconfirmedTransactionsCallback == null) {
            Logger.debug("No handler set for QueryUnconfirmedTransactions (Mempool) message.");
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
            final List<Sha256Hash> blockHeaderHashes = requestBlockHeadersMessage.getBlockHashes();
            final Sha256Hash desiredBlockHeaderHash = requestBlockHeadersMessage.getStopBeforeBlockHash();
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    queryBlockHeadersCallback.run(blockHeaderHashes, desiredBlockHeaderHash, BitcoinNode.this);
                }
            });
        }
        else {
            Logger.debug("No handler set for QueryBlockHeaders message.");
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
            Logger.debug("No handler set for RequestExtraThinBlock message.");
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
            Logger.debug("No handler set for RequestExtraThinBlock message.");
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
                                    downloadTransactionCallback.onFailure(itemHash);
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
                    Logger.info("Unsolicited NOT_FOUND Message: " + inventoryItem.getItemType() + " : " + inventoryItem.getItemHash());
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
        final BitcoinNodeIpAddressMessage nodeIpAddressMessage = _protocolMessageFactory.newNodeIpAddressMessage();
        for (final BitcoinNodeIpAddress nodeIpAddress : connectedPeers) {
            nodeIpAddressMessage.addAddress(nodeIpAddress);
        }
        _queueMessage(nodeIpAddressMessage);
    }

    protected void _onSetTransactionBloomFilterMessageReceived(final SetTransactionBloomFilterMessage setTransactionBloomFilterMessage) {
        _bloomFilter = MutableBloomFilter.copyOf(setTransactionBloomFilterMessage.getBloomFilter());
        _transactionRelayIsEnabled = true;

        final OnNewBloomFilterCallback onNewBloomFilterCallback = _onNewBloomFilterCallback;
        if (onNewBloomFilterCallback != null) {
            onNewBloomFilterCallback.run(this);
        }
    }

    protected void _onUpdateTransactionBloomFilterMessageReceived(final UpdateTransactionBloomFilterMessage updateTransactionBloomFilterMessage) {
        if (_bloomFilter != null) {
            _bloomFilter.addItem(updateTransactionBloomFilterMessage.getItem());
            _transactionRelayIsEnabled = true;
        }
    }

    protected void _onClearTransactionBloomFilterMessageReceived(final ClearTransactionBloomFilterMessage clearTransactionBloomFilterMessage) {
        _bloomFilter = null;
        _transactionRelayIsEnabled = true; // NOTE: This behavior mimics Bitcoin Unlimited...
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
            Logger.debug("No handler set for RequestSpvBlocks message.");
        }
    }

    private void _onQuerySlpStatus(final QuerySlpStatusMessage querySlpStatusMessage) {
        if (_slpTransactionsIsEnabled) {
            final RequestSlpTransactionsCallback requestDataCallback = _requestSlpTransactionsCallback;
            if (requestDataCallback != null) {
                final List<Sha256Hash> transactionHashes = querySlpStatusMessage.getHashes().asConst();
                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        requestDataCallback.run(transactionHashes, BitcoinNode.this);
                    }
                });
            }
            else {
                Logger.debug("No handler set for RequestSlpTransactions message.");
            }
        }
    }

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = _protocolMessageFactory.newQueryBlocksMessage();
        queryBlocksMessage.addBlockHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
    }

    protected void _requestMerkleBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.MERKLE_BLOCK, blockHash));

        final MutableList<BitcoinProtocolMessage> messages = new MutableList<BitcoinProtocolMessage>(2);
        messages.add(requestDataMessage);
        messages.add(_protocolMessageFactory.newPingMessage()); // A ping message is sent to ensure the remote node responds with a non-transaction message (Pong) to close out the MerkleBlockMessage transmission.
        _queueMessages(messages);
    }

    protected void _requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.COMPACT_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = _protocolMessageFactory.newRequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.EXTRA_THIN_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = _protocolMessageFactory.newRequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);
    }

    protected void _requestThinTransactions(final Sha256Hash blockHash, final List<ByteArray> transactionShortHashes) {
        final RequestExtraThinTransactionsMessage requestThinTransactionsMessage = _protocolMessageFactory.newRequestExtraThinTransactionsMessage();
        requestThinTransactionsMessage.setBlockHash(blockHash);
        requestThinTransactionsMessage.setTransactionShortHashes(transactionShortHashes);

        _queueMessage(requestThinTransactionsMessage);
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes) {
        final RequestBlockHeadersMessage requestBlockHeadersMessage = _protocolMessageFactory.newRequestBlockHeadersMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            requestBlockHeadersMessage.addBlockHash(blockHash);
        }
        _queueMessage(requestBlockHeadersMessage);
    }

    protected void _requestTransactions(final List<Sha256Hash> transactionHashes) {
        final RequestDataMessage requestTransactionMessage = _protocolMessageFactory.newRequestDataMessage();
        for (final Sha256Hash transactionHash : transactionHashes) {
            requestTransactionMessage.addInventoryItem(new InventoryItem(InventoryItemType.TRANSACTION, transactionHash));
        }
        _queueMessage(requestTransactionMessage);
    }

    protected void _requestSlpStatus(final List<Sha256Hash> transactionHashes) {
        if (! _slpValidityCheckingIsEnabled) {
            Logger.warn("Attempting to Request SLP Status for " + transactionHashes.getCount() + " transactions when SLP validity checking has not been enabled.");
        }
        else {
            final QuerySlpStatusMessage querySlpStatusMessage = _protocolMessageFactory.newQuerySlpStatusMessage();
            for (final Sha256Hash transactionHash : transactionHashes) {
                querySlpStatusMessage.addHash(transactionHash);
            }
            _queueMessage(querySlpStatusMessage);
        }
    }

    protected void _enableSlpValidityChecking(final Boolean shouldEnableSlpValidityChecking) {
        if (_synchronizeVersionMessage == null) {
            Logger.warn("Unable to set SLP validity checking for un-handshaked node: " + this.getRemoteNodeIpAddress());
            return;
        }

        final NodeFeatures remoteNodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        if (remoteNodeFeatures == null) {
            Logger.warn("Unable to verify SLP index node feature for node: " + this.getRemoteNodeIpAddress());
            return;
        }

        if (remoteNodeFeatures.hasFeatureFlagEnabled(NodeFeatures.Feature.SLP_INDEX_ENABLED)) {
            Logger.debug("Enabling SLP Validity checking for node " + this.getRemoteNodeIpAddress());
            final EnableSlpTransactionsMessage enableSlpTransactionsMessage = new EnableSlpTransactionsMessage();
            enableSlpTransactionsMessage.setIsEnabled(shouldEnableSlpValidityChecking);

            _queueMessage(enableSlpTransactionsMessage);

            _slpValidityCheckingIsEnabled = shouldEnableSlpValidityChecking;
        }
    }

    public BitcoinNode(final String host, final Integer port, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        this(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPool, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final String host, final Integer port, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        this(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, threadPool, localNodeFeatures, addressInflater);
    }

    public BitcoinNode(final String host, final Integer port, final BitcoinBinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        this(host, port, binaryPacketFormat, threadPool, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final String host, final Integer port, final BitcoinBinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        super(host, port, binaryPacketFormat, threadPool);
        _addressInflater = addressInflater;
        _localNodeFeatures = localNodeFeatures;

        _protocolMessageFactory = binaryPacketFormat.getProtocolMessageFactory();

        _requestMonitor = _createRequestMonitor();
        _requestMonitorThread = null;

        _defineRoutes();
        _initConnection();
    }

    /**
     * Constructs a BitcoinNode from an already-connected BinarySocket.
     *  The BinarySocket must have been created with a BitcoinProtocolMessageFactory.
     */
    public BitcoinNode(final BinarySocket binarySocket, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures) {
        this(binarySocket, threadPool, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final BinarySocket binarySocket, final ThreadPool threadPool, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        super(binarySocket, threadPool);
        _localNodeFeatures = localNodeFeatures;
        _addressInflater = addressInflater;

        final BinaryPacketFormat binaryPacketFormat = _connection.getBinaryPacketFormat();
        _protocolMessageFactory = (BitcoinProtocolMessageFactory) binaryPacketFormat.getProtocolMessageFactory();

        _requestMonitor = _createRequestMonitor();
        _requestMonitorThread = null;

        _defineRoutes();
        _initConnection();
    }

    public void transmitBlockFinder(final List<Sha256Hash> blockHashes) {
        final QueryBlocksMessage queryBlocksMessage = _protocolMessageFactory.newQueryBlocksMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            queryBlocksMessage.addBlockHash(blockHash);
        }

        _queueMessage(queryBlocksMessage);
    }

    public void transmitTransaction(final Transaction transaction) {
        final TransactionMessage transactionMessage = _protocolMessageFactory.newTransactionMessage();
        transactionMessage.setTransaction(transaction);
        _queueMessage(transactionMessage);
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash) {
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);

        _requestTimers.add(new FailableRequest(downloadBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadBlockCallback.onFailure(blockHash);
            }
        }));

        _requestBlock(blockHash);
    }

    public void requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback downloadMerkleBlockCallback) {
        _storeInMapSet(_downloadMerkleBlockRequests, blockHash, downloadMerkleBlockCallback);

        _requestTimers.add(new FailableRequest(downloadMerkleBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadMerkleBlockCallback.onFailure(blockHash);
            }
        }));

        _requestMerkleBlock(blockHash);
    }

    public void requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestTimers.add(new FailableRequest(downloadThinBlockCallback));
        _requestThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadExtraThinBlockCallback downloadThinBlockCallback) {
        _storeInMapSet(_downloadExtraThinBlockRequests, blockHash, downloadThinBlockCallback);
        _requestTimers.add(new FailableRequest(downloadThinBlockCallback));
        _requestExtraThinBlock(blockHash, knownTransactionsFilter);
    }

    public void requestThinTransactions(final Sha256Hash blockHash, final List<Sha256Hash> transactionHashes, final DownloadThinTransactionsCallback downloadThinBlockCallback) {
        final ImmutableListBuilder<ByteArray> shortTransactionHashesBuilder = new ImmutableListBuilder<ByteArray>(transactionHashes.getCount());
        for (final Sha256Hash transactionHash : transactionHashes) {
            final ByteArray shortTransactionHash = MutableByteArray.wrap(transactionHash.getBytes(0, 8));
            shortTransactionHashesBuilder.add(shortTransactionHash);
        }
        final List<ByteArray> shortTransactionHashes = shortTransactionHashesBuilder.build();

        _storeInMapSet(_downloadThinTransactionsRequests, blockHash, downloadThinBlockCallback);
        _requestTimers.add(new FailableRequest(downloadThinBlockCallback));
        _requestThinTransactions(blockHash, shortTransactionHashes);
    }

    public void requestBlockHeaders(final List<Sha256Hash> blockHashes, final DownloadBlockHeadersCallback downloadBlockHeaderCallback) {
        if (blockHashes.isEmpty()) { return; }

        final Sha256Hash firstBlockHash = blockHashes.get(0);
        _storeInMapSet(_downloadBlockHeadersRequests, firstBlockHash, downloadBlockHeaderCallback);
        _requestTimers.add(new FailableRequest(downloadBlockHeaderCallback));
        _requestBlockHeaders(blockHashes);
    }

    public void requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback downloadTransactionCallback) {
        if (transactionHashes.isEmpty()) { return; }

        for (final Sha256Hash transactionHash : transactionHashes) {
            _storeInMapSet(_downloadTransactionRequests, transactionHash, downloadTransactionCallback);
            _requestTimers.add(new FailableRequest(downloadTransactionCallback, new Runnable() {
                @Override
                public void run() {
                    downloadTransactionCallback.onFailure(transactionHash);
                }
            }));
        }
        _requestTransactions(transactionHashes);
    }

    public void transmitTransactionHashes(final List<Sha256Hash> transactionHashes) {
        final RequestSlpTransactionsCallback requestSlpTransactionsCallback = _requestSlpTransactionsCallback;

        final InventoryMessage inventoryMessage = _protocolMessageFactory.newInventoryMessage();
        for (final Sha256Hash transactionHash : transactionHashes) {
            final InventoryItem inventoryItem;
            if (! _slpTransactionsIsEnabled || requestSlpTransactionsCallback == null) {
                inventoryItem = new InventoryItem(InventoryItemType.TRANSACTION, transactionHash);
            }
            else {
                final Boolean isValid = requestSlpTransactionsCallback.getSlpStatus(transactionHash);
                if (isValid == null) {
                    inventoryItem = new InventoryItem(InventoryItemType.TRANSACTION, transactionHash);
                }
                else if (isValid) {
                    inventoryItem = new InventoryItem(InventoryItemType.VALID_SLP_TRANSACTION, transactionHash);
                }
                else {
                    inventoryItem = new InventoryItem(InventoryItemType.INVALID_SLP_TRANSACTION, transactionHash);
                }
            }
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        _queueMessage(inventoryMessage);
    }

    public void transmitBlockHashes(final List<Sha256Hash> blockHashes) {
        final InventoryMessage inventoryMessage = _protocolMessageFactory.newInventoryMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.BLOCK, blockHash);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        _queueMessage(inventoryMessage);
    }

    public void transmitBlockHeader(final BlockHeader blockHeader, final Integer transactionCount) {
        final BlockHeadersMessage blockHeadersMessage = _protocolMessageFactory.newBlockHeadersMessage();

        final BlockHeaderWithTransactionCount blockHeaderWithTransactionCount = new ImmutableBlockHeaderWithTransactionCount(blockHeader, transactionCount);
        blockHeadersMessage.addBlockHeader(blockHeaderWithTransactionCount);

        _queueMessage(blockHeadersMessage);
    }

    public void transmitBlockHeader(final BlockHeaderWithTransactionCount blockHeader) {
        final BlockHeadersMessage blockHeadersMessage = _protocolMessageFactory.newBlockHeadersMessage();
        blockHeadersMessage.addBlockHeader(blockHeader);
        _queueMessage(blockHeadersMessage);
    }

    public void setBloomFilter(final BloomFilter bloomFilter) {
        final SetTransactionBloomFilterMessage bloomFilterMessage = _protocolMessageFactory.newSetTransactionBloomFilterMessage();
        bloomFilterMessage.setBloomFilter(bloomFilter);
        _queueMessage(bloomFilterMessage);

        if (Logger.isDebugEnabled()) {
            Logger.debug("Setting Bloom Filter for Peer: " + _connection);
            if (Logger.isTraceEnabled()) {
                final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
                Logger.debug(bloomFilterDeflater.toBytes(bloomFilter));
            }
        }
    }

    /**
     * Sets a callback for when the remote node defines a new BloomFilter.
     *  NOTE: This is the remote BloomFilter, not the local filter defined by ::setBloomFilter.
     */
    public void setOnNewBloomFilterCallback(final OnNewBloomFilterCallback onNewBloomFilterCallback) {
        _onNewBloomFilterCallback = onNewBloomFilterCallback;
    }

    public void transmitBlockHeaders(final List<BlockHeader> blockHeaders) {
        final BlockHeadersMessage blockHeadersMessage = _protocolMessageFactory.newBlockHeadersMessage();
        for (final BlockHeader blockHeader : blockHeaders) {
            blockHeadersMessage.addBlockHeader(blockHeader);
        }
        _queueMessage(blockHeadersMessage);
    }

    public void transmitBlock(final Block block) {
        final BlockMessage blockMessage = _protocolMessageFactory.newBlockMessage();
        blockMessage.setBlock(block);
        _queueMessage(blockMessage);
    }

    public void transmitMerkleBlock(final Block block) {
        final MutableBloomFilter bloomFilter = _bloomFilter;
        if (bloomFilter == null) {
            // NOTE: When a MerkleBlock is requested without a BloomFilter set, Bitcoin XT sends a MerkleBlock w/ BloomFilter.MATCH_ALL.
            Logger.warn("Attempting to Transmit MerkleBlock when no BloomFilter is available.");
            final BlockMessage blockMessage = _protocolMessageFactory.newBlockMessage();
            blockMessage.setBlock(block);
            _queueMessage(blockMessage);
        }
        else {
            // The response to a MerkleBlock request is a combination of messages.
            //  1. The first message should be the MerkleBlock itself.
            //  2. Immediately following should be the any transactions that match the Node's bloomFilter.
            //  3. Finally, since the receiving node has no way to determine if the transaction stream is complete, a ping message is sent to interrupt the flow.
            final MutableList<ProtocolMessage> messages = new MutableList<ProtocolMessage>();

            final MerkleBlockMessage merkleBlockMessage = _protocolMessageFactory.newMerkleBlockMessage();
            merkleBlockMessage.setBlockHeader(block);
            merkleBlockMessage.setPartialMerkleTree(block.getPartialMerkleTree(bloomFilter));
            messages.add(merkleBlockMessage);

            // BIP37 dictates that matched transactions be separately relayed...
            //  "In addition, because a merkleblock message contains only a list of transaction hashes, transactions
            //      matching the filter should also be sent in separate tx messages after the merkleblock is sent. This
            //      avoids a slow roundtrip that would otherwise be required (receive hashes, didn't see some of these
            //      transactions yet, ask for them)."
            final UpdateBloomFilterMode updateBloomFilterMode = Util.coalesce(UpdateBloomFilterMode.valueOf(bloomFilter.getUpdateMode()), UpdateBloomFilterMode.READ_ONLY);
            final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode, _addressInflater);
            final List<Transaction> transactions = block.getTransactions();
            for (final Transaction transaction : transactions) {
                final boolean transactionMatches = transactionBloomFilterMatcher.shouldInclude(transaction);
                if (transactionMatches) {
                    final TransactionMessage transactionMessage = _protocolMessageFactory.newTransactionMessage();
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

    public void setRequestSlpTransactionsCallback(final RequestSlpTransactionsCallback requestSlpTransactionsCallback) {
        _requestSlpTransactionsCallback = requestSlpTransactionsCallback;
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

    public Boolean isNewBlocksViaHeadersEnabled() {
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

    /**
     * If the remote peer supports SLP validity checking, send an enable SLP transactions message.
     */
    public void enableSlpValidityChecking(final Boolean shouldEnableSlpValidityChecking) {
        _enableSlpValidityChecking(shouldEnableSlpValidityChecking);
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
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(bloomFilter, updateBloomFilterMode, _addressInflater);
        return transactionBloomFilterMatcher.shouldInclude(transaction);
    }

    /**
     * Returns true if the Transaction matches the BitcoinNode's BloomFilter, or if a BloomFilter has not been set.
     */
    public Boolean matchesFilter(final Transaction transaction, final UpdateBloomFilterMode updateBloomFilterMode) {
        final TransactionBloomFilterMatcher transactionBloomFilterMatcher = new TransactionBloomFilterMatcher(_bloomFilter, updateBloomFilterMode, _addressInflater);
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
        final InventoryMessage inventoryMessage = _protocolMessageFactory.newInventoryMessage();
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.BLOCK, headBlockHash);
        inventoryMessage.addInventoryItem(inventoryItem);
        _queueMessage(inventoryMessage);
    }

    public void enableNewBlockViaHeaders() {
        final NewBlocksViaHeadersMessage newBlocksViaHeadersMessage = new NewBlocksViaHeadersMessage();
        _queueMessage(newBlocksViaHeadersMessage);
    }

    public void getAddressBlocks(final List<Address> addresses) {
        _requestAddressBlocks(addresses);
    }

    public void getAddressBlocks(final List<Address> addresses, final BlockInventoryMessageCallback addressBlocksCallback) {
        if (addressBlocksCallback != null) {
            synchronized (_downloadAddressBlocksRequests) {
                _downloadAddressBlocksRequests.add(addressBlocksCallback);
            }
        }

        _requestAddressBlocks(addresses);
    }

    public void getSlpStatus(final List<Sha256Hash> transactionHashes) {
        _requestSlpStatus(transactionHashes);
    }

    @Override
    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        if (_localNodeIpAddress == null) { return null; }
        if (! (_localNodeIpAddress instanceof BitcoinNodeIpAddress)) { return null; }
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

    public void removeCallback(final BitcoinNodeCallback callback) {
        _removeCallback(callback);
    }

    public void clearRequests() {
        synchronized (_downloadBlockRequests) { _downloadBlockRequests.clear(); }
        synchronized (_downloadMerkleBlockRequests) { _downloadMerkleBlockRequests.clear(); }
        synchronized (_downloadBlockHeadersRequests) { _downloadBlockHeadersRequests.clear(); }
        synchronized (_downloadTransactionRequests) { _downloadTransactionRequests.clear(); }
        synchronized (_downloadThinBlockRequests) { _downloadThinBlockRequests.clear(); }
        synchronized (_downloadExtraThinBlockRequests) { _downloadExtraThinBlockRequests.clear(); }
        synchronized (_downloadThinTransactionsRequests) { _downloadThinTransactionsRequests.clear(); }
        synchronized (_downloadAddressBlocksRequests) { _downloadAddressBlocksRequests.clear(); }
    }

    /**
     * Returns the blockHeight defined during the Node's handshake or null if the handshake has not completed.
     */
    public Long getBlockHeight() {
        return _blockHeight;
    }
}

package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.address.TypedAddress;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.MerkleBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.bloomfilter.UpdateBloomFilterMode;
import com.softwareverde.bitcoin.server.State;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.BitcoinBinaryPacketFormat;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageFactory;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.clear.ClearTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.set.SetTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.bloomfilter.update.UpdateTransactionBloomFilterMessage;
import com.softwareverde.bitcoin.server.message.type.compact.EnableCompactBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.dsproof.DoubleSpendProofMessage;
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
import com.softwareverde.bitcoin.server.message.type.query.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.header.RequestBlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.mempool.QueryUnconfirmedTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.InventoryMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.header.BlockHeadersMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.block.merkle.MerkleBlockMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.error.NotFoundResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.server.message.type.query.response.transaction.TransactionMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.utxo.UtxoCommitmentMessage;
import com.softwareverde.bitcoin.server.message.type.query.utxo.NodeSpecificUtxoCommitmentBreakdown;
import com.softwareverde.bitcoin.server.message.type.query.utxo.QueryUtxoCommitmentsMessage;
import com.softwareverde.bitcoin.server.message.type.query.utxo.UtxoCommitmentsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.block.ThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.block.RequestExtraThinBlockMessage;
import com.softwareverde.bitcoin.server.message.type.thin.request.transaction.RequestExtraThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.thin.transaction.ThinTransactionsMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.BitcoinAcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledPublicKeyRequest;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledSha256HashRequest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionBloomFilterMatcher;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProof;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.mutable.ConcurrentMutableHashMap;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.constable.set.mutable.MutableSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
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
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BitcoinNode extends Node {
    public static final Long MIN_BYTES_PER_SECOND = (ByteUtil.Unit.Binary.MEBIBYTES / 8L); // 1mbps, slower than 3G.
    public static final Long REQUEST_TIME_BUFFER = 1000L; // Max time, in ms, assumed it takes to respond to a request, ignoring ping.

    protected static final AddressInflater DEFAULT_ADDRESS_INFLATER = new AddressInflater();

    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(0L);

    public interface BitcoinNodeCallback { }
    public interface BitcoinNodeHandler { }

    public interface BitcoinNodeRequestCallback<T> extends BitcoinNodeCallback {
        void onResult(RequestId requestId, BitcoinNode bitcoinNode, T response);
    }

    public interface FailableBitcoinNodeRequestCallback<T, S> extends BitcoinNodeRequestCallback<T> {
        default void onFailure(RequestId requestId, BitcoinNode bitcoinNode, S response) { }
    }

    public interface DownloadBlockCallback extends FailableBitcoinNodeRequestCallback<Block, Sha256Hash> { }

    public interface DownloadUtxoCommitmentCallback extends FailableBitcoinNodeRequestCallback<ByteArray, PublicKey> { }

    public interface DownloadMerkleBlockCallback extends FailableBitcoinNodeRequestCallback<MerkleBlockParameters, Sha256Hash> { }

    public interface DownloadBlockHeadersCallback extends FailableBitcoinNodeRequestCallback<List<BlockHeader>, Sha256Hash> { }

    public interface DownloadTransactionCallback extends FailableBitcoinNodeRequestCallback<Transaction, Sha256Hash> { }

    public interface DownloadThinBlockCallback extends FailableBitcoinNodeRequestCallback<ThinBlockParameters, Sha256Hash> { }

    public interface DownloadExtraThinBlockCallback extends FailableBitcoinNodeRequestCallback<ExtraThinBlockParameters, Sha256Hash> { }

    public interface DownloadThinTransactionsCallback extends FailableBitcoinNodeRequestCallback<List<Transaction>, Sha256Hash> { }

    public interface DownloadDoubleSpendProofCallback extends FailableBitcoinNodeRequestCallback<DoubleSpendProof, Sha256Hash> { }

    public interface RequestPeersHandler extends BitcoinNodeCallback {
        List<BitcoinNodeIpAddress> getConnectedPeers();
    }

    public interface UtxoCommitmentsCallback extends BitcoinNodeRequestCallback<List<NodeSpecificUtxoCommitmentBreakdown>> { }

    public interface BlockInventoryAnnouncementHandler extends BitcoinNodeHandler {
        void onNewInventory(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes);
        void onNewHeaders(BitcoinNode bitcoinNode, List<BlockHeader> blockHeaders);
    }

    public interface SpvBlockInventoryAnnouncementHandler extends BitcoinNodeHandler {
        void onResult(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes);
    }

    public interface TransactionInventoryAnnouncementHandler extends BitcoinNodeHandler {
        void onResult(BitcoinNode bitcoinNode, List<Sha256Hash> transactionHashes);

        default void onResult(BitcoinNode bitcoinNode, List<Sha256Hash> transactionHashes, Boolean isSlpValid) {
            this.onResult(bitcoinNode, transactionHashes);
        }
    }

    public interface DoubleSpendProofAnnouncementHandler extends BitcoinNodeHandler {
        void onResult(BitcoinNode bitcoinNode, List<Sha256Hash> doubleSpendProofsIdentifiers);
    }

    public interface RequestBlockHashesHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash);
    }

    public interface RequestBlockHeadersHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, List<Sha256Hash> blockHashes, Sha256Hash desiredBlockHash);
    }

    public interface RequestUnconfirmedTransactionsHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode);
    }

    public interface RequestDataHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, List<InventoryItem> dataHashes);
    }

    public interface RequestSpvBlocksHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, List<? extends TypedAddress> addresses);
    }

    public interface RequestSlpTransactionsHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, List<Sha256Hash> transactionHashes);
        Boolean getSlpStatus(Sha256Hash transactionHash);
    }

    public interface QueryUtxoCommitmentsHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode);
    }

    public interface RequestExtraThinBlockHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, Sha256Hash blockHash, BloomFilter bloomFilter);
    }

    public interface RequestExtraThinTransactionHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode, Sha256Hash blockHash, List<ByteArray> transactionShortHashes);
    }

    public interface NewBloomFilterHandler extends BitcoinNodeHandler {
        void run(BitcoinNode bitcoinNode);
    }

    protected interface CallbackExecutor<S extends BitcoinNodeCallback> {
        void onResult(PendingRequest<S> pendingRequest);
    }

    public static final SynchronizationStatus DEFAULT_STATUS_CALLBACK = new SynchronizationStatus() {
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

    protected static class PendingRequest<T extends BitcoinNodeCallback> {
        public final RequestId requestId;
        public final RequestPriority requestPriority;
        public final T callback;

        public PendingRequest(final RequestId requestId, final T callback, final RequestPriority requestPriority) {
            this.requestId = requestId;
            this.requestPriority = requestPriority;
            this.callback = callback;
        }

        @Override
        public int hashCode() {
            return this.requestId.hashCode();
        }

        @Override
        public boolean equals(final Object object) {
            if (! (object instanceof PendingRequest)) { return false; }
            return Util.areEqual(this.requestId, ((PendingRequest) object).requestId);
        }
    }

    protected static class CallbackThread extends Thread {
        public CallbackThread(final Runnable runnable) {
            super(runnable);
            this.setName("BitcoinNode Callback");
            this.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.debug(exception);
                }
            });
        }
    }

    protected abstract static class AsyncCallbackExecutor<S extends BitcoinNodeCallback> implements CallbackExecutor<S> {
        @Override
        public void onResult(final PendingRequest<S> pendingRequest) {
            // _callbackWorker.submitTask(new WorkerManager.Task() {
            //     @Override
            //     public void run() {
            //         AsyncCallbackExecutor.this.onResult0(pendingRequest);
            //     }
            // });
            (new CallbackThread(new Runnable() {
                @Override
                public void run() {
                    AsyncCallbackExecutor.this.onResult0(pendingRequest);
                }
            })).start();
        }

        public abstract void onResult0(PendingRequest<S> pendingRequest);
    }

    protected RequestId _newRequestId() {
        return RequestId.wrap(NEXT_REQUEST_ID.incrementAndGet());
    }

    protected final Runnable _requestMonitor;
    protected Thread _requestMonitorThread;

    protected final ConcurrentLinkedQueue<BitcoinNodeObserver> _observers = new ConcurrentLinkedQueue<>();

    protected final AddressInflater _addressInflater;
    protected final MessageRouter _messageRouter = new MessageRouter();

    // protected final WorkerManager _callbackWorker;

    // Requests Maps
    protected final ConcurrentMutableHashMap<RequestId, FailableRequest> _failableRequests = new ConcurrentMutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadBlockCallback>>> _downloadBlockRequests = new MutableHashMap<>();
    protected final MutableMap<PublicKey, MutableSet<PendingRequest<DownloadUtxoCommitmentCallback>>> _downloadUtxoCommitmentRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadMerkleBlockCallback>>> _downloadMerkleBlockRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadBlockHeadersCallback>>> _downloadBlockHeadersRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadTransactionCallback>>> _downloadTransactionRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadThinBlockCallback>>> _downloadThinBlockRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadExtraThinBlockCallback>>> _downloadExtraThinBlockRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadThinTransactionsCallback>>> _downloadThinTransactionsRequests = new MutableHashMap<>();
    protected final MutableMap<Sha256Hash, MutableSet<PendingRequest<DownloadDoubleSpendProofCallback>>> _downloadDoubleSpendProofRequests = new MutableHashMap<>();
    protected final MutableMap<RequestId, BlockInventoryAnnouncementHandler> _downloadAddressBlocksRequests = new MutableHashMap<>();
    protected final MutableMap<RequestId, UtxoCommitmentsCallback> _utxoCommitmentsCallbacks = new MutableHashMap<>();

    protected final BitcoinProtocolMessageFactory _protocolMessageFactory;
    protected final LocalNodeFeatures _localNodeFeatures;

    protected SynchronizationStatus _synchronizationStatus = DEFAULT_STATUS_CALLBACK;

    protected RequestBlockHashesHandler _queryBlocksCallback;
    protected RequestBlockHeadersHandler _queryBlockHeadersCallback;
    protected RequestDataHandler _requestDataHandler;
    protected BlockInventoryAnnouncementHandler _blockInventoryMessageHandler;
    protected RequestPeersHandler _requestPeersHandler;
    protected RequestUnconfirmedTransactionsHandler _queryUnconfirmedTransactionsCallback;
    protected RequestSpvBlocksHandler _requestSpvBlocksHandler;
    protected QueryUtxoCommitmentsHandler _queryUtxoCommitmentsHandler;

    protected RequestExtraThinBlockHandler _requestExtraThinBlockCallback;
    protected RequestExtraThinTransactionHandler _requestExtraThinTransactionCallback;

    protected BitcoinSynchronizeVersionMessage _synchronizeVersionMessage;

    protected TransactionInventoryAnnouncementHandler _transactionsAnnouncementCallback;
    protected SpvBlockInventoryAnnouncementHandler _spvBlockInventoryAnnouncementCallback;
    protected DoubleSpendProofAnnouncementHandler _doubleSpendProofAnnouncementCallback;

    protected DownloadBlockCallback _unsolicitedBlockReceivedCallback;

    protected Boolean _peersWereRequested = false;

    protected Boolean _announceNewBlocksViaHeadersIsEnabled = false;
    protected Integer _compactBlocksVersion;

    protected NewBloomFilterHandler _onNewBloomFilterCallback;
    protected Boolean _transactionRelayIsEnabled = true;

    protected MutableBloomFilter _bloomFilter;
    protected Sha256Hash _batchContinueHash; // https://en.bitcoin.it/wiki/Satoshi_Client_Block_Exchange#Batch_Continue_Mechanism

    protected MerkleBlockParameters _currentMerkleBlockBeingTransmitted; // Represents the currently MerkleBlock being transmitted from the node. Becomes unset after a non-transaction message is received.

    protected Long _blockHeight;

    protected AtomicBoolean _isConnected = new AtomicBoolean(false);

    protected void _removeCallback(final RequestId requestId) {
        BitcoinNodeUtil.removeValueFromMapSet(_downloadBlockRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadUtxoCommitmentRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadMerkleBlockRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadBlockHeadersRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadTransactionRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadThinBlockRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadExtraThinBlockRequests, requestId);
        BitcoinNodeUtil.removeValueFromMapSet(_downloadThinTransactionsRequests, requestId);
        _failableRequests.remove(requestId);

        synchronized (_utxoCommitmentsCallbacks) {
            _utxoCommitmentsCallbacks.remove(requestId);
        }

        synchronized (_downloadAddressBlocksRequests) {
            _downloadAddressBlocksRequests.remove(requestId);
        }
    }

    protected void _observeOnDataSent(final BitcoinProtocolMessage bitcoinProtocolMessage) {
        final MessageType messageType = bitcoinProtocolMessage.getCommand();
        final Integer byteCount = bitcoinProtocolMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataSent(BitcoinNode.this, messageType, byteCount);
        }
    }

    /**
     * Returns a list of active (and unfilled) UnfulfilledRequest from the provided request map.
     */
    protected <CallbackType extends BitcoinNodeCallback> List<UnfulfilledSha256HashRequest> _getPendingSha256HashRequests(final MutableMap<Sha256Hash, MutableSet<PendingRequest<CallbackType>>> requestMap) {
        final MutableList<UnfulfilledSha256HashRequest> unfulfilledRequests;

        synchronized (requestMap) {
            unfulfilledRequests = new MutableArrayList<>(requestMap.getCount());
            for (final Tuple<Sha256Hash, MutableSet<PendingRequest<CallbackType>>> entry : requestMap) {
                final Sha256Hash itemHash = entry.first;
                for (final PendingRequest<?> pendingRequest : entry.second) {
                    unfulfilledRequests.add(new UnfulfilledSha256HashRequest(BitcoinNode.this, pendingRequest.requestId, pendingRequest.requestPriority, itemHash));
                }
            }
        }

        return unfulfilledRequests;
    }

    /**
     * Returns a list of active (and unfilled) UnfulfilledRequest from the provided request map.
     */
    protected <CallbackType extends BitcoinNodeCallback> List<UnfulfilledPublicKeyRequest> _getPendingPublicKeyRequests(final MutableMap<PublicKey, MutableSet<PendingRequest<CallbackType>>> requestMap) {
        final MutableList<UnfulfilledPublicKeyRequest> unfulfilledRequests;

        synchronized (requestMap) {
            unfulfilledRequests = new MutableArrayList<>(requestMap.getCount());
            for (final Tuple<PublicKey, MutableSet<PendingRequest<CallbackType>>> entry : requestMap) {
                final PublicKey publicKey = entry.first;
                for (final PendingRequest<?> pendingRequest : entry.second) {
                    unfulfilledRequests.add(new UnfulfilledPublicKeyRequest(BitcoinNode.this, pendingRequest.requestId, pendingRequest.requestPriority, publicKey));
                }
            }
        }

        return unfulfilledRequests;
    }

    @Override
    protected void _queueMessage(final ProtocolMessage message) {
        super._queueMessage(message);

        if (message instanceof BitcoinProtocolMessage) {
            Logger.trace("Sending: " + ((BitcoinProtocolMessage) message).getCommand() + " to " + BitcoinNode.this.getConnectionString());

            final BitcoinProtocolMessage bitcoinProtocolMessage = (BitcoinProtocolMessage) message;
            _observeOnDataSent(bitcoinProtocolMessage);
        }
    }

    @Override
    protected void _queueMessages(final List<? extends ProtocolMessage> messages) {
        super._queueMessages(messages);

        for (final ProtocolMessage message : messages) {
            if (message instanceof BitcoinProtocolMessage) {
                Logger.trace("Sending: " + ((BitcoinProtocolMessage) message).getCommand() + " to " + BitcoinNode.this.getConnectionString());

                final BitcoinProtocolMessage bitcoinProtocolMessage = (BitcoinProtocolMessage) message;
                _observeOnDataSent(bitcoinProtocolMessage);
            }
        }
    }

    protected Long _getMaximumTimeoutMs(final BitcoinNodeCallback callback) {
        if ( (callback instanceof DownloadBlockCallback) || (callback instanceof DownloadUtxoCommitmentCallback) ) {
            final float buffer = 2.0F;
            return (long) ((BitcoinConstants.getBlockMaxByteCount() / BitcoinNode.MIN_BYTES_PER_SECOND) * buffer * 1000L);
        }

        return (30L * 1000L); // 30 seconds...
    }

    @Override
    protected void _onHandshakeComplete() {
        super._onHandshakeComplete();

        for (final BitcoinNodeObserver observer : _observers) {
            observer.onHandshakeComplete(BitcoinNode.this);
        }
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
        final boolean wasConnected = _isConnected.compareAndSet(true, false);
        if (! wasConnected) { return; }

        synchronized (_requestMonitor) {
            final Thread requestMonitorThread = _requestMonitorThread;
            if (requestMonitorThread != null) {
                requestMonitorThread.interrupt();
                _requestMonitorThread = null;
            }
        }

        { // Unset all callback and handlers...
            _queryBlocksCallback = null;
            _queryBlockHeadersCallback = null;
            _requestDataHandler = null;
            _requestSpvBlocksHandler = null;
            _queryUtxoCommitmentsHandler = null;
            _blockInventoryMessageHandler = null;
            _requestExtraThinBlockCallback = null;
            _requestExtraThinTransactionCallback = null;
            _transactionsAnnouncementCallback = null;
            _spvBlockInventoryAnnouncementCallback = null;
            _doubleSpendProofAnnouncementCallback = null;
        }

        super._disconnect();

        BitcoinNodeUtil.failPendingRequests(_downloadBlockRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadUtxoCommitmentRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadMerkleBlockRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadTransactionRequests, _failableRequests, this);

        BitcoinNodeUtil.failPendingRequests(_downloadBlockHeadersRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadThinBlockRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadExtraThinBlockRequests, _failableRequests, this);
        BitcoinNodeUtil.failPendingRequests(_downloadThinTransactionsRequests, _failableRequests, this);

        _failableRequests.clear();

        // try {
        //     _callbackWorker.close(3000L);
        // }
        // catch (final Exception exception) {
        //     Logger.debug(exception);
        // }
    }

    @Override
    protected void _onConnect() {
        final boolean wasDisconnected = _isConnected.compareAndSet(false, true);
        if (! wasDisconnected) { return; }

        // _callbackWorker.start(); // Reinitialize the callbackWorker if reconnecting (does nothing upon first connect).

        synchronized (_requestMonitor) {
            final Thread existingRequestMonitorThread = _requestMonitorThread;
            if (existingRequestMonitorThread != null) {
                existingRequestMonitorThread.interrupt();
            }

            final Thread requestMonitorThread = new Thread(_requestMonitor);
            requestMonitorThread.setName("Bitcoin Node - Request Monitor - " + _connection.toString());
            requestMonitorThread.setDaemon(true); // Ensure the thread is closed when the process dies (unnecessary, but proper).
            requestMonitorThread.start();
            _requestMonitorThread = requestMonitorThread;
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

        _failableRequests.mutableVisit(new MutableMap.MutableVisitor<>() {
            @Override
            public boolean run(final Tuple<RequestId, FailableRequest> mapEntry) {
                final RequestId requestId = mapEntry.first;
                final FailableRequest failableRequest = mapEntry.second;

                final Long maxRequestAgeMs = _getMaximumTimeoutMs(failableRequest.callback);
                final long requestAgeMs = (nowMs - failableRequest.requestStartTimeMs);

                if (requestAgeMs > maxRequestAgeMs) {
                    mapEntry.first = null; // Remove item...

                    _removeCallback(requestId);
                    failableRequest.onFailure.run();
                }
                else {
                    final Long ping = Util.coalesce(_calculateAveragePingMs(), 1000L);
                    if (requestAgeMs >= ((ping * 2L) + REQUEST_TIME_BUFFER)) {
                        final Long startingByteCountReceived = failableRequest.startingByteCountReceived;
                        final Long newByteCountReceived = _connection.getTotalBytesReceivedCount();
                        final long bytesReceivedSinceRequested = (newByteCountReceived - startingByteCountReceived);
                        final long bytesPerMs = (bytesReceivedSinceRequested / requestAgeMs);
                        final double bytesPerSecond = (bytesPerMs * 1000L);
                        final double megabytesPerSecond = (bytesPerSecond / ByteUtil.Unit.Binary.MEBIBYTES);

                        if (Logger.isTraceEnabled()) {
                            Logger.trace("Download progress: bytesReceivedSinceRequested=" + bytesReceivedSinceRequested + ", requestAgeMs=" + requestAgeMs + ", bytesPerMs=" + bytesPerMs + ", megabytesPerSecond=" + megabytesPerSecond + ", minMbps=" + (BitcoinNode.MIN_BYTES_PER_SECOND / ByteUtil.Unit.Binary.MEBIBYTES.doubleValue()) + " - " + BitcoinNode.this.getConnectionString() + " - " + failableRequest.requestDescription);
                        }

                        if (bytesPerSecond < BitcoinNode.MIN_BYTES_PER_SECOND) {
                            Logger.info("Detected stalled download from " + BitcoinNode.this.getConnectionString() + " (" + megabytesPerSecond + " MB/s) - " + failableRequest.requestDescription);

                            mapEntry.first = null; // Remove item...

                            _removeCallback(requestId);
                            failableRequest.onFailure.run();
                        }
                    }
                }

                return true;
            }
        });
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
                    synchronized (_requestMonitor) {
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

                Logger.trace("Received: " + message.getCommand() + " from " + BitcoinNode.this);

                _lastMessageReceivedTimestamp = _systemTime.getCurrentTimeInMilliSeconds();

                // If a MerkleBlock was requested, trigger the MerkleBlock completion when a non-Transaction message is received.
                if (message.getCommand() != MessageType.TRANSACTION) {
                    final MerkleBlockParameters merkleBlockParameters = _currentMerkleBlockBeingTransmitted;
                    _currentMerkleBlockBeingTransmitted = null;

                    if (merkleBlockParameters != null) {
                        final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
                        final Sha256Hash blockHash = merkleBlock.getHash();
                        BitcoinNodeUtil.executeAndClearCallbacks(_downloadMerkleBlockRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
                            @Override
                            public void onResult0(final PendingRequest<DownloadMerkleBlockCallback> pendingRequest) {
                                final DownloadMerkleBlockCallback callback = pendingRequest.callback;
                                callback.onResult(pendingRequest.requestId, BitcoinNode.this, merkleBlockParameters);
                            }
                        });
                    }
                }

                _messageRouter.route(message.getCommand(), message, BitcoinNode.this);
            }
        });

        _connection.setOnConnectFailureCallback(new Runnable() {
            @Override
            public void run() {
                _disconnect();

                final NodeConnectedCallback nodeConnectedCallback = _nodeConnectedCallback;
                if (nodeConnectedCallback != null) {
                    nodeConnectedCallback.onFailure();
                }
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

    @Override
    protected Long _onPongReceived(final PongMessage pongMessage) {
        final Long msElapsed = super._onPongReceived(pongMessage);

        for (final BitcoinNodeObserver observer : _observers) {
            observer.onPongReceived(BitcoinNode.this, msElapsed);
        }

        return msElapsed;
    }

    protected void _defineRoutes() {
        _messageRouter.addRoute(MessageType.PING,                           (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onPingReceived((BitcoinPingMessage) message); });
        _messageRouter.addRoute(MessageType.PONG,                           (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onPongReceived((BitcoinPongMessage) message); });
        _messageRouter.addRoute(MessageType.SYNCHRONIZE_VERSION,            (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onSynchronizeVersion((SynchronizeVersionMessage) message); });
        _messageRouter.addRoute(MessageType.ACKNOWLEDGE_VERSION,            (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onAcknowledgeVersionMessageReceived((BitcoinAcknowledgeVersionMessage) message); });
        _messageRouter.addRoute(MessageType.NODE_ADDRESSES,                 (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onNodeAddressesReceived((BitcoinNodeIpAddressMessage) message); });
        _messageRouter.addRoute(MessageType.ERROR,                          (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onErrorMessageReceived((ErrorMessage) message); });
        _messageRouter.addRoute(MessageType.INVENTORY,                      (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onInventoryMessageReceived((InventoryMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_DATA,                   (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onRequestDataMessageReceived((RequestDataMessage) message); });
        _messageRouter.addRoute(MessageType.BLOCK,                          (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onBlockMessageReceived((BlockMessage) message); });
        _messageRouter.addRoute(MessageType.TRANSACTION,                    (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onTransactionMessageReceived((TransactionMessage) message); });
        _messageRouter.addRoute(MessageType.DOUBLE_SPEND_PROOF,             (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onDoubleSpendProofMessageReceived((DoubleSpendProofMessage) message); });
        _messageRouter.addRoute(MessageType.MERKLE_BLOCK,                   (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onMerkleBlockReceived((MerkleBlockMessage) message); });
        _messageRouter.addRoute(MessageType.BLOCK_HEADERS,                  (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onBlockHeadersMessageReceived((BlockHeadersMessage) message); });
        _messageRouter.addRoute(MessageType.QUERY_BLOCKS,                   (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onQueryBlocksMessageReceived((QueryBlocksMessage) message); });
        _messageRouter.addRoute(MessageType.QUERY_UNCONFIRMED_TRANSACTIONS, (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onQueryUnconfirmedTransactionsReceived((QueryUnconfirmedTransactionsMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_BLOCK_HEADERS,          (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onQueryBlockHeadersMessageReceived((RequestBlockHeadersMessage) message); });
        _messageRouter.addRoute(MessageType.ENABLE_NEW_BLOCKS_VIA_HEADERS,  (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _announceNewBlocksViaHeadersIsEnabled = true; });
        _messageRouter.addRoute(MessageType.ENABLE_COMPACT_BLOCKS,          (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> {
            final EnableCompactBlocksMessage enableCompactBlocksMessage = (EnableCompactBlocksMessage) message;
            _compactBlocksVersion = (enableCompactBlocksMessage.isEnabled() ? enableCompactBlocksMessage.getVersion() : null);
        });
        _messageRouter.addRoute(MessageType.REQUEST_EXTRA_THIN_BLOCK,       (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onRequestExtraThinBlockMessageReceived((RequestExtraThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.EXTRA_THIN_BLOCK,               (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onExtraThinBlockMessageReceived((ExtraThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.THIN_BLOCK,                     (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onThinBlockMessageReceived((ThinBlockMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_EXTRA_THIN_TRANSACTIONS,(final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onRequestExtraThinTransactionsMessageReceived((RequestExtraThinTransactionsMessage) message); });
        _messageRouter.addRoute(MessageType.THIN_TRANSACTIONS,              (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onThinTransactionsMessageReceived((ThinTransactionsMessage) message); });
        _messageRouter.addRoute(MessageType.NOT_FOUND,                      (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onNotFoundMessageReceived((NotFoundResponseMessage) message); });
        _messageRouter.addRoute(MessageType.FEE_FILTER,                     (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onFeeFilterMessageReceived((FeeFilterMessage) message); });
        _messageRouter.addRoute(MessageType.REQUEST_PEERS,                  (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onRequestPeersMessageReceived((RequestPeersMessage) message); });
        _messageRouter.addRoute(MessageType.SET_TRANSACTION_BLOOM_FILTER,   (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onSetTransactionBloomFilterMessageReceived((SetTransactionBloomFilterMessage) message); });
        _messageRouter.addRoute(MessageType.UPDATE_TRANSACTION_BLOOM_FILTER,(final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onUpdateTransactionBloomFilterMessageReceived((UpdateTransactionBloomFilterMessage) message); });
        _messageRouter.addRoute(MessageType.CLEAR_TRANSACTION_BLOOM_FILTER, (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onClearTransactionBloomFilterMessageReceived((ClearTransactionBloomFilterMessage) message); });

        _messageRouter.addRoute(MessageType.QUERY_UTXO_COMMITMENTS,         (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onQueryUtxoCommitmentsMessageReceived((QueryUtxoCommitmentsMessage) message); });
        _messageRouter.addRoute(MessageType.UTXO_COMMITMENTS,               (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onUtxoCommitmentsReceived((UtxoCommitmentsMessage) message); });
        _messageRouter.addRoute(MessageType.UTXO_COMMITMENT,                (final ProtocolMessage message, final BitcoinNode bitcoinNode) -> { _onUtxoCommitmentMessageReceived((UtxoCommitmentMessage) message); });

        _messageRouter.setUnknownRouteHandler(new MessageRouter.UnknownRouteHandler() {
            @Override
            public void run(final MessageType messageType, final ProtocolMessage message, final BitcoinNode bitcoinNode) {
                final BitcoinProtocolMessage bitcoinProtocolMessage = (BitcoinProtocolMessage) message;
                Logger.warn("Unhandled Message Command: " + messageType + ": 0x" + HexUtil.toHexString(bitcoinProtocolMessage.getHeaderBytes()) + " from " + bitcoinNode);
            }
        });
    }

    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        Logger.info("RECEIVED ERROR:" + rejectCode.getRejectMessageType().getValue() + " " + HexUtil.toHexString(new byte[] { rejectCode.getCode() }) + " " + errorMessage.getRejectDescription() + " " + HexUtil.toHexString(errorMessage.getExtraData()) + " " + this.getUserAgent() + " " + this.getConnectionString());
    }

    protected void _onRequestDataMessageReceived(final RequestDataMessage requestDataMessage) {
        final RequestDataHandler requestDataHandler = _requestDataHandler;

        if (requestDataHandler != null) {
            final List<InventoryItem> dataHashes = new ImmutableList<>(requestDataMessage.getInventoryItems());
            requestDataHandler.run(BitcoinNode.this, dataHashes);
        }
        else {
            Logger.debug("No handler set for RequestData message.");
        }

        final MessageType messageType = requestDataMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    @Override
    protected void _onNodeAddressesReceived(final NodeIpAddressMessage nodeIpAddressMessage) {
        if (! _peersWereRequested) {
            Logger.debug(this + " sent unsolicited peers.");
            return;
        }

        super._onNodeAddressesReceived(nodeIpAddressMessage);
    }

    protected void _onInventoryMessageReceived(final InventoryMessage inventoryMessage) {
        final MutableMap<InventoryItemType, MutableList<Sha256Hash>> dataHashesMap = new MutableHashMap<>();

        final List<InventoryItem> dataHashes = inventoryMessage.getInventoryItems();
        for (final InventoryItem inventoryItem : dataHashes) {
            final InventoryItemType inventoryItemType = inventoryItem.getItemType();
            BitcoinNodeUtil.storeInMapList(dataHashesMap, inventoryItemType, inventoryItem.getItemHash());
        }

        for (final InventoryItemType inventoryItemType : dataHashesMap.getKeys()) {
            final List<Sha256Hash> objectHashes = dataHashesMap.get(inventoryItemType);

            if (objectHashes.isEmpty()) { continue; }

            switch (inventoryItemType) {
                case BLOCK: {
                    final BlockInventoryAnnouncementHandler blockInventoryMessageHandler = _blockInventoryMessageHandler;
                    if (blockInventoryMessageHandler != null) {
                        blockInventoryMessageHandler.onNewInventory(BitcoinNode.this, objectHashes);
                    }
                    else {
                        Logger.debug("No handler set for BlockInventoryMessageHandler.");
                    }
                } break;

                case TRANSACTION: {
                    final TransactionInventoryAnnouncementHandler transactionsAnnouncementCallback = _transactionsAnnouncementCallback;
                    if (transactionsAnnouncementCallback != null) {
                        transactionsAnnouncementCallback.onResult(BitcoinNode.this, objectHashes);
                    }
                    else {
                        Logger.debug("No handler set for TransactionInventoryAnnouncementHandler.");
                    }
                } break;

                case MERKLE_BLOCK: {
                    if (Logger.isDebugEnabled()) {
                        for (final Sha256Hash objectHash : objectHashes) {
                            Logger.debug("Received AddressBlock: " + objectHash + " from " + _connection);
                        }
                    }

                    final MutableMap<RequestId, BlockInventoryAnnouncementHandler> addressBlocksCallbacks;
                    synchronized (_downloadAddressBlocksRequests) {
                        addressBlocksCallbacks = new MutableHashMap<>(_downloadAddressBlocksRequests);
                        _downloadAddressBlocksRequests.clear();
                    }

                    final SpvBlockInventoryAnnouncementHandler spvBlockInventoryAnnouncementHandler = _spvBlockInventoryAnnouncementCallback;
                    if (spvBlockInventoryAnnouncementHandler != null) {
                        for (final Tuple<RequestId, BlockInventoryAnnouncementHandler> callbackEntry : addressBlocksCallbacks) {
                            final BlockInventoryAnnouncementHandler blockInventoryMessageCallback = callbackEntry.second;
                            blockInventoryMessageCallback.onNewInventory(BitcoinNode.this, objectHashes);
                        }

                        spvBlockInventoryAnnouncementHandler.onResult(BitcoinNode.this, objectHashes);
                    }
                    else {
                        Logger.debug("No handler set for SpvBlockInventoryAnnouncementHandler.");
                    }
                } break;

                case DOUBLE_SPEND_PROOF: {
                    final DoubleSpendProofAnnouncementHandler doubleSpendProofAnnouncementCallback = _doubleSpendProofAnnouncementCallback;
                    if (doubleSpendProofAnnouncementCallback != null) {
                        doubleSpendProofAnnouncementCallback.onResult(BitcoinNode.this, objectHashes);
                    }
                    else {
                        Logger.debug("No handler set for DoubleSpendProofAnnouncementCallback.");
                    }
                } break;

                default: {
                    if (Logger.isDebugEnabled()) {
                        for (final Sha256Hash objectHash : objectHashes) {
                            Logger.debug("Received unsupported inventory: " + inventoryItemType + ":" + objectHash + " from " + _connection);
                        }
                    }
                } break;
            }
        }

        final MessageType messageType = inventoryMessage.getCommand();
        final Integer byteCount = inventoryMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, false);
        }
    }

    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        if (block == null) {
            Logger.debug("Received invalid block message. " + blockMessage.getBytes());
            return;
        }

        final Sha256Hash blockHash = block.getHash();
        final Boolean blockHeaderIsValid = block.isValid();
        if (! blockHeaderIsValid) {
            Logger.info("Received invalid Block from " + BitcoinNode.this + ": " + blockHash);
        }

        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadBlockRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadBlockCallback> pendingRequest) {
                final DownloadBlockCallback callback = pendingRequest.callback;
                final Block blockOrNull = (blockHeaderIsValid ? block : null);
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, blockOrNull);
            }
        });

        final MessageType messageType = blockMessage.getCommand();
        final Integer byteCount = blockMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }

        if (! wasRequested) {
            Logger.debug("Received unsolicited block: " + blockHash + " from " + BitcoinNode.this);

            final DownloadBlockCallback unsolicitedBlockReceivedCallback = _unsolicitedBlockReceivedCallback;
            if (unsolicitedBlockReceivedCallback != null) {
                unsolicitedBlockReceivedCallback.onResult(null, BitcoinNode.this, block);
            }
        }
    }

    protected void _onUtxoCommitmentMessageReceived(final UtxoCommitmentMessage utxoCommitmentMessage) {
        final PublicKey publicKey = utxoCommitmentMessage.getMultisetPublicKey();
        final ByteArray utxoCommitmentBytes = utxoCommitmentMessage.getUtxoCommitmentBytes();
        if ( (utxoCommitmentBytes == null) || (publicKey == null) ) {
            Logger.debug("Received invalid UTXO Commitment message. " + publicKey);
            return;
        }

        final PublicKey compressedPublicKey = publicKey.compress();

        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadUtxoCommitmentRequests, _failableRequests, compressedPublicKey, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadUtxoCommitmentCallback> pendingRequest) {
                final DownloadUtxoCommitmentCallback callback = pendingRequest.callback;
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, utxoCommitmentBytes);
            }
        });

        final MessageType messageType = utxoCommitmentMessage.getCommand();
        final Integer byteCount = utxoCommitmentMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }
    }

    protected void _onTransactionMessageReceived(final TransactionMessage transactionMessage) {
        final Transaction transaction = transactionMessage.getTransaction();

        final Sha256Hash transactionHash = transaction.getHash();
        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadTransactionRequests, _failableRequests, transactionHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadTransactionCallback> pendingRequest) {
                final DownloadTransactionCallback callback = pendingRequest.callback;
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, transaction);
            }
        });

        final MerkleBlockParameters merkleBlockParameters = _currentMerkleBlockBeingTransmitted;
        if (merkleBlockParameters != null) {
            final MerkleBlock merkleBlock = merkleBlockParameters.getMerkleBlock();
            if (merkleBlock.containsTransaction(transactionHash)) {
                merkleBlockParameters.addTransaction(transaction);
            }

            if (merkleBlockParameters.hasAllTransactions()) {
                _currentMerkleBlockBeingTransmitted = null;
                BitcoinNodeUtil.executeAndClearCallbacks(_downloadMerkleBlockRequests, _failableRequests, merkleBlock.getHash(), new AsyncCallbackExecutor<>() {
                    @Override
                    public void onResult0(final PendingRequest<DownloadMerkleBlockCallback> pendingRequest) {
                        final DownloadMerkleBlockCallback callback = pendingRequest.callback;
                        callback.onResult(pendingRequest.requestId, BitcoinNode.this, merkleBlockParameters);
                    }
                });
            }
        }
        else { // Prevent double-accounting for Transactions sent as part of a MerkleBlock.
            final MessageType messageType = transactionMessage.getCommand();
            final Integer byteCount = transactionMessage.getByteCount();
            for (final BitcoinNodeObserver observer : _observers) {
                observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
            }
        }
    }

    protected void _onDoubleSpendProofMessageReceived(final DoubleSpendProofMessage doubleSpendProofMessage) {
        final DoubleSpendProof doubleSpendProof = doubleSpendProofMessage.getDoubleSpendProof();

        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadDoubleSpendProofRequests, _failableRequests, doubleSpendProofHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadDoubleSpendProofCallback> pendingRequest) {
                final DownloadDoubleSpendProofCallback callback = pendingRequest.callback;
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, doubleSpendProof);
            }
        });

        if (! wasRequested) {
            final DoubleSpendProofAnnouncementHandler doubleSpendProofAnnouncementHandler = _doubleSpendProofAnnouncementCallback;
            if (doubleSpendProofAnnouncementHandler != null) {
                doubleSpendProofAnnouncementHandler.onResult(BitcoinNode.this, new ImmutableList<Sha256Hash>(doubleSpendProofHash));
            }
        }
    }

    protected void _onMerkleBlockReceived(final MerkleBlockMessage merkleBlockMessage) {
        final MerkleBlock merkleBlock = merkleBlockMessage.getMerkleBlock();
        final Boolean merkleBlockIsValid = merkleBlock.isValid();

        final Sha256Hash blockHash = merkleBlock.getHash();

        if (! merkleBlockIsValid) {
            final Set<PendingRequest<DownloadMerkleBlockCallback>> pendingRequests;
            synchronized (_downloadMerkleBlockRequests) {
                pendingRequests = _downloadMerkleBlockRequests.remove(blockHash);
            }
            if (pendingRequests != null) {
                for (final PendingRequest<DownloadMerkleBlockCallback> pendingRequest : pendingRequests) {
                    final DownloadMerkleBlockCallback callback = pendingRequest.callback;
                    callback.onFailure(pendingRequest.requestId, BitcoinNode.this, blockHash);

                    for (final BitcoinNodeObserver observer : _observers) {
                        observer.onFailedRequest(BitcoinNode.this, MessageType.MERKLE_BLOCK, pendingRequest.requestPriority);
                    }
                }
            }
            return;
        }

        final PartialMerkleTree partialMerkleTree = merkleBlock.getPartialMerkleTree();
        final List<Sha256Hash> merkleTreeTransactionHashes = partialMerkleTree.getTransactionHashes();
        final int transactionCount = merkleTreeTransactionHashes.getCount();

        final MerkleBlockParameters merkleBlockParameters = new MerkleBlockParameters(merkleBlock);
        if (transactionCount == 0) {
            // No Transactions should be transmitted alongside this MerkleBlock, so execute any callbacks and return early.
            final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadMerkleBlockRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
                @Override
                public void onResult0(final PendingRequest<DownloadMerkleBlockCallback> pendingRequest) {
                    final DownloadMerkleBlockCallback callback = pendingRequest.callback;
                    callback.onResult(pendingRequest.requestId, BitcoinNode.this, merkleBlockParameters);
                }
            });

            final Integer byteCount = merkleBlockParameters.getByteCount();
            for (final BitcoinNodeObserver observer : _observers) {
                observer.onDataReceived(BitcoinNode.this, MessageType.MERKLE_BLOCK, byteCount, wasRequested);
            }

            return;
        }

        // Wait for additional Transactions to be transmitted.
        //  NOTE: Not all Transactions listed within the MerkleTree will be broadcast, so receiving non-Transaction message will also trigger the completion of the MerkleBlock.
        _currentMerkleBlockBeingTransmitted = merkleBlockParameters;
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

        final boolean wasRequested = (! _downloadBlockHeadersRequests.isEmpty());
        final BlockHeader firstBlockHeader = blockHeaders.get(0);
        final AsyncCallbackExecutor<DownloadBlockHeadersCallback> callbackExecutor = new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadBlockHeadersCallback> pendingRequest) {
                final DownloadBlockHeadersCallback callback = pendingRequest.callback;
                final List<BlockHeader> blockHeadersOrNull = (allBlockHeadersAreValid ? blockHeaders : null);
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, blockHeadersOrNull);
            }
        };
        if (_downloadBlockHeadersRequests.containsKey(firstBlockHeader.getPreviousBlockHash())) {
            BitcoinNodeUtil.executeAndClearCallbacks(_downloadBlockHeadersRequests, _failableRequests, firstBlockHeader.getPreviousBlockHash(), callbackExecutor);
        }
        else { // Trigger all callbacks since a blockFinder may have been requested (i.e. reorg detection).
            for (final Sha256Hash blockHash : _downloadBlockHeadersRequests.getKeys()) {
                BitcoinNodeUtil.executeAndClearCallbacks(_downloadBlockHeadersRequests, _failableRequests, blockHash, callbackExecutor);
            }
        }

        if ( (! wasRequested) && announceNewBlocksViaHeadersIsEnabled ) {
            Logger.trace(firstBlockHeader.getHash() + " was announced by " + BitcoinNode.this + ".");
            final BlockInventoryAnnouncementHandler blockInventoryMessageHandler = _blockInventoryMessageHandler;
            if (blockInventoryMessageHandler != null) {
                blockInventoryMessageHandler.onNewHeaders(BitcoinNode.this, blockHeaders);
            }
        }

        final MessageType messageType = blockHeadersMessage.getCommand();
        final Integer byteCount = blockHeadersMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }
    }

    protected void _onQueryBlocksMessageReceived(final QueryBlocksMessage queryBlocksMessage) {
        final RequestBlockHashesHandler queryBlocksCallback = _queryBlocksCallback;

        if (queryBlocksCallback != null) {
            final MutableList<Sha256Hash> blockHeaderHashes = new MutableArrayList<>(queryBlocksMessage.getBlockHashes());
            final Sha256Hash desiredBlockHeaderHash = queryBlocksMessage.getStopBeforeBlockHash();
            queryBlocksCallback.run(BitcoinNode.this, blockHeaderHashes, desiredBlockHeaderHash);
        }
        else {
            Logger.debug("No handler set for QueryBlocks message.");
        }

        final MessageType messageType = queryBlocksMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onQueryUnconfirmedTransactionsReceived(final QueryUnconfirmedTransactionsMessage queryUnconfirmedTransactionsMessage) {
        final RequestUnconfirmedTransactionsHandler queryUnconfirmedTransactionsCallback = _queryUnconfirmedTransactionsCallback;
        if (queryUnconfirmedTransactionsCallback != null) {
            queryUnconfirmedTransactionsCallback.run(BitcoinNode.this);
        }
        else {
            Logger.debug("No handler set for QueryUnconfirmedTransactions message.");
        }

        final MessageType messageType = queryUnconfirmedTransactionsMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onQueryBlockHeadersMessageReceived(final RequestBlockHeadersMessage requestBlockHeadersMessage) {
        final RequestBlockHeadersHandler queryBlockHeadersCallback = _queryBlockHeadersCallback;

        if (queryBlockHeadersCallback != null) {
            final List<Sha256Hash> blockHeaderHashes = requestBlockHeadersMessage.getBlockHashes();
            final Sha256Hash desiredBlockHeaderHash = requestBlockHeadersMessage.getStopBeforeBlockHash();
            queryBlockHeadersCallback.run(BitcoinNode.this, blockHeaderHashes, desiredBlockHeaderHash);
        }
        else {
            Logger.debug("No handler set for QueryBlockHeaders message.");
        }

        final MessageType messageType = requestBlockHeadersMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onRequestExtraThinBlockMessageReceived(final RequestExtraThinBlockMessage requestExtraThinBlockMessage) {
        final RequestExtraThinBlockHandler requestExtraThinBlockCallback = _requestExtraThinBlockCallback;

        if (requestExtraThinBlockCallback != null) {
            final InventoryItem inventoryItem = requestExtraThinBlockMessage.getInventoryItem();
            if (inventoryItem.getItemType() != InventoryItemType.EXTRA_THIN_BLOCK) { return; }

            final Sha256Hash blockHash = inventoryItem.getItemHash();
            final BloomFilter bloomFilter = requestExtraThinBlockMessage.getBloomFilter();
            requestExtraThinBlockCallback.run(BitcoinNode.this, blockHash, bloomFilter);
        }
        else {
            Logger.debug("No handler set for RequestExtraThinBlock message.");
        }

        final MessageType messageType = requestExtraThinBlockMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onRequestExtraThinTransactionsMessageReceived(final RequestExtraThinTransactionsMessage requestExtraThinTransactionsMessage) {
        final RequestExtraThinTransactionHandler requestExtraThinTransactionCallback = _requestExtraThinTransactionCallback;

        if (requestExtraThinTransactionCallback != null) {
            final Sha256Hash blockHash = requestExtraThinTransactionsMessage.getBlockHash();
            final List<ByteArray> transactionShortHashes = requestExtraThinTransactionsMessage.getTransactionShortHashes();
            requestExtraThinTransactionCallback.run(BitcoinNode.this, blockHash, transactionShortHashes);
        }
        else {
            Logger.debug("No handler set for RequestExtraThinBlock message.");
        }

        final MessageType messageType = requestExtraThinTransactionsMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onThinBlockMessageReceived(final ThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<Sha256Hash> transactionHashes = blockMessage.getTransactionHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ThinBlockParameters thinBlockParameters = new ThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadThinBlockRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadThinBlockCallback> pendingRequest) {
                final DownloadThinBlockCallback callback = pendingRequest.callback;
                final ThinBlockParameters thinBlockParametersOrNull = (blockHeaderIsValid ? thinBlockParameters : null);
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, thinBlockParametersOrNull);
            }
        });

        final MessageType messageType = blockMessage.getCommand();
        final Integer byteCount = blockMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }
    }

    protected void _onExtraThinBlockMessageReceived(final ExtraThinBlockMessage blockMessage) {
        final BlockHeader blockHeader = blockMessage.getBlockHeader();
        final List<ByteArray> transactionHashes = blockMessage.getTransactionShortHashes();
        final List<Transaction> transactions = blockMessage.getMissingTransactions();
        final Boolean blockHeaderIsValid = blockHeader.isValid();

        final ExtraThinBlockParameters extraThinBlockParameters = new ExtraThinBlockParameters(blockHeader, transactionHashes, transactions);

        final Sha256Hash blockHash = blockHeader.getHash();
        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadExtraThinBlockRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadExtraThinBlockCallback> pendingRequest) {
                final DownloadExtraThinBlockCallback callback = pendingRequest.callback;
                final ExtraThinBlockParameters extraThinBlockParametersOrNull = (blockHeaderIsValid ? extraThinBlockParameters : null);
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, extraThinBlockParametersOrNull);
            }
        });

        final MessageType messageType = blockMessage.getCommand();
        final Integer byteCount = blockMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }
    }

    protected void _onThinTransactionsMessageReceived(final ThinTransactionsMessage transactionsMessage) {
        final Sha256Hash blockHash = transactionsMessage.getBlockHash();
        final List<Transaction> transactions = transactionsMessage.getTransactions();

        final Boolean wasRequested = BitcoinNodeUtil.executeAndClearCallbacks(_downloadThinTransactionsRequests, _failableRequests, blockHash, new AsyncCallbackExecutor<>() {
            @Override
            public void onResult0(final PendingRequest<DownloadThinTransactionsCallback> pendingRequest) {
                final DownloadThinTransactionsCallback callback = pendingRequest.callback;
                callback.onResult(pendingRequest.requestId, BitcoinNode.this, transactions);
            }
        });

        final MessageType messageType = transactionsMessage.getCommand();
        final Integer byteCount = transactionsMessage.getByteCount();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataReceived(BitcoinNode.this, messageType, byteCount, wasRequested);
        }
    }

    protected void _onNotFoundMessageReceived(final NotFoundResponseMessage notFoundResponseMessage) {
        Logger.trace("Received NOT FOUND from " + BitcoinNode.this + ".");

        for (final InventoryItem inventoryItem : notFoundResponseMessage.getInventoryItems()) {
            final Sha256Hash itemHash = inventoryItem.getItemHash();
            final InventoryItemType inventoryItemType = inventoryItem.getItemType();
            switch (inventoryItemType) {
                case BLOCK: {
                    synchronized (_downloadBlockRequests) {
                        final Set<PendingRequest<DownloadBlockCallback>> downloadBlockCallbacks = _downloadBlockRequests.remove(itemHash);
                        if (downloadBlockCallbacks == null) { return; }

                        for (final PendingRequest<DownloadBlockCallback> pendingRequest : downloadBlockCallbacks) {
                            _failableRequests.remove(pendingRequest.requestId);
                            pendingRequest.callback.onFailure(pendingRequest.requestId, BitcoinNode.this, itemHash);

                            for (final BitcoinNodeObserver observer : _observers) {
                                observer.onBlockNotFound(BitcoinNode.this, itemHash);
                            }
                        }
                    }
                } break;

                case TRANSACTION: {
                    synchronized (_downloadTransactionRequests) {
                        final Set<PendingRequest<DownloadTransactionCallback>> downloadTransactionPendingRequests = _downloadTransactionRequests.remove(itemHash);
                        if (downloadTransactionPendingRequests == null) { return; }

                        for (final PendingRequest<DownloadTransactionCallback> pendingRequest : downloadTransactionPendingRequests) {
                            pendingRequest.callback.onFailure(pendingRequest.requestId, BitcoinNode.this, itemHash);

                            for (final BitcoinNodeObserver observer : _observers) {
                                observer.onTransactionNotFound(BitcoinNode.this, itemHash);
                            }
                        }
                    }
                } break;

                case MERKLE_BLOCK: {
                    synchronized (_downloadMerkleBlockRequests) {
                        final Set<PendingRequest<DownloadMerkleBlockCallback>> downloadMerkleBlockPendingRequests = _downloadMerkleBlockRequests.remove(itemHash);
                        if (downloadMerkleBlockPendingRequests == null) { return; }

                        for (final PendingRequest<DownloadMerkleBlockCallback> pendingRequest : downloadMerkleBlockPendingRequests) {
                            pendingRequest.callback.onFailure(pendingRequest.requestId, BitcoinNode.this, itemHash);

                            for (final BitcoinNodeObserver observer : _observers) {
                                observer.onBlockNotFound(BitcoinNode.this, itemHash);
                            }
                        }
                    }
                } break;

                case UTXO_COMMITMENT_EVEN:
                case UTXO_COMMITMENT_ODD: {
                    synchronized (_downloadUtxoCommitmentRequests) {
                        final PublicKey publicKey = RequestDataMessage.convertUtxoCommitmentInventoryToPublicKey(inventoryItemType, itemHash);
                        final Set<PendingRequest<DownloadUtxoCommitmentCallback>> downloadUtxoCommitmentCallbacks = _downloadUtxoCommitmentRequests.remove(publicKey);
                        if (downloadUtxoCommitmentCallbacks == null) { return; }

                        for (final PendingRequest<DownloadUtxoCommitmentCallback> pendingRequest : downloadUtxoCommitmentCallbacks) {
                            _failableRequests.remove(pendingRequest.requestId);
                            pendingRequest.callback.onFailure(pendingRequest.requestId, BitcoinNode.this, publicKey);

                            for (final BitcoinNodeObserver observer : _observers) {
                                observer.onBlockNotFound(BitcoinNode.this, itemHash);
                            }
                        }
                    }
                } break;

                default: {
                    Logger.info("Unsolicited NOT_FOUND Message: " + inventoryItem.getItemType() + " : " + inventoryItem.getItemHash() + " from " + BitcoinNode.this);
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

        final MessageType messageType = requestPeersMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _onSetTransactionBloomFilterMessageReceived(final SetTransactionBloomFilterMessage setTransactionBloomFilterMessage) {
        _bloomFilter = MutableBloomFilter.copyOf(setTransactionBloomFilterMessage.getBloomFilter());
        _transactionRelayIsEnabled = true;

        final NewBloomFilterHandler onNewBloomFilterCallback = _onNewBloomFilterCallback;
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

    protected void _onQueryUtxoCommitmentsMessageReceived(final QueryUtxoCommitmentsMessage queryUtxoCommitmentsMessage) {
        final QueryUtxoCommitmentsHandler queryUtxoCommitmentsHandler = _queryUtxoCommitmentsHandler;
        if (queryUtxoCommitmentsHandler == null) {
            Logger.debug("No handler set for QueryUtxoCommitments message.");
            return;
        }

        queryUtxoCommitmentsHandler.run(BitcoinNode.this);
    }

    protected void _onUtxoCommitmentsReceived(final UtxoCommitmentsMessage utxoCommitmentsMessage) {
        final List<NodeSpecificUtxoCommitmentBreakdown> utxoCommitmentBreakdowns = utxoCommitmentsMessage.getUtxoCommitments();

        synchronized (_utxoCommitmentsCallbacks) {
            for (final Tuple<RequestId, UtxoCommitmentsCallback> callbackEntry : _utxoCommitmentsCallbacks) {
                final RequestId requestId = callbackEntry.first;
                final UtxoCommitmentsCallback utxoCommitmentsCallback = callbackEntry.second;

                utxoCommitmentsCallback.onResult(requestId, BitcoinNode.this, utxoCommitmentBreakdowns);
            }
            _utxoCommitmentsCallbacks.clear();
        }
    }

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = _protocolMessageFactory.newQueryBlocksMessage();
        queryBlocksMessage.addBlockHash(blockHash);
        _queueMessage(queryBlocksMessage);

        final MessageType messageType = queryBlocksMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);

        final MessageType messageType = requestDataMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestMerkleBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(InventoryItemType.MERKLE_BLOCK, blockHash));

        final MutableList<BitcoinProtocolMessage> messages = new MutableArrayList<>(2);
        messages.add(requestDataMessage);
        messages.add(_protocolMessageFactory.newPingMessage()); // A ping message is sent to ensure the remote node responds with a non-transaction message (Pong) to close out the MerkleBlockMessage transmission.
        _queueMessages(messages);

        final MessageType messageType = requestDataMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.COMPACT_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = _protocolMessageFactory.newRequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);

        final MessageType messageType = requestExtraThinBlockMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter) {
        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.EXTRA_THIN_BLOCK, blockHash);

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = _protocolMessageFactory.newRequestExtraThinBlockMessage();
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);
        requestExtraThinBlockMessage.setBloomFilter(knownTransactionsFilter);

        _queueMessage(requestExtraThinBlockMessage);

        final MessageType messageType = requestExtraThinBlockMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestThinTransactions(final Sha256Hash blockHash, final List<ByteArray> transactionShortHashes) {
        final RequestExtraThinTransactionsMessage requestThinTransactionsMessage = _protocolMessageFactory.newRequestExtraThinTransactionsMessage();
        requestThinTransactionsMessage.setBlockHash(blockHash);
        requestThinTransactionsMessage.setTransactionShortHashes(transactionShortHashes);

        _queueMessage(requestThinTransactionsMessage);

        final MessageType messageType = requestThinTransactionsMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestBlockHeaders(final List<Sha256Hash> blockHashes) {
        final RequestBlockHeadersMessage requestBlockHeadersMessage = _protocolMessageFactory.newRequestBlockHeadersMessage();
        for (final Sha256Hash blockHash : blockHashes) {
            requestBlockHeadersMessage.addBlockHash(blockHash);
        }
        _queueMessage(requestBlockHeadersMessage);

        final MessageType messageType = requestBlockHeadersMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    protected void _requestTransactions(final List<Sha256Hash> transactionHashes) {
        final RequestDataMessage requestTransactionMessage = _protocolMessageFactory.newRequestDataMessage();
        for (final Sha256Hash transactionHash : transactionHashes) {
            requestTransactionMessage.addInventoryItem(new InventoryItem(InventoryItemType.TRANSACTION, transactionHash));
        }
        _queueMessage(requestTransactionMessage);

        final MessageType messageType = requestTransactionMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }
    }

    public BitcoinNode(final String host, final Integer port, final LocalNodeFeatures localNodeFeatures) {
        this(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final String host, final Integer port, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        this(host, port, BitcoinProtocolMessage.BINARY_PACKET_FORMAT, localNodeFeatures, addressInflater);
    }

    public BitcoinNode(final String host, final Integer port, final BitcoinBinaryPacketFormat binaryPacketFormat, final LocalNodeFeatures localNodeFeatures) {
        this(host, port, binaryPacketFormat, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final String host, final Integer port, final BitcoinBinaryPacketFormat binaryPacketFormat, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        super(host, port, binaryPacketFormat);
        _addressInflater = addressInflater;
        _localNodeFeatures = localNodeFeatures;

        _protocolMessageFactory = binaryPacketFormat.getProtocolMessageFactory();

        _requestMonitor = _createRequestMonitor();
        _requestMonitorThread = null;

        // _callbackWorker = new WorkerManager(2, 1024);
        // _callbackWorker.setName("Callback Worker");
        // _callbackWorker.start();

        _defineRoutes();
        _initConnection();
    }

    /**
     * Constructs a BitcoinNode from an already-connected BinarySocket.
     *  The BinarySocket must have been created with a BitcoinProtocolMessageFactory.
     */
    public BitcoinNode(final BinarySocket binarySocket, final LocalNodeFeatures localNodeFeatures) {
        this(binarySocket, localNodeFeatures, DEFAULT_ADDRESS_INFLATER);
    }

    public BitcoinNode(final BinarySocket binarySocket, final LocalNodeFeatures localNodeFeatures, final AddressInflater addressInflater) {
        super(binarySocket);
        _localNodeFeatures = localNodeFeatures;
        _addressInflater = addressInflater;

        final BinaryPacketFormat binaryPacketFormat = _connection.getBinaryPacketFormat();
        _protocolMessageFactory = (BitcoinProtocolMessageFactory) binaryPacketFormat.getProtocolMessageFactory();

        _requestMonitor = _createRequestMonitor();
        _requestMonitorThread = null;

        // _callbackWorker = new WorkerManager(2, 1024);
        // _callbackWorker.setName("Callback Worker");
        // _callbackWorker.start();

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

    public void requestNodeAddresses() {
        _peersWereRequested = true;
        _queueMessage(new RequestPeersMessage());
    }

    public RequestId requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        return this.requestBlock(blockHash, downloadBlockCallback, RequestPriority.NORMAL);
    }

    public RequestId requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();
        BitcoinNodeUtil.storeInMapSet(_downloadBlockRequests, blockHash, new PendingRequest<>(requestId, downloadBlockCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        _failableRequests.put(requestId, new FailableRequest("BLOCK " + blockHash, requestStartBytesReceived, downloadBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadBlockCallback.onFailure(requestId, BitcoinNode.this, blockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.BLOCK, requestPriority);
                }
            }
        }));

        _requestBlock(blockHash);

        return requestId;
    }

    public RequestId requestUtxoCommitment(final PublicKey publicKey, final DownloadUtxoCommitmentCallback downloadUtxoCommitmentCallback) {
        return this.requestUtxoCommitment(publicKey, downloadUtxoCommitmentCallback, RequestPriority.NORMAL);
    }

    public RequestId requestUtxoCommitment(final PublicKey publicKey, final DownloadUtxoCommitmentCallback downloadUtxoCommitmentCallback, final RequestPriority requestPriority) {
        final PublicKey compressedPublicKey = publicKey.compress();

        final InventoryItemType inventoryItemType;
        final Sha256Hash bucketHash;
        {
            final Tuple<InventoryItemType, Sha256Hash> tuple = RequestDataMessage.convertUtxoCommitmentPublicKeyToInventory(compressedPublicKey);
            inventoryItemType = tuple.first;
            bucketHash = tuple.second;
        }

        final RequestId requestId = _newRequestId();
        BitcoinNodeUtil.storeInMapSet(_downloadUtxoCommitmentRequests, compressedPublicKey, new PendingRequest<>(requestId, downloadUtxoCommitmentCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        _failableRequests.put(requestId, new FailableRequest("UTXO COMMITMENT " + bucketHash, requestStartBytesReceived, downloadUtxoCommitmentCallback, new Runnable() {
            @Override
            public void run() {
                downloadUtxoCommitmentCallback.onFailure(requestId, BitcoinNode.this, compressedPublicKey);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.BLOCK, requestPriority);
                }
            }
        }));

        final RequestDataMessage requestDataMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDataMessage.addInventoryItem(new InventoryItem(inventoryItemType, bucketHash));
        _queueMessage(requestDataMessage);

        final MessageType messageType = requestDataMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }

        return requestId;
    }

    public RequestId requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback downloadMerkleBlockCallback) {
        return this.requestMerkleBlock(blockHash, downloadMerkleBlockCallback, RequestPriority.NORMAL);
    }

    public RequestId requestMerkleBlock(final Sha256Hash blockHash, final DownloadMerkleBlockCallback downloadMerkleBlockCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();
        BitcoinNodeUtil.storeInMapSet(_downloadMerkleBlockRequests, blockHash, new PendingRequest<>(requestId, downloadMerkleBlockCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        _failableRequests.put(requestId, new FailableRequest("MERKLE BLOCK " + blockHash, requestStartBytesReceived, downloadMerkleBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadMerkleBlockCallback.onFailure(requestId, BitcoinNode.this, blockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.MERKLE_BLOCK, requestPriority);
                }
            }
        }));

        _requestMerkleBlock(blockHash);
        return requestId;
    }

    public RequestId requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadThinBlockCallback downloadThinBlockCallback) {
        return this.requestThinBlock(blockHash, knownTransactionsFilter, downloadThinBlockCallback, RequestPriority.NORMAL);
    }

    public RequestId requestThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadThinBlockCallback downloadThinBlockCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();
        BitcoinNodeUtil.storeInMapSet(_downloadThinBlockRequests, blockHash, new PendingRequest<>(requestId, downloadThinBlockCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        _failableRequests.put(requestId, new FailableRequest("THIN BLOCK " + blockHash, requestStartBytesReceived, downloadThinBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadThinBlockCallback.onFailure(requestId, BitcoinNode.this, blockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.THIN_BLOCK, requestPriority);
                }
            }
        }));
        _requestThinBlock(blockHash, knownTransactionsFilter);
        return requestId;
    }

    public RequestId requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadExtraThinBlockCallback downloadThinBlockCallback) {
        return this.requestExtraThinBlock(blockHash, knownTransactionsFilter, downloadThinBlockCallback, RequestPriority.NORMAL);
    }

    public RequestId requestExtraThinBlock(final Sha256Hash blockHash, final BloomFilter knownTransactionsFilter, final DownloadExtraThinBlockCallback downloadThinBlockCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();
        BitcoinNodeUtil.storeInMapSet(_downloadExtraThinBlockRequests, blockHash, new PendingRequest<>(requestId, downloadThinBlockCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        _failableRequests.put(requestId, new FailableRequest("xTHIN BLOCK " + blockHash, requestStartBytesReceived, downloadThinBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadThinBlockCallback.onFailure(requestId, BitcoinNode.this, blockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.EXTRA_THIN_BLOCK, requestPriority);
                }
            }
        }));
        _requestExtraThinBlock(blockHash, knownTransactionsFilter);
        return requestId;
    }

    public RequestId requestThinTransactions(final Sha256Hash blockHash, final List<Sha256Hash> transactionHashes, final DownloadThinTransactionsCallback downloadThinBlockCallback) {
        return this.requestThinTransactions(blockHash, transactionHashes, downloadThinBlockCallback, RequestPriority.NORMAL);
    }

    public RequestId requestThinTransactions(final Sha256Hash blockHash, final List<Sha256Hash> transactionHashes, final DownloadThinTransactionsCallback downloadThinBlockCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();
        final ImmutableListBuilder<ByteArray> shortTransactionHashesBuilder = new ImmutableListBuilder<>(transactionHashes.getCount());
        for (final Sha256Hash transactionHash : transactionHashes) {
            final ByteArray shortTransactionHash = MutableByteArray.wrap(transactionHash.getBytes(0, 8));
            shortTransactionHashesBuilder.add(shortTransactionHash);
        }
        final List<ByteArray> shortTransactionHashes = shortTransactionHashesBuilder.build();

        BitcoinNodeUtil.storeInMapSet(_downloadThinTransactionsRequests, blockHash, new PendingRequest<>(requestId, downloadThinBlockCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        final String requestDescription = "THIN TXs (block: " + blockHash + ", txCount: " + transactionHashes.getCount() + ")";
        _failableRequests.put(requestId, new FailableRequest(requestDescription, requestStartBytesReceived, downloadThinBlockCallback, new Runnable() {
            @Override
            public void run() {
                downloadThinBlockCallback.onFailure(requestId, BitcoinNode.this, blockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.THIN_TRANSACTIONS, requestPriority);
                }
            }
        }));
        _requestThinTransactions(blockHash, shortTransactionHashes);
        return requestId;
    }

    public RequestId requestBlockHeadersAfter(final Sha256Hash blockHash, final DownloadBlockHeadersCallback downloadBlockHeaderCallback) {
        return this.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(blockHash), downloadBlockHeaderCallback, RequestPriority.NORMAL);
    }

    public RequestId requestBlockHeadersAfter(final Sha256Hash blockHash, final DownloadBlockHeadersCallback downloadBlockHeaderCallback, final RequestPriority requestPriority) {
        return this.requestBlockHeadersAfter(new ImmutableList<Sha256Hash>(blockHash), downloadBlockHeaderCallback, requestPriority);
    }

    public RequestId requestBlockHeadersAfter(final List<Sha256Hash> blockFinder, final DownloadBlockHeadersCallback downloadBlockHeaderCallback) {
        return this.requestBlockHeadersAfter(blockFinder, downloadBlockHeaderCallback, RequestPriority.NORMAL);
    }

    public RequestId requestBlockHeadersAfter(final List<Sha256Hash> blockFinder, final DownloadBlockHeadersCallback downloadBlockHeaderCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();

        if (blockFinder.isEmpty()) {
            downloadBlockHeaderCallback.onFailure(requestId, BitcoinNode.this, null);
            return requestId;
        }

        final Sha256Hash firstBlockHash = blockFinder.get(0);
        BitcoinNodeUtil.storeInMapSet(_downloadBlockHeadersRequests, firstBlockHash, new PendingRequest<>(requestId, downloadBlockHeaderCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        final String requestDescription = "BLOCK FINDER " + blockFinder.get(0) + " - " + blockFinder.get(blockFinder.getCount() - 1);
        _failableRequests.put(requestId, new FailableRequest(requestDescription, requestStartBytesReceived, downloadBlockHeaderCallback, new Runnable() {
            @Override
            public void run() {
                downloadBlockHeaderCallback.onFailure(requestId, BitcoinNode.this, firstBlockHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.BLOCK_HEADERS, requestPriority);
                }
            }
        }));
        _requestBlockHeaders(blockFinder);
        return requestId;
    }

    public RequestId requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback downloadTransactionCallback) {
        return this.requestTransactions(transactionHashes, downloadTransactionCallback, RequestPriority.NORMAL);
    }

    public RequestId requestTransactions(final List<Sha256Hash> transactionHashes, final DownloadTransactionCallback downloadTransactionCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();

        if (transactionHashes.isEmpty()) {
            downloadTransactionCallback.onFailure(requestId, BitcoinNode.this, null);
            return requestId;
        }

        for (final Sha256Hash transactionHash : transactionHashes) {
            BitcoinNodeUtil.storeInMapSet(_downloadTransactionRequests, transactionHash, new PendingRequest<>(requestId, downloadTransactionCallback, requestPriority));
            final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
            final String requestDescription = "TXs (count: " + transactionHashes.getCount() + ")";
            _failableRequests.put(requestId, new FailableRequest(requestDescription, requestStartBytesReceived, downloadTransactionCallback, new Runnable() {
                @Override
                public void run() {
                    downloadTransactionCallback.onFailure(requestId, BitcoinNode.this, transactionHash);

                    for (final BitcoinNodeObserver observer : _observers) {
                        observer.onFailedRequest(BitcoinNode.this, MessageType.TRANSACTION, requestPriority);
                    }
                }
            }));
        }
        _requestTransactions(transactionHashes);
        return requestId;
    }

    public void transmitDoubleSpendProofHash(final Sha256Hash doubleSpendProofHash) {
        final InventoryMessage inventoryMessage = _protocolMessageFactory.newInventoryMessage();

        final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.DOUBLE_SPEND_PROOF, doubleSpendProofHash);
        inventoryMessage.addInventoryItem(inventoryItem);

        _queueMessage(inventoryMessage);
    }

    public void transmitDoubleSpendProof(final DoubleSpendProof doubleSpendProof) {
        final DoubleSpendProofMessage doubleSpendProofMessage = _protocolMessageFactory.newDoubleSpendProofMessage();
        doubleSpendProofMessage.setDoubleSpendProof(doubleSpendProof);
        _queueMessage(doubleSpendProofMessage);
    }

    public RequestId requestDoubleSpendProof(final Sha256Hash doubleSpendProofHash, final DownloadDoubleSpendProofCallback downloadDoubleSpendProofCallback) {
        return this.requestDoubleSpendProof(doubleSpendProofHash, downloadDoubleSpendProofCallback, RequestPriority.NORMAL);
    }

    public RequestId requestDoubleSpendProof(final Sha256Hash doubleSpendProofHash, final DownloadDoubleSpendProofCallback downloadDoubleSpendProofCallback, final RequestPriority requestPriority) {
        final RequestId requestId = _newRequestId();

        BitcoinNodeUtil.storeInMapSet(_downloadDoubleSpendProofRequests, doubleSpendProofHash, new PendingRequest<>(requestId, downloadDoubleSpendProofCallback, requestPriority));
        final Long requestStartBytesReceived = _connection.getTotalBytesReceivedCount();
        final String requestDescription = ("Double Spend Proof: " + doubleSpendProofHash);
        _failableRequests.put(requestId, new FailableRequest(requestDescription, requestStartBytesReceived, downloadDoubleSpendProofCallback, new Runnable() {
            @Override
            public void run() {
                downloadDoubleSpendProofCallback.onFailure(requestId, BitcoinNode.this, doubleSpendProofHash);

                for (final BitcoinNodeObserver observer : _observers) {
                    observer.onFailedRequest(BitcoinNode.this, MessageType.DOUBLE_SPEND_PROOF, requestPriority);
                }
            }
        }));

        final RequestDataMessage requestDoubleSpendProofMessage = _protocolMessageFactory.newRequestDataMessage();
        requestDoubleSpendProofMessage.addInventoryItem(new InventoryItem(InventoryItemType.DOUBLE_SPEND_PROOF, doubleSpendProofHash));
        _queueMessage(requestDoubleSpendProofMessage);

        final MessageType messageType = requestDoubleSpendProofMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }

        return requestId;
    }

    public void transmitTransactionHashes(final List<Sha256Hash> transactionHashes) {
        final InventoryMessage inventoryMessage = _protocolMessageFactory.newInventoryMessage();
        for (final Sha256Hash transactionHash : transactionHashes) {
            final InventoryItem inventoryItem = new InventoryItem(InventoryItemType.TRANSACTION, transactionHash);
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

        Logger.debug("Setting Bloom Filter for Peer: " + _connection);
        if (Logger.isTraceEnabled()) {
            final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
            Logger.trace(bloomFilterDeflater.toBytes(bloomFilter));
        }
    }

    /**
     * Sets a callback for when the remote node defines a new BloomFilter.
     *  NOTE: This is the remote BloomFilter, not the local filter defined by ::setBloomFilter.
     */
    public void setNewBloomFilterHandler(final NewBloomFilterHandler onNewBloomFilterCallback) {
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

    public void transmitUtxoCommitment(final PublicKey publicKey, final ByteArray byteArray) {
        final UtxoCommitmentMessage utxoCommitmentMessage = _protocolMessageFactory.newUtxoCommitmentMessage();
        utxoCommitmentMessage.setMultisetPublicKey(publicKey);
        utxoCommitmentMessage.setUtxoCommitmentBytes(byteArray);

        _queueMessage(utxoCommitmentMessage);
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
            final MutableList<ProtocolMessage> messages = new MutableArrayList<>();

            final PartialMerkleTree partialMerkleTree;
            try {
                partialMerkleTree = block.getPartialMerkleTree(bloomFilter);
            }
            catch (final RuntimeException exception) {
                // 2021-12-16: There is a bug causing an IndexOutOfBounds exception for some BloomFilter/Block combination.  Remove this once resolved.
                if (Logger.isDebugEnabled()) {
                    final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
                    final Sha256Hash blockHash = block.getHash();
                    final ByteArray bloomFilterBytes = bloomFilterDeflater.toBytes(bloomFilter);
                    Logger.debug("Unable to calculate PartialMerkleTree: " + blockHash + " " + bloomFilterBytes, exception);
                }

                throw exception;
            }

            final MerkleBlockMessage merkleBlockMessage = _protocolMessageFactory.newMerkleBlockMessage();
            merkleBlockMessage.setBlockHeader(block);
            merkleBlockMessage.setPartialMerkleTree(partialMerkleTree);
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

    public void setRequestBlockHashesHandler(final RequestBlockHashesHandler queryBlocksCallback) {
        _queryBlocksCallback = queryBlocksCallback;
    }

    public void setRequestBlockHeadersHandler(final RequestBlockHeadersHandler queryBlockHeadersCallback) {
        _queryBlockHeadersCallback = queryBlockHeadersCallback;
    }

    public void setRequestDataHandler(final RequestDataHandler requestDataCallback) {
        _requestDataHandler = requestDataCallback;
    }

    public void setRequestSpvBlocksHandler(final RequestSpvBlocksHandler requestSpvBlocksCallback) {
        _requestSpvBlocksHandler = requestSpvBlocksCallback;
    }

    public void setQueryUtxoCommitmentsHandler(final QueryUtxoCommitmentsHandler queryUtxoCommitmentsHandler) {
        _queryUtxoCommitmentsHandler = queryUtxoCommitmentsHandler;
    }

    public void setBlockInventoryMessageHandler(final BlockInventoryAnnouncementHandler blockInventoryMessageHandler) {
        _blockInventoryMessageHandler = blockInventoryMessageHandler;
    }

    public void setRequestPeersHandler(final RequestPeersHandler requestPeersHandler) {
        _requestPeersHandler = requestPeersHandler;
    }

    public void setRequestUnconfirmedTransactionsHandler(final RequestUnconfirmedTransactionsHandler queryUnconfirmedTransactionsCallback) {
        _queryUnconfirmedTransactionsCallback = queryUnconfirmedTransactionsCallback;
    }

    public void setRequestExtraThinBlockHandler(final RequestExtraThinBlockHandler requestExtraThinBlockCallback) {
        _requestExtraThinBlockCallback = requestExtraThinBlockCallback;
    }

    public void setTransactionsAnnouncementCallback(final TransactionInventoryAnnouncementHandler transactionsAnnouncementCallback) {
        _transactionsAnnouncementCallback = transactionsAnnouncementCallback;
    }

    public void setSpvBlockInventoryAnnouncementCallback(final SpvBlockInventoryAnnouncementHandler spvBlockInventoryAnnouncementCallback) {
        _spvBlockInventoryAnnouncementCallback = spvBlockInventoryAnnouncementCallback;
    }

    public void setDoubleSpendProofAnnouncementCallback(final DoubleSpendProofAnnouncementHandler doubleSpendProofAnnouncementCallback) {
        _doubleSpendProofAnnouncementCallback = doubleSpendProofAnnouncementCallback;
    }

    public Boolean isNewBlocksViaHeadersEnabled() {
        return _announceNewBlocksViaHeadersIsEnabled;
    }

    public Boolean supportsExtraThinBlocks() {
        if (_synchronizeVersionMessage == null) { return false; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        return nodeFeatures.isFeatureEnabled(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED);
    }

    public String getUserAgent() {
        if (_synchronizeVersionMessage == null) { return null; }
        return _synchronizeVersionMessage.getUserAgent();
    }

    public Boolean hasFeatureEnabled(final NodeFeatures.Feature feature) {
        if (_synchronizeVersionMessage == null) { return null; }

        final NodeFeatures nodeFeatures = _synchronizeVersionMessage.getNodeFeatures();
        return nodeFeatures.isFeatureEnabled(feature);
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

    public RequestId requestUtxoCommitments(final UtxoCommitmentsCallback utxoCommitmentsCallback) {
        final RequestId requestId = _newRequestId();
        final QueryUtxoCommitmentsMessage queryUtxoCommitmentsMessage = _protocolMessageFactory.newQueryUtxoCommitmentsMessage();

        synchronized (_utxoCommitmentsCallbacks) {
            _utxoCommitmentsCallbacks.put(requestId, utxoCommitmentsCallback);
        }

        _queueMessage(queryUtxoCommitmentsMessage);

        final MessageType messageType = queryUtxoCommitmentsMessage.getCommand();
        for (final BitcoinNodeObserver observer : _observers) {
            observer.onDataRequested(BitcoinNode.this, messageType);
        }

        return requestId;
    }

    @Override
    public BitcoinNodeIpAddress getLocalNodeIpAddress() {
        final NodeIpAddress nodeIpAddress = super.getLocalNodeIpAddress();
        final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(nodeIpAddress);
        if (_localNodeFeatures != null) {
            final NodeFeatures localNodeFeatures = _localNodeFeatures.getNodeFeatures();
            bitcoinNodeIpAddress.setNodeFeatures(localNodeFeatures);
        }

        return bitcoinNodeIpAddress;
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

    public void removeCallback(final RequestId requestId) {
        _removeCallback(requestId);
    }

    public void clearRequests() {
        synchronized (_downloadBlockRequests) { _downloadBlockRequests.clear(); }
        synchronized (_downloadUtxoCommitmentRequests) { _downloadUtxoCommitmentRequests.clear(); }
        synchronized (_downloadMerkleBlockRequests) { _downloadMerkleBlockRequests.clear(); }
        synchronized (_downloadBlockHeadersRequests) { _downloadBlockHeadersRequests.clear(); }
        synchronized (_downloadTransactionRequests) { _downloadTransactionRequests.clear(); }
        synchronized (_downloadThinBlockRequests) { _downloadThinBlockRequests.clear(); }
        synchronized (_downloadExtraThinBlockRequests) { _downloadExtraThinBlockRequests.clear(); }
        synchronized (_downloadThinTransactionsRequests) { _downloadThinTransactionsRequests.clear(); }
        synchronized (_downloadAddressBlocksRequests) { _downloadAddressBlocksRequests.clear(); }

        _failableRequests.clear();
    }

    /**
     * Returns the blockHeight defined during the Node's handshake or null if the handshake has not completed.
     */
    public Long getBlockHeight() {
        return _blockHeight;
    }

    public void setBlockHeight(final Long blockHeight) {
        _blockHeight = blockHeight;
    }

    public void addObserver(final BitcoinNodeObserver observer) {
        _observers.add(observer);
    }

    public void removeObserver(final BitcoinNodeObserver observer) {
        _observers.remove(observer);
    }

    public void setUnsolicitedBlockReceivedCallback(final DownloadBlockCallback unsolicitedBlockReceivedCallback) {
        _unsolicitedBlockReceivedCallback = unsolicitedBlockReceivedCallback;
    }

    public List<UnfulfilledSha256HashRequest> getPendingBlockRequests() {
        return _getPendingSha256HashRequests(_downloadBlockRequests);
    }

    public List<UnfulfilledSha256HashRequest> getPendingTransactionRequests() {
        return _getPendingSha256HashRequests(_downloadTransactionRequests);
    }

    public List<UnfulfilledSha256HashRequest> getPendingBlockHeadersRequests() {
        return _getPendingSha256HashRequests(_downloadBlockHeadersRequests);
    }

    public List<UnfulfilledSha256HashRequest> getPendingMerkleBlockRequests() {
        return _getPendingSha256HashRequests(_downloadMerkleBlockRequests);
    }

    public List<UnfulfilledPublicKeyRequest> getPendingUtxoCommitmentRequests() {
        return _getPendingPublicKeyRequests(_downloadUtxoCommitmentRequests);
    }
}

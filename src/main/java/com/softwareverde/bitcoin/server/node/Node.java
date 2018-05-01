package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.block.BlockMessage;
import com.softwareverde.bitcoin.server.message.type.error.ErrorMessage;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.address.NodeIpAddressMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.ping.PingMessage;
import com.softwareverde.bitcoin.server.message.type.node.pong.PongMessage;
import com.softwareverde.bitcoin.server.message.type.query.block.QueryBlocksMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.QueryResponseMessage;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.server.message.type.request.RequestDataMessage;
import com.softwareverde.bitcoin.server.message.type.version.acknowledge.AcknowledgeVersionMessage;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.SynchronizeVersionMessage;
import com.softwareverde.bitcoin.server.socket.ip.Ip;
import com.softwareverde.bitcoin.server.socket.ip.IpInflater;
import com.softwareverde.bitcoin.server.socket.ip.Ipv4;
import com.softwareverde.bitcoin.type.callback.Callback;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;

import java.util.*;

public class Node extends NodeConnectionDelegate {
    public interface NodeAddressesReceivedCallback {
        void onNewNodeAddress(NodeIpAddress nodeIpAddress);
    }

    public interface NodeConnectedCallback {
        void onNodeConnected();
    }

    public interface NodeHandshakeCompleteCallback {
        void onHandshakeComplete();
    }

    public interface NodeDisconnectedCallback {
        void onNodeDisconnected();
    }

    protected class PingRequest {
        public final PingCallback pingCallback;
        public final Long timestamp;

        public PingRequest(final PingCallback pingCallback) {
            this.pingCallback = pingCallback;
            this.timestamp = System.currentTimeMillis();
        }
    }

    protected static final Object NODE_ID_MUTEX = new Object();
    protected static Long _nextId = 0L;

    public interface QueryCallback extends Callback<List<Sha256Hash>> { }
    public interface DownloadBlockCallback extends Callback<Block> { }
    public interface PingCallback extends Callback<Long> { }

    protected static class BlockHashQueryCallback implements Callback<List<Sha256Hash>> {
        public Sha256Hash afterBlockHash;
        public QueryCallback callback;

        public BlockHashQueryCallback(final Sha256Hash afterBlockHash, final QueryCallback callback) {
            this.afterBlockHash = afterBlockHash;
            this.callback = callback;
        }

        @Override
        public void onResult(final List<Sha256Hash> result) {
            this.callback.onResult(result);
        }
    }

    protected NodeId _id;
    protected NodeIpAddress _nodeIpAddress = null;
    protected Boolean _handshakeIsComplete = false;
    protected final List<ProtocolMessage> _postHandshakeMessageQueue = new LinkedList<ProtocolMessage>();
    protected final Map<DataHashType, Set<DataHash>> _availableDataHashes = new HashMap<DataHashType, Set<DataHash>>();

    protected final Map<DataHashType, Set<BlockHashQueryCallback>> _queryRequests = new HashMap<DataHashType, Set<BlockHashQueryCallback>>();
    protected final Map<Sha256Hash, Set<DownloadBlockCallback>> _downloadBlockRequests = new HashMap<Sha256Hash, Set<DownloadBlockCallback>>();
    protected final Map<Long, PingRequest> _pingRequests = new HashMap<Long, PingRequest>();

    protected NodeAddressesReceivedCallback _nodeAddressesReceivedCallback = null;
    protected NodeConnectedCallback _nodeConnectedCallback = null;
    protected NodeHandshakeCompleteCallback _nodeHandshakeCompleteCallback = null;
    protected NodeDisconnectedCallback _nodeDisconnectedCallback = null;

    protected <T, S> void _storeInMapSet(final Map<T, Set<S>> destinationMap, final T key, final S value) {
        Set<S> destinationSet = destinationMap.get(key);
        if (destinationSet == null) {
            destinationSet = new HashSet<S>();
            destinationMap.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected <T, S> void _storeInMapList(final Map<T, List<S>> destinationList, final T key, final S value) {
        List<S> destinationSet = destinationList.get(key);
        if (destinationSet == null) {
            destinationSet = new ArrayList<S>();
            destinationList.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected <U, T, S extends Callback<U>> void _executeAndClearCallbacks(final Map<T, Set<S>> callbackMap, final T key, final U value) {
        final Set<S> callbackSet = callbackMap.remove(key);
        if (callbackSet == null) { return; }

        for (final S callback : callbackSet) {
            callback.onResult(value);
        }
    }

    protected void _queueMessage(final ProtocolMessage message) {
        if (_handshakeIsComplete) {
            _connection.queueMessage(message);
        }
        else {
            _postHandshakeMessageQueue.add(message);
        }
    }

    @Override
    protected void _onConnect() {
        final SynchronizeVersionMessage synchronizeVersionMessage = new SynchronizeVersionMessage();
        { // Set Remote NodeIpAddress...
            final NodeIpAddress remoteNodeIpAddress = new NodeIpAddress();
            remoteNodeIpAddress.setIp(Ipv4.parse(_connection.getRemoteIp()));
            remoteNodeIpAddress.setPort(_connection.getPort());
            remoteNodeIpAddress.setNodeFeatures(new NodeFeatures());
            synchronizeVersionMessage.setRemoteAddress(remoteNodeIpAddress);
        }
        _connection.queueMessage(synchronizeVersionMessage);

        if (_nodeConnectedCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    final NodeConnectedCallback callback = _nodeConnectedCallback;
                    if (callback != null) {
                        callback.onNodeConnected();
                    }
                }
            })).start();
        }
    }

    @Override
    protected void _onDisconnect() {
        Logger.log("Socket disconnected.");

        if (_nodeDisconnectedCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    final NodeDisconnectedCallback callback = _nodeDisconnectedCallback;
                    if (callback != null) {
                        callback.onNodeDisconnected();
                    }
                }
            })).start();
        }
    }

    @Override
    protected void _onPingReceived(final PingMessage pingMessage) {
        final PongMessage pongMessage = new PongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
        _queueMessage(pongMessage);
    }

    @Override
    protected void _onPongReceived(final PongMessage pongMessage) {
        final Long nonce = pongMessage.getNonce();
        final PingRequest pingRequest = _pingRequests.remove(nonce);

        final PingCallback pingCallback = (pingRequest != null ? pingRequest.pingCallback : null);
        if (pingCallback != null) {
            final Long now = System.currentTimeMillis();
            final Long msElapsed = (now - pingRequest.timestamp);
            pingCallback.onResult(msElapsed);
        }
    }

    @Override
    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
        // TODO: Should probably not accept any node version...
        final AcknowledgeVersionMessage acknowledgeVersionMessage = new AcknowledgeVersionMessage();
        _nodeIpAddress = synchronizeVersionMessage.getLocalNodeIpAddress();
        _queueMessage(acknowledgeVersionMessage);
    }

    @Override
    protected void _onAcknowledgeVersionMessageReceived(final AcknowledgeVersionMessage acknowledgeVersionMessage) {
        _handshakeIsComplete = true;
        if (_nodeHandshakeCompleteCallback != null) {
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    final NodeHandshakeCompleteCallback callback = _nodeHandshakeCompleteCallback;
                    if (callback != null) {
                        callback.onHandshakeComplete();
                    }
                }
            })).start();
        }

        while (! _postHandshakeMessageQueue.isEmpty()) {
            _queueMessage(_postHandshakeMessageQueue.remove(0));
        }
    }

    @Override
    protected void _onNodeAddressesReceived(final NodeIpAddressMessage nodeIpAddressMessage) {
        for (final NodeIpAddress nodeIpAddress : nodeIpAddressMessage.getNodeIpAddresses()) {

            Logger.log("Network Address: "+ HexUtil.toHexString(nodeIpAddress.getBytesWithTimestamp()));

            if (_nodeAddressesReceivedCallback != null) {
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final NodeAddressesReceivedCallback callback = _nodeAddressesReceivedCallback;
                        if (callback != null) {
                            callback.onNewNodeAddress(nodeIpAddress);
                        }
                    }
                })).start();
            }
        }
    }

    @Override
    protected void _onErrorMessageReceived(final ErrorMessage errorMessage) {
        final ErrorMessage.RejectCode rejectCode = errorMessage.getRejectCode();
        Logger.log("RECEIVED ERROR:"+ rejectCode.getRejectMessageType().getValue() +" "+ HexUtil.toHexString(new byte[] { rejectCode.getCode() }) +" "+ errorMessage.getRejectDescription() +" "+ HexUtil.toHexString(errorMessage.getExtraData()));
    }

    @Override
    protected void _onQueryResponseMessageReceived(final QueryResponseMessage queryResponseMessage) {
        final Map<DataHashType, List<Sha256Hash>> dataHashesMap = new HashMap<DataHashType, List<Sha256Hash>>();

        final List<DataHash> dataHashes = queryResponseMessage.getDataHashes();
        for (final DataHash dataHash : dataHashes) {
            final DataHashType dataHashType = dataHash.getDataHashType();
            _storeInMapSet(_availableDataHashes, dataHashType, dataHash);
            _storeInMapList(dataHashesMap, dataHashType, dataHash.getObjectHash());
        }

        for (final DataHashType dataHashType : dataHashesMap.keySet()) {
            final List<Sha256Hash> objectHashes = dataHashesMap.get(dataHashType);
            if (objectHashes.isEmpty()) { continue; }

            {   // NOTE: Since the QueryResponseMessage is not tied to the QueryRequest for Blocks,
                //  so in order to tie the callback to the response, the first block within the response is requested.
                //  If the downloaded Block's previousBlockHash matchesByte the requestAfter BlockHash, then the response is
                //  assumed to be for that callback's request.

                if (dataHashType == DataHashType.BLOCK) {
                    final Sha256Hash blockHash = objectHashes.get(0);
                    _storeInMapSet(_downloadBlockRequests, blockHash, new DownloadBlockCallback() {
                        @Override
                        public void onResult(final Block block) {
                            final Set<BlockHashQueryCallback> blockHashQueryCallbackSet = _queryRequests.get(dataHashType);
                            if (blockHashQueryCallbackSet == null) { return; }

                            for (final BlockHashQueryCallback blockHashQueryCallback : Util.copySet(blockHashQueryCallbackSet)) {
                                if (block.getPreviousBlockHash().equals(blockHashQueryCallback.afterBlockHash)) {
                                    blockHashQueryCallbackSet.remove(blockHashQueryCallback);
                                    blockHashQueryCallback.onResult(objectHashes);
                                }
                            }
                        }
                    });
                    _requestBlock(blockHash); // TODO: Convert to _requestBlockHeader(blockHash);

                    continue;
                }
            }

            _executeAndClearCallbacks(_queryRequests, dataHashType, objectHashes);
        }
    }

    @Override
    protected void _onBlockMessageReceived(final BlockMessage blockMessage) {
        final Block block = blockMessage.getBlock();
        final Boolean blockHeaderIsValid = block.isValid();

        final Sha256Hash blockHash = block.getHash();
        _executeAndClearCallbacks(_downloadBlockRequests, blockHash, (blockHeaderIsValid ? block : null));
    }

    protected void _queryForBlockHashesAfter(final Sha256Hash blockHash) {
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        queryBlocksMessage.addBlockHeaderHash(blockHash);
        _queueMessage(queryBlocksMessage);
    }

    protected void _requestBlock(final Sha256Hash blockHash) {
        final RequestDataMessage requestDataMessage = new RequestDataMessage();
        requestDataMessage.addInventoryItem(new DataHash(DataHashType.BLOCK, blockHash));
        _queueMessage(requestDataMessage);
    }

    public Node(final String host, final Integer port) {
        super(host, port);
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }
    }

    public NodeId getId() { return _id; }

    public Boolean handshakeIsComplete() {
        return _handshakeIsComplete;
    }

    public String getConnectionString() {
        return (Util.coalesce(_connection.getRemoteIp(), _connection.getHost()) + ":" + _connection.getPort());
    }

    public NodeIpAddress getNodeAddress() {
        if (! _handshakeIsComplete) { return null; }
        if (_nodeIpAddress == null) { return null; }

        final NodeIpAddress nodeIpAddress = _nodeIpAddress.copy();

        final IpInflater ipInflater = new IpInflater();
        final Ip ip = ipInflater.fromString(_connection.getRemoteIp());
        if (ip != null) {
            nodeIpAddress.setIp(ip);
        }

        final Integer port = _connection.getPort();
        if (port != null) {
            nodeIpAddress.setPort(port);
        }

        return nodeIpAddress;
    }

    public void setNodeAddressesReceivedCallback(final NodeAddressesReceivedCallback nodeAddressesReceivedCallback) {
        _nodeAddressesReceivedCallback = nodeAddressesReceivedCallback;
    }

    public void setNodeConnectedCallback(final NodeConnectedCallback nodeConnectedCallback) {
        _nodeConnectedCallback = nodeConnectedCallback;
    }

    public void setNodeHandshakeCompleteCallback(final NodeHandshakeCompleteCallback nodeHandshakeCompleteCallback) {
        _nodeHandshakeCompleteCallback = nodeHandshakeCompleteCallback;
    }

    public void setNodeDisconnectedCallback(final NodeDisconnectedCallback nodeDisconnectedCallback) {
        _nodeDisconnectedCallback = nodeDisconnectedCallback;
    }

    public void requestBlockHashesAfter(final Sha256Hash blockHash, final QueryCallback queryCallback) {
        _storeInMapSet(_queryRequests, DataHashType.BLOCK, new BlockHashQueryCallback(blockHash, queryCallback));
        _queryForBlockHashesAfter(blockHash);
    }

    public void requestBlock(final Sha256Hash blockHash, final DownloadBlockCallback downloadBlockCallback) {
        _storeInMapSet(_downloadBlockRequests, blockHash, downloadBlockCallback);
        _requestBlock(blockHash);
    }

    public void ping(final PingCallback pingCallback) {
        final PingMessage pingMessage = new PingMessage();
        final PingRequest pingRequest = new PingRequest(pingCallback);
        _pingRequests.put(pingMessage.getNonce(), pingRequest);
        _queueMessage(pingMessage);
    }

    public void broadcastNodeAddress(final NodeIpAddress nodeIpAddress) {
        final NodeIpAddressMessage nodeIpAddressMessage = new NodeIpAddressMessage();
        nodeIpAddressMessage.addAddress(nodeIpAddress);
        _queueMessage(nodeIpAddressMessage);
    }

    public void broadcastNodeAddresses(final List<NodeIpAddress> nodeIpAddresses) {
        final NodeIpAddressMessage nodeIpAddressMessage = new NodeIpAddressMessage();
        for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
            nodeIpAddressMessage.addAddress(nodeIpAddress);
        }
        _queueMessage(nodeIpAddressMessage);
    }

    public void disconnect() {
        _nodeAddressesReceivedCallback = null;
        _nodeConnectedCallback = null;
        _nodeHandshakeCompleteCallback = null;
        _nodeDisconnectedCallback = null;

        _connection.disconnect();

        _handshakeIsComplete = false;
        _postHandshakeMessageQueue.clear();
        _availableDataHashes.clear();

        _queryRequests.clear();
        _downloadBlockRequests.clear();
        _pingRequests.clear();
    }
}

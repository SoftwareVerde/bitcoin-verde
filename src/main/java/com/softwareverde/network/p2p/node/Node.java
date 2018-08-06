package com.softwareverde.network.p2p.node;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.ip.IpInflater;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.*;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.*;

public abstract class Node {
    public interface NodeAddressesReceivedCallback { void onNewNodeAddress(NodeIpAddress nodeIpAddress); }
    public interface NodeConnectedCallback { void onNodeConnected();}
    public interface NodeHandshakeCompleteCallback { void onHandshakeComplete(); }
    public interface NodeDisconnectedCallback { void onNodeDisconnected(); }
    public interface PingCallback { void onResult(Long latency); }

    protected static class PingRequest {
        public final PingCallback pingCallback;
        public final Long timestamp;

        public PingRequest(final PingCallback pingCallback) {
            this.pingCallback = pingCallback;
            this.timestamp = System.currentTimeMillis();
        }
    }

    protected static final RotatingQueue<Long> LOCAL_SYNCHRONIZATION_NONCES = new RotatingQueue<Long>(32);

    private static final Object NODE_ID_MUTEX = new Object();
    private static Long _nextId = 0L;

    protected static <T, S> void _storeInMapSet(final Map<T, Set<S>> destinationMap, final T key, final S value) {
        Set<S> destinationSet = destinationMap.get(key);
        if (destinationSet == null) {
            destinationSet = new HashSet<S>();
            destinationMap.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected static <T, S> void _storeInMapList(final Map<T, MutableList<S>> destinationList, final T key, final S value) {
        MutableList<S> destinationSet = destinationList.get(key);
        if (destinationSet == null) {
            destinationSet = new MutableList<S>();
            destinationList.put(key, destinationSet);
        }
        destinationSet.add(value);
    }

    protected final NodeId _id;
    protected final NodeConnection _connection;
    protected final Long _initializationTime = System.currentTimeMillis();

    protected final SystemTime _systemTime = new SystemTime();

    protected NodeIpAddress _localNodeIpAddress = null;
    protected Boolean _handshakeHasBeenInvoked = false;
    protected Boolean _handshakeIsComplete = false;
    protected Long _lastMessageReceivedTimestamp = 0L;
    protected final LinkedList<ProtocolMessage> _postHandshakeMessageQueue = new LinkedList<ProtocolMessage>();
    protected Long _networkTimeOffset; // This field is an offset (in milliseconds) that should be added to the local time in order to adjust local SystemTime to this node's NetworkTime...

    protected final Map<Long, PingRequest> _pingRequests = new HashMap<Long, PingRequest>();

    protected NodeAddressesReceivedCallback _nodeAddressesReceivedCallback = null;
    protected NodeConnectedCallback _nodeConnectedCallback = null;
    protected NodeHandshakeCompleteCallback _nodeHandshakeCompleteCallback = null;
    protected NodeDisconnectedCallback _nodeDisconnectedCallback = null;

    protected final LinkedList<Runnable> _postConnectQueue = new LinkedList<Runnable>();

    protected abstract PingMessage _createPingMessage();
    protected abstract PongMessage _createPongMessage(final PingMessage pingMessage);
    protected abstract SynchronizeVersionMessage _createSynchronizeVersionMessage();
    protected abstract AcknowledgeVersionMessage _createAcknowledgeVersionMessage(SynchronizeVersionMessage synchronizeVersionMessage);
    protected abstract NodeIpAddressMessage _createNodeIpAddressMessage();

    protected void _queueMessage(final ProtocolMessage message) {
        if (_handshakeIsComplete) {
            _connection.queueMessage(message);
        }
        else {
            _postHandshakeMessageQueue.addLast(message);
        }
    }

    protected void _disconnect() {
        _nodeAddressesReceivedCallback = null;
        _nodeConnectedCallback = null;
        _nodeHandshakeCompleteCallback = null;
        _nodeDisconnectedCallback = null;

        _connection.disconnect();

        _handshakeIsComplete = false;
        _postHandshakeMessageQueue.clear();

        _pingRequests.clear();
    }

    /**
     * Creates a SynchronizeVersion message and enqueues it to the connection.
     *  NOTE: If the connection is not currently alive, this function will not be executed until it has been successfully
     *  connected, otherwise _createSynchronizeVersionMessage() cannot determine the correct remote address.
     */
    protected void _handshake() {
        if (! _handshakeHasBeenInvoked) {
            final Runnable createAndQueueHandshake = new Runnable() {
                @Override
                public void run() {
                    final SynchronizeVersionMessage synchronizeVersionMessage = _createSynchronizeVersionMessage();

                    final Long synchronizationNonce = synchronizeVersionMessage.getNonce();
                    synchronized (LOCAL_SYNCHRONIZATION_NONCES) {
                        LOCAL_SYNCHRONIZATION_NONCES.add(synchronizationNonce);
                    }

                    _connection.queueMessage(synchronizeVersionMessage);
                }
            };

            synchronized (_postConnectQueue) {
                if (_connection.isConnected()) {
                    createAndQueueHandshake.run();
                }
                else {
                    _postConnectQueue.addLast(createAndQueueHandshake);
                }
            }

            _handshakeHasBeenInvoked = true;
        }
    }

    protected void _onConnect() {
        _handshake();
        synchronized (_postConnectQueue) {
            while (! _postConnectQueue.isEmpty()) {
                final Runnable postConnectRunnable = _postConnectQueue.removeLast();
                postConnectRunnable.run();
            }
        }

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

    protected void _onPingReceived(final PingMessage pingMessage) {
        final PongMessage pongMessage = _createPongMessage(pingMessage);
        _queueMessage(pongMessage);
    }

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

    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
        // TODO: Should probably not accept any node version...

        { // Detect if the connection is to itself...
            final Long remoteNonce = synchronizeVersionMessage.getNonce();
            synchronized (LOCAL_SYNCHRONIZATION_NONCES) {
                for (final Long pastNonce : LOCAL_SYNCHRONIZATION_NONCES) {
                    if (Util.areEqual(pastNonce, remoteNonce)) {
                        Logger.log("Detected connection to self. Disconnecting.");
                        _disconnect();
                        return;
                    }
                }
            }
        }

        { // Calculate the node's network time offset...
            final Long currentTime = _systemTime.getCurrentTimeInSeconds();
            final Long nodeTime = synchronizeVersionMessage.getTimestamp();
            _networkTimeOffset = ((nodeTime - currentTime) * 1000L);
        }

        _localNodeIpAddress = synchronizeVersionMessage.getLocalNodeIpAddress();

        final AcknowledgeVersionMessage acknowledgeVersionMessage = _createAcknowledgeVersionMessage(synchronizeVersionMessage);
        _connection.queueMessage(acknowledgeVersionMessage);
    }

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
            _queueMessage(_postHandshakeMessageQueue.removeFirst());
        }
    }

    protected void _onNodeAddressesReceived(final NodeIpAddressMessage nodeIpAddressMessage) {
        for (final NodeIpAddress nodeIpAddress : nodeIpAddressMessage.getNodeIpAddresses()) {
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

    public Node(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _connection = new NodeConnection(host, port, binaryPacketFormat);
    }

    public Node(final BinarySocket binarySocket) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _connection = new NodeConnection(binarySocket);
    }

    public NodeId getId() { return _id; }

    public Long getInitializationTime() {
        return _initializationTime;
    }

    public void handshake() {
        _handshake();
    }

    public Boolean handshakeIsComplete() {
        return _handshakeIsComplete;
    }

    public Long getNetworkTimeOffset() {
        return _networkTimeOffset;
    }

    public Long getLastMessageReceivedTimestamp() {
        return _lastMessageReceivedTimestamp;
    }

    public Boolean hasActiveConnection() {
        return ( (_connection.isConnected()) && (_lastMessageReceivedTimestamp > 0) );
    }

    public String getConnectionString() {
        return (Util.coalesce(_connection.getRemoteIp(), _connection.getHost()) + ":" + _connection.getPort());
    }

    public NodeIpAddress getRemoteNodeIpAddress() {
        final String remoteAddress = Util.coalesce(_connection.getRemoteIp(), _connection.getHost());
        if (remoteAddress == null) { return null; }

        final IpInflater ipInflater = new IpInflater();
        final Ip ip = ipInflater.fromString(remoteAddress);
        if (ip == null) { return null; }

        return new NodeIpAddress(ip, _connection.getPort());
    }

    public NodeIpAddress getLocalNodeIpAddress() {
        if (! _handshakeIsComplete) { return null; }
        if (_localNodeIpAddress == null) { return null; }

        return _localNodeIpAddress.copy();
    }

    public void setNodeAddressesReceivedCallback(final NodeAddressesReceivedCallback nodeAddressesReceivedCallback) {
        _nodeAddressesReceivedCallback = nodeAddressesReceivedCallback;
    }

    public void setNodeConnectedCallback(final NodeConnectedCallback nodeConnectedCallback) {
        _nodeConnectedCallback = nodeConnectedCallback;

        if (_connection.isConnected()) {
            if (_nodeConnectedCallback != null) {
                _nodeConnectedCallback.onNodeConnected();
            }
        }
    }

    public void setNodeHandshakeCompleteCallback(final NodeHandshakeCompleteCallback nodeHandshakeCompleteCallback) {
        _nodeHandshakeCompleteCallback = nodeHandshakeCompleteCallback;
    }

    public void setNodeDisconnectedCallback(final NodeDisconnectedCallback nodeDisconnectedCallback) {
        _nodeDisconnectedCallback = nodeDisconnectedCallback;
    }

    public void ping(final PingCallback pingCallback) {
        final PingMessage pingMessage = _createPingMessage();
        final PingRequest pingRequest = new PingRequest(pingCallback);
        _pingRequests.put(pingMessage.getNonce(), pingRequest);
        _queueMessage(pingMessage);
    }

    public void broadcastNodeAddress(final NodeIpAddress nodeIpAddress) {
        final NodeIpAddressMessage nodeIpAddressMessage = _createNodeIpAddressMessage();
        nodeIpAddressMessage.addAddress(nodeIpAddress);
        _queueMessage(nodeIpAddressMessage);
    }

    public void broadcastNodeAddresses(final List<? extends NodeIpAddress> nodeIpAddresses) {
        final NodeIpAddressMessage nodeIpAddressMessage = _createNodeIpAddressMessage();
        for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
            nodeIpAddressMessage.addAddress(nodeIpAddress);
        }
        _queueMessage(nodeIpAddressMessage);
    }

    public void connect() {
        _connection.startConnectionThread();
    }

    public void disconnect() {
        _disconnect();
    }

    public Boolean isConnected() {
        return _connection.isConnected();
    }

    public String getHost() {
        return _connection.getHost();
    }

    public Integer getPort() {
        return _connection.getPort();
    }

    @Override
    public String toString() {
        return _connection.toString();
    }
}

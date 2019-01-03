package com.softwareverde.network.p2p.node;

import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.pool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.*;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class Node {
    public interface NodeAddressesReceivedCallback { void onNewNodeAddresses(List<NodeIpAddress> nodeIpAddress); }
    public interface NodeConnectedCallback { void onNodeConnected();}
    public interface NodeHandshakeCompleteCallback { void onHandshakeComplete(); }
    public interface NodeDisconnectedCallback { void onNodeDisconnected(); }
    public interface PingCallback { void onResult(Long latency); }

    protected static class PingRequest {
        public final PingCallback pingCallback;
        public final Long timestamp;

        public PingRequest(final PingCallback pingCallback, final Long currentTimeInMilliseconds) {
            this.pingCallback = pingCallback;
            this.timestamp = currentTimeInMilliseconds;
        }
    }

    protected static final RotatingQueue<Long> LOCAL_SYNCHRONIZATION_NONCES = new RotatingQueue<Long>(32);

    private static final Object NODE_ID_MUTEX = new Object();
    private static Long _nextId = 0L;

    protected final NodeId _id;
    protected final NodeConnection _connection;
    protected final Long _initializationTime;

    protected final SystemTime _systemTime;

    protected NodeIpAddress _localNodeIpAddress = null;
    protected Boolean _handshakeHasBeenInvoked = false;
    protected Boolean _handshakeIsComplete = false;
    protected Long _lastMessageReceivedTimestamp = 0L;
    protected final ConcurrentLinkedQueue<ProtocolMessage> _postHandshakeMessageQueue = new ConcurrentLinkedQueue<ProtocolMessage>();
    protected Long _networkTimeOffset; // This field is an offset (in milliseconds) that should be added to the local time in order to adjust local SystemTime to this node's NetworkTime...

    protected final ConcurrentHashMap<Long, PingRequest> _pingRequests = new ConcurrentHashMap<Long, PingRequest>();

    protected NodeAddressesReceivedCallback _nodeAddressesReceivedCallback = null;
    protected NodeConnectedCallback _nodeConnectedCallback = null;
    protected NodeHandshakeCompleteCallback _nodeHandshakeCompleteCallback = null;
    protected NodeDisconnectedCallback _nodeDisconnectedCallback = null;

    protected final ConcurrentLinkedQueue<Runnable> _postConnectQueue = new ConcurrentLinkedQueue<Runnable>();

    protected final ThreadPool _threadPool;

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
            _postHandshakeMessageQueue.offer(message);
        }
    }

    protected void _disconnect() {
        final NodeDisconnectedCallback nodeDisconnectedCallback = _nodeDisconnectedCallback;

        _nodeAddressesReceivedCallback = null;
        _nodeConnectedCallback = null;
        _nodeHandshakeCompleteCallback = null;
        _nodeDisconnectedCallback = null;

        _handshakeIsComplete = false;
        _postHandshakeMessageQueue.clear();

        _pingRequests.clear();

        if (_threadPool instanceof ThreadPoolThrottle) {
            ((ThreadPoolThrottle) _threadPool).stop();
        }

        _connection.setOnDisconnectCallback(null); // Intentionally avoid triggering the normal socket disconnect callback...
        _connection.disconnect();

        Logger.log("Socket disconnected. " + "(" + this.getConnectionString() + ")");

        if (nodeDisconnectedCallback != null) {
            // Intentionally not using the thread pool since it has been shutdown...
            (new Thread(new Runnable() {
                @Override
                public void run() {
                    nodeDisconnectedCallback.onNodeDisconnected();
                }
            })).start();
        }
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
                    _postConnectQueue.offer(createAndQueueHandshake);
                }
            }

            _handshakeHasBeenInvoked = true;
        }
    }

    protected void _onConnect() {
        _handshake();
        synchronized (_postConnectQueue) {
            Runnable postConnectRunnable;
            while ((postConnectRunnable = _postConnectQueue.poll()) != null) {
                postConnectRunnable.run();
            }
        }

        if (_nodeConnectedCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    final NodeConnectedCallback callback = _nodeConnectedCallback;
                    if (callback != null) {
                        callback.onNodeConnected();
                    }
                }
            });
        }
    }

    protected void _onPingReceived(final PingMessage pingMessage) {
        final PongMessage pongMessage = _createPongMessage(pingMessage);
        _queueMessage(pongMessage);
    }

    protected void _onPongReceived(final PongMessage pongMessage) {
        final Long nonce = pongMessage.getNonce();
        final PingRequest pingRequest = _pingRequests.remove(nonce);
        if (pingRequest == null) { return; }

        final PingCallback pingCallback = pingRequest.pingCallback;
        if (pingCallback != null) {
            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
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

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final NodeHandshakeCompleteCallback callback = _nodeHandshakeCompleteCallback;
                if (callback != null) {
                    callback.onHandshakeComplete();
                }
            }
        });

        ProtocolMessage protocolMessage;
        while ((protocolMessage = _postHandshakeMessageQueue.poll()) != null) {
            _queueMessage(protocolMessage);
        }
    }

    protected void _onNodeAddressesReceived(final NodeIpAddressMessage nodeIpAddressMessage) {
        final NodeAddressesReceivedCallback nodeAddressesReceivedCallback = _nodeAddressesReceivedCallback;
        final List<NodeIpAddress> nodeIpAddresses = nodeIpAddressMessage.getNodeIpAddresses();
        if (nodeIpAddresses.isEmpty()) { return; }

        if (nodeAddressesReceivedCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    nodeAddressesReceivedCallback.onNewNodeAddresses(nodeIpAddresses);
                }
            });
        }
    }

    public Node(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _systemTime = new SystemTime();
        _connection = new NodeConnection(host, port, binaryPacketFormat, threadPool);
        _initializationTime = _systemTime.getCurrentTimeInMilliSeconds();
        _threadPool = threadPool;
    }

    public Node(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat, final SystemTime systemTime, final ThreadPool threadPool) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _systemTime = systemTime;
        _connection = new NodeConnection(host, port, binaryPacketFormat, threadPool);
        _initializationTime = _systemTime.getCurrentTimeInMilliSeconds();
        _threadPool = threadPool;
    }

    public Node(final BinarySocket binarySocket, final ThreadPool threadPool) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _systemTime = new SystemTime();
        _connection = new NodeConnection(binarySocket, threadPool);
        _initializationTime = _systemTime.getCurrentTimeInMilliSeconds();
        _threadPool = threadPool;
    }

    public NodeId getId() { return _id; }

    public Long getInitializationTimestamp() {
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
        final Ip ip = _connection.getIp();
        return ((ip != null ? ip.toString() : _connection.getHost()) + ":" + _connection.getPort());
    }

    public NodeIpAddress getRemoteNodeIpAddress() {
        final Ip ip;
        {
            final Ip connectionIp = _connection.getIp();
            ip = (connectionIp != null ? connectionIp : Ip.fromString(_connection.getHost()));
        }

        if (ip == null) {
            return null;
        }

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

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final PingRequest pingRequest = new PingRequest(pingCallback, now);
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
        _connection.connect();
    }

    public void disconnect() {
        _disconnect();
    }

    public Boolean isConnected() {
        return _connection.isConnected();
    }

    /**
     * Attempts to look up the Node's host, or returns null if the lookup fails.
     */
    public String getHost() {
        return _connection.getHost();
    }

    public Ip getIp() {
        return _connection.getIp();
    }

    public Integer getPort() {
        return _connection.getPort();
    }

    @Override
    public String toString() {
        return _connection.toString();
    }
}

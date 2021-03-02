package com.softwareverde.network.p2p.node;

import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPoolThrottle;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.message.type.AcknowledgeVersionMessage;
import com.softwareverde.network.p2p.message.type.NodeIpAddressMessage;
import com.softwareverde.network.p2p.message.type.PingMessage;
import com.softwareverde.network.p2p.message.type.PongMessage;
import com.softwareverde.network.p2p.message.type.SynchronizeVersionMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.socket.BinaryPacketFormat;
import com.softwareverde.network.socket.BinarySocket;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public abstract class Node {
    public interface NodeAddressesReceivedCallback { void onNewNodeAddresses(List<NodeIpAddress> nodeIpAddress); }
    public interface NodeConnectedCallback { void onNodeConnected();}
    public interface HandshakeCompleteCallback { void onHandshakeComplete(); }
    public interface DisconnectedCallback { void onNodeDisconnected(); }
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
    protected final Boolean _isOutboundConnection;

    protected final SystemTime _systemTime;

    protected final Object _synchronizationMutex = new Object();
    protected volatile Boolean _acknowledgeVersionMessageReceived = false;
    protected volatile Boolean _acknowledgeVersionMessageSent = false;
    protected volatile Boolean _handshakeIsComplete = false;

    protected NodeIpAddress _localNodeIpAddress = null;
    protected final AtomicBoolean _handshakeHasBeenInvoked = new AtomicBoolean(false);
    protected final AtomicBoolean _synchronizeVersionMessageHasBeenSent = new AtomicBoolean(false);
    protected Long _lastMessageReceivedTimestamp = 0L;
    protected final ConcurrentLinkedQueue<ProtocolMessage> _postHandshakeMessageQueue = new ConcurrentLinkedQueue<ProtocolMessage>();
    protected Long _networkTimeOffset; // This field is an offset (in milliseconds) that should be added to the local time in order to adjust local SystemTime to this node's NetworkTime...
    protected final AtomicBoolean _hasBeenDisconnected = new AtomicBoolean(false);

    protected final ConcurrentHashMap<Long, PingRequest> _pingRequests = new ConcurrentHashMap<Long, PingRequest>();

    protected NodeAddressesReceivedCallback _nodeAddressesReceivedCallback = null;
    protected NodeConnectedCallback _nodeConnectedCallback = null;
    protected HandshakeCompleteCallback _handshakeCompleteCallback = null;
    protected DisconnectedCallback _nodeDisconnectedCallback = null;

    protected final ConcurrentLinkedQueue<Runnable> _postConnectQueue = new ConcurrentLinkedQueue<Runnable>();

    protected final ThreadPool _threadPool;

    /**
     * Latencies in milliseconds...
     */
    protected final CircleBuffer<Long> _latenciesMs = new CircleBuffer<Long>(32);

    protected abstract PingMessage _createPingMessage();
    protected abstract PongMessage _createPongMessage(final PingMessage pingMessage);
    protected abstract SynchronizeVersionMessage _createSynchronizeVersionMessage();
    protected abstract AcknowledgeVersionMessage _createAcknowledgeVersionMessage(SynchronizeVersionMessage synchronizeVersionMessage);
    protected abstract NodeIpAddressMessage _createNodeIpAddressMessage();

    protected final ReentrantReadWriteLock.ReadLock _sendSingleMessageLock;
    protected final ReentrantReadWriteLock.WriteLock _sendMultiMessageLock;



    protected void _writeQueuedPostHandshakeMessages() {
        try {
            _sendSingleMessageLock.lockInterruptibly();
        }
        catch (final InterruptedException exception) { return; }

        try {
            ProtocolMessage protocolMessage;
            while ((protocolMessage = _postHandshakeMessageQueue.poll()) != null) {
                _connection.queueMessage(protocolMessage);
            }
        }
        finally {
            _sendSingleMessageLock.unlock();
        }
    }

    protected void _queueMessage(final ProtocolMessage message) {
        try {
            _sendSingleMessageLock.lockInterruptibly();
        }
        catch (final InterruptedException exception) {
            Logger.trace("Dropped message due to interrupt: " + message.getClass());
            return;
        }

        try {
            if (_handshakeIsComplete) {
                _connection.queueMessage(message);
            }
            else {
                _postHandshakeMessageQueue.offer(message);
            }
        }
        finally {
            _sendSingleMessageLock.unlock();
        }
    }

    /**
     * Guarantees that the messages are queued in the order provided, and that no other messages are sent between them.
     */
    protected void _queueMessages(final List<? extends ProtocolMessage> messages) {
        try {
            _sendMultiMessageLock.lockInterruptibly();
        }
        catch (final InterruptedException exception) {
            // Log that a message was dropped due to interrupt...
            if (Logger.isTraceEnabled()) {
                for (final ProtocolMessage message : messages) {
                    Logger.trace("Dropped message due to interrupt: " + message.getClass());
                }
            }
            return;
        }

        try {
            if (_handshakeIsComplete) {
                for (final ProtocolMessage message : messages) {
                    _connection.queueMessage(message);
                }
            }
            else {
                for (final ProtocolMessage message : messages) {
                    _postHandshakeMessageQueue.offer(message);
                }
            }
        }
        finally {
            _sendMultiMessageLock.unlock();
        }
    }

    protected void _disconnect() {
        if (_hasBeenDisconnected.getAndSet(true)) { return; }

        Logger.debug("Socket disconnected. " + "(" + this.getConnectionString() + ")");

        final DisconnectedCallback nodeDisconnectedCallback = _nodeDisconnectedCallback;

        _nodeAddressesReceivedCallback = null;
        _nodeConnectedCallback = null;
        _handshakeCompleteCallback = null;
        _nodeDisconnectedCallback = null;

        _handshakeIsComplete = false;
        _acknowledgeVersionMessageReceived = false;
        _acknowledgeVersionMessageSent = false;
        _postHandshakeMessageQueue.clear();

        _pingRequests.clear();

        if (_threadPool instanceof ThreadPoolThrottle) {
            ((ThreadPoolThrottle) _threadPool).stop();
        }

        _connection.setOnDisconnectCallback(null); // Prevent any disconnect callbacks from repeating...
        _connection.cancelConnecting();
        _connection.disconnect();

        if (nodeDisconnectedCallback != null) {
            // Intentionally not using the thread pool since it has been shutdown...
            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    nodeDisconnectedCallback.onNodeDisconnected();
                }
            });
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.error("Uncaught exception in Thread.", exception);
                }
            });
            thread.start();
        }
    }

    /**
     * Creates a SynchronizeVersion message and enqueues it to the connection.
     *  NOTE: If the connection is not currently alive, this function will not be executed until it has been successfully
     *  connected, otherwise _createSynchronizeVersionMessage() cannot determine the correct remote address.
     */
    protected void _handshake() {
        if (! _handshakeHasBeenInvoked.getAndSet(true)) {
            final Runnable createAndQueueHandshake = new Runnable() {
                @Override
                public void run() {
                    final SynchronizeVersionMessage synchronizeVersionMessage = _createSynchronizeVersionMessage();

                    final Long synchronizationNonce = synchronizeVersionMessage.getNonce();
                    synchronized (LOCAL_SYNCHRONIZATION_NONCES) {
                        LOCAL_SYNCHRONIZATION_NONCES.add(synchronizationNonce);
                    }

                    _connection.queueMessage(synchronizeVersionMessage);

                    synchronized (_synchronizeVersionMessageHasBeenSent) {
                        _synchronizeVersionMessageHasBeenSent.set(true);
                        _synchronizeVersionMessageHasBeenSent.notifyAll();
                    }
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
        }
    }

    protected void _onHandshakeComplete() {
        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final HandshakeCompleteCallback callback = _handshakeCompleteCallback;
                if (callback != null) {
                    callback.onHandshakeComplete();
                }
            }
        });

        _writeQueuedPostHandshakeMessages();
        _handshakeIsComplete = true;
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

    protected void _ping(final PingCallback pingCallback) {
        final PingMessage pingMessage = _createPingMessage();

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final PingRequest pingRequest = new PingRequest(pingCallback, now);
        _pingRequests.put(pingMessage.getNonce(), pingRequest);
        _queueMessage(pingMessage);
    }

    protected void _onPingReceived(final PingMessage pingMessage) {
        final PongMessage pongMessage = _createPongMessage(pingMessage);
        _queueMessage(pongMessage);
    }

    protected Long _onPongReceived(final PongMessage pongMessage) {
        final Long nonce = pongMessage.getNonce();
        final PingRequest pingRequest = _pingRequests.remove(nonce);
        if (pingRequest == null) { return null; }

        final PingCallback pingCallback = pingRequest.pingCallback;

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final Long msElapsed = (now - pingRequest.timestamp);

        _latenciesMs.push(msElapsed);

        if (pingCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    pingCallback.onResult(msElapsed);
                }
            });
        }

        return msElapsed;
    }

    protected void _onSynchronizeVersion(final SynchronizeVersionMessage synchronizeVersionMessage) {
        // TODO: Should probably not accept any node version...

        { // Detect if the connection is to itself...
            final Long remoteNonce = synchronizeVersionMessage.getNonce();
            synchronized (LOCAL_SYNCHRONIZATION_NONCES) {
                for (final Long pastNonce : LOCAL_SYNCHRONIZATION_NONCES) {
                    if (Util.areEqual(pastNonce, remoteNonce)) {
                        Logger.info("Detected connection to self. Disconnecting.");
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

        _localNodeIpAddress = synchronizeVersionMessage.getRemoteNodeIpAddress();

        { // Ensure that this node sends its SynchronizeVersion message before the AcknowledgeVersionMessage is transmitted...
            // NOTE: Since  Node::handshake may have been invoked already, it's possible for a race condition between responding to
            //  the SynchronizeVersion here and the other call to handshake.  Therefore, _synchronizeVersionMessageHasBeenSent is
            //  waited on until actually queuing the AcknowledgeVersionMessage.

            _handshake();

            synchronized (_synchronizeVersionMessageHasBeenSent) {
                if (! _synchronizeVersionMessageHasBeenSent.get()) {
                    try { _synchronizeVersionMessageHasBeenSent.wait(10000L); } catch (final Exception exception) { }
                }
            }

            final AcknowledgeVersionMessage acknowledgeVersionMessage = _createAcknowledgeVersionMessage(synchronizeVersionMessage);
            _connection.queueMessage(acknowledgeVersionMessage);

            synchronized (_synchronizationMutex) {
                _acknowledgeVersionMessageSent = true;

                if (_acknowledgeVersionMessageReceived) {
                    _onHandshakeComplete();
                }
            }
        }
    }

    protected void _onAcknowledgeVersionMessageReceived(final AcknowledgeVersionMessage acknowledgeVersionMessage) {
        synchronized (_synchronizationMutex) {
            _acknowledgeVersionMessageReceived = true;

            if (_acknowledgeVersionMessageSent) {
                _onHandshakeComplete();
            }
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

    protected void _initConnection() {
        _connection.setOnConnectCallback(new Runnable() {
            @Override
            public void run() {
                _onConnect();
            }
        });

        _connection.setOnDisconnectCallback(new Runnable() {
            @Override
            public void run() {
                _disconnect();
            }
        });
    }

    protected Long _calculateAveragePingMs() {
        final int itemCount = _latenciesMs.getCount();
        long sum = 0L;
        long count = 0L;
        for (int i = 0; i < itemCount; ++i) {
            final Long value = _latenciesMs.get(i);
            if (value == null) { continue; }

            sum += value;
            count += 1L;
        }
        if (count == 0L) { return null; }

        return (sum / count);
    }

    public Node(final String host, final Integer port, final BinaryPacketFormat binaryPacketFormat, final ThreadPool threadPool) {
        this(host, port, binaryPacketFormat, new SystemTime(), threadPool);
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
        _isOutboundConnection = true;

        final ReentrantReadWriteLock queueMessageLock = new ReentrantReadWriteLock();
        _sendSingleMessageLock = queueMessageLock.readLock();
        _sendMultiMessageLock = queueMessageLock.writeLock();

        _initConnection();
    }

    public Node(final BinarySocket binarySocket, final ThreadPool threadPool) {
        this(binarySocket, threadPool, false);
    }

    public Node(final BinarySocket binarySocket, final ThreadPool threadPool, final Boolean isOutboundConnection) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _systemTime = new SystemTime();
        _connection = new NodeConnection(binarySocket, threadPool);
        _initializationTime = _systemTime.getCurrentTimeInMilliSeconds();
        _threadPool = threadPool;
        _isOutboundConnection = isOutboundConnection;

        final ReentrantReadWriteLock queueMessageLock = new ReentrantReadWriteLock();
        _sendSingleMessageLock = queueMessageLock.readLock();
        _sendMultiMessageLock = queueMessageLock.writeLock();

        _initConnection();
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

    /**
     * Returns a NodeIpAddress consisting of the Ip and Port for the connection.
     *  If the connection string was provided as a domain name, it will attempted to be resolved.
     *  If the domain cannot be resolved, then null is returned.
     */
    public NodeIpAddress getRemoteNodeIpAddress() {
        final Ip ip;
        {
            final Ip connectionIp = _connection.getIp();
            if (connectionIp != null) {
                ip = connectionIp;
            }
            else {
                final String hostName = _connection.getHost();
                final Ip ipFromString = Ip.fromString(hostName);
                if (ipFromString != null) {
                    ip = ipFromString;
                }
                else {
                    ip = Ip.fromHostName(hostName);
                }
            }
        }
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
            if (nodeConnectedCallback != null) {
                nodeConnectedCallback.onNodeConnected();
            }
        }
    }

    public void setHandshakeCompleteCallback(final HandshakeCompleteCallback handshakeCompleteCallback) {
        _handshakeCompleteCallback = handshakeCompleteCallback;
    }

    public void setDisconnectedCallback(final DisconnectedCallback nodeDisconnectedCallback) {
        _nodeDisconnectedCallback = nodeDisconnectedCallback;
    }

    public void setLocalNodeIpAddress(final NodeIpAddress nodeIpAddress) {
        _localNodeIpAddress = nodeIpAddress;
    }

    public void ping(final PingCallback pingCallback) {
        _ping(pingCallback);
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

    /**
     * NodeConnection::connect must be called, even if the underlying socket was already connected.
     */
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

    @Override
    public int hashCode() {
        return _id.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof Node)) { return false; }
        return Util.areEqual(_id, ((Node) object)._id);
    }

    /**
     * Returns the average ping (in milliseconds) for the node over the course of the last 32 pings.
     */
    public Long getAveragePing() {
        return _calculateAveragePingMs();
    }

    public Boolean isOutboundConnection() {
        return _isOutboundConnection;
    }

    public Long getTotalBytesReceivedCount() {
        return _connection.getTotalBytesReceivedCount();
    }

    public Long getTotalBytesSentCount() {
        return _connection.getTotalBytesSentCount();
    }
}

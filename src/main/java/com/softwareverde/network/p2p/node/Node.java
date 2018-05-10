package com.softwareverde.network.p2p.node;

import com.softwareverde.bitcoin.type.callback.Callback;
import com.softwareverde.io.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.ip.IpInflater;
import com.softwareverde.network.p2p.message.ProtocolMessage;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.util.Util;

import java.util.*;

public class Node {
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

    public interface PingCallback extends Callback<Long> { }

    protected static final Object NODE_ID_MUTEX = new Object();
    protected static Long _nextId = 0L;

    protected class PingRequest {
        public final PingCallback pingCallback;
        public final Long timestamp;

        public PingRequest(final PingCallback pingCallback) {
            this.pingCallback = pingCallback;
            this.timestamp = System.currentTimeMillis();
        }
    }

    protected Long _lastMessageReceivedTimestamp = 0L;

    public Long getLastMessageReceivedTimestamp() {
        return _lastMessageReceivedTimestamp;
    }

    public Boolean hasActiveConnection() {
        return ( (_connection.isConnected()) && (_lastMessageReceivedTimestamp > 0) );
    }

    protected final NodeId _id;
    protected final NodeConnection _connection;

    protected NodeIpAddress _nodeIpAddress = null;
    protected Boolean _handshakeIsComplete = false;
    protected final List<ProtocolMessage> _postHandshakeMessageQueue = new LinkedList<ProtocolMessage>();

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
        final PongMessage pongMessage = new PongMessage();
        pongMessage.setNonce(pingMessage.getNonce());
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
        final AcknowledgeVersionMessage acknowledgeVersionMessage = new AcknowledgeVersionMessage();
        _nodeIpAddress = synchronizeVersionMessage.getLocalNodeIpAddress();
        _queueMessage(acknowledgeVersionMessage);
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
            _queueMessage(_postHandshakeMessageQueue.remove(0));
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

    public Node(final String host, final Integer port) {
        synchronized (NODE_ID_MUTEX) {
            _id = NodeId.wrap(_nextId);
            _nextId += 1;
        }

        _connection = new NodeConnection(host, port);
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

        _pingRequests.clear();
    }
}

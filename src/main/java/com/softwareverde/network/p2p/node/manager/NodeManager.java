package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeFactory;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.p2p.node.manager.health.MutableNodeHealth;
import com.softwareverde.network.p2p.node.manager.health.NodeHealth;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NodeManager<NODE extends Node> {
    public static Boolean LOGGING_ENABLED = true;

    protected static ThreadPool _threadPool;

    public interface NodeFilter<NODE> {
        Boolean meetsCriteria(NODE node);
    }

    public interface NodeApiTransmission { }

    /**
     * A NodeJsonRpcConnection invocation that does not invoke a response.
     */
    public interface NodeApiMessage<NODE> extends NodeApiTransmission {
        void run(NODE node);
    }

    /**
     * NodeApiRequest.run() should invoke an Api Call on the provided Node.
     *  It is required that within the NodeApiRequestCallback, NodeApiRequestCallback::didTimeout is invoked immediately.
     *  NodeApiRequestCallback::didTimeout cancels the retry-thread timeout and returns true if the request has already timed out.
     *  If nodeApiInvocationCallback.didTimeout() returns true, then the the NodeApiRequestCallback should abort.
     */
    public static abstract class NodeApiRequest<NODE> implements NodeApiMessage<NODE> {
        private MutableNodeHealth.Request nodeHealthRequest;
        public Boolean didTimeout = false;
        public abstract void onFailure();
    }

    protected class NodeMaintenanceThread extends Thread {
        public NodeMaintenanceThread() {
            this.setName("Node Manager - Maintenance Thread - " + this.getId());
        }

        @Override
        public void run() {
            while (true) {
                synchronized (_mutex) {
                    _pingIdleNodes();
                }

                synchronized (_mutex) {
                    _removeDisconnectedNodes();
                }

                try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
            }

            if (LOGGING_ENABLED) {
                Logger.log("Node Maintenance Thread exiting...");
            }
        }
    }

    protected final Object _mutex = new Object();
    protected final SystemTime _systemTime;
    protected final NodeFactory<NODE> _nodeFactory;
    protected final Map<NodeId, NODE> _nodes;
    protected Map<NodeId, NODE> _pendingNodes = new HashMap<NodeId, NODE>(); // Nodes that have been added but have not yet completed their handshake...
    protected final ConcurrentHashMap<NodeId, MutableNodeHealth> _nodeHealthMap;
    protected final MutableList<NodeApiMessage<NODE>> _queuedTransmissions = new MutableList<NodeApiMessage<NODE>>();
    protected final PendingRequestsManager<NODE> _pendingRequestsManager;
    protected final ConcurrentHashSet<NodeIpAddress> _nodeAddresses = new ConcurrentHashSet<NodeIpAddress>();
    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();
    protected final Integer _maxNodeCount;
    protected final MutableNetworkTime _networkTime;
    protected Boolean _isShuttingDown = false;

    protected ConcurrentHashSet<NodeIpAddress> _newNodeAddresses = new ConcurrentHashSet<NodeIpAddress>();
    protected Long _lastAddressBroadcastTimestamp = 0L;

    protected void _onAllNodesDisconnected() { }
    protected void _onNodeHandshakeComplete(final NODE node) { }
    protected void _onNodeConnected(final NODE node) { }

    protected void _addHandshakedNode(final NODE node) {
        final NodeId newNodeId = node.getId();
        _nodes.put(newNodeId, node);
        _nodeHealthMap.put(newNodeId, new MutableNodeHealth(newNodeId, _systemTime));
    }

    protected void _addNotHandshakedNode(final NODE node) {
        final Long nowInMilliseconds = _systemTime.getCurrentTimeInMilliSeconds();

        { // Cleanup any pending nodes that still haven't completed their handshake...
            final Map<NodeId, NODE> pendingNodes = _pendingNodes;
            _pendingNodes = new HashMap<NodeId, NODE>(pendingNodes);

            for (final NODE oldPendingNode : pendingNodes.values()) {
                final Long pendingSinceTimeMilliseconds = oldPendingNode.getInitializationTimestamp();
                if (nowInMilliseconds - pendingSinceTimeMilliseconds < 30000L) {
                    _pendingNodes.put(oldPendingNode.getId(), oldPendingNode);
                }
                else {
                    oldPendingNode.disconnect();
                }
            }
        }

        _pendingNodes.put(node.getId(), node);
    }

    protected void _removeNode(final NODE node) {
        final NodeId nodeId = node.getId();

        _nodes.remove(nodeId);

        if (LOGGING_ENABLED) {
            final MutableNodeHealth nodeHealth = _nodeHealthMap.remove(nodeId);
            Logger.log("P2P: Dropped Node: " + node.getConnectionString() + " - " + (nodeHealth != null ? nodeHealth.getHealth() : "??? ") +  "hp");
        }

        node.disconnect();

        if (_nodes.isEmpty()) {
            if (! _isShuttingDown) {
                _onAllNodesDisconnected();
            }
        }
    }

    // NOTE: Requires Mutex lock...
    protected void _checkMaxNodeCount(final Integer maxNodeCount) {
        if (maxNodeCount > 0) {
            while (_nodes.size() > maxNodeCount) {
                final List<NODE> inactiveNodes = _getInactiveNodes();
                if (inactiveNodes.getSize() > 0) {
                    final NODE inactiveNode = inactiveNodes.get(0);
                    _removeNode(inactiveNode);
                    continue;
                }

                final NODE worstActiveNode = _selectWorstActiveNode();
                if (worstActiveNode != null) {
                    _removeNode(worstActiveNode);
                    continue;
                }

                final Set<NodeId> keySet = _nodes.keySet();
                final NodeId firstKey = keySet.iterator().next();
                final NODE node = _nodes.get(firstKey);
                _removeNode(node);
            }
        }
    }

    // NOTE: Requires Mutex Lock...
    protected void _broadcastNewNodesToExistingNodes(final List<NodeIpAddress> nodeIpAddresses) {
        for (final NODE node : _nodes.values()) {
            node.broadcastNodeAddresses(nodeIpAddresses);

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting " + nodeIpAddresses.getSize() + " new Nodes to existing Node (" + node + ")");
            }
        }
    }

    // NOTE: Requires Mutex Lock...
    protected void _broadcastExistingNodesToNewNode(final NODE newNode) {
        final Collection<NODE> nodes = _nodes.values();

        final MutableList<NodeIpAddress> nodeAddresses = new MutableList<NodeIpAddress>(nodes.size());
        for (final NODE node : nodes) {
            final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            nodeAddresses.add(nodeIpAddress);

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting Existing Node (" + nodeIpAddress + ") to New Node (" + newNode + ")");
            }
        }

        newNode.broadcastNodeAddresses(nodeAddresses);
    }

    protected void _onNodeDisconnected(final NODE node) {
        synchronized (_mutex) {
            if (LOGGING_ENABLED) {
                Logger.log("P2P: Node Disconnected: " + node.getConnectionString());
            }

            _removeNode(node);
        }
    }

    protected void _processQueuedMessages() {
        synchronized (_mutex) {
            // Copy the list of queued transactions since _selectNodeForRequest and _sendMessage could potentially requeue the transmission...
            final List<NodeApiMessage<NODE>> queuedTransmissions = new ImmutableList<NodeApiMessage<NODE>>(_queuedTransmissions);
            _queuedTransmissions.clear();

            for (final NodeApiMessage<NODE> apiTransmission : queuedTransmissions) {
                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (apiTransmission instanceof NodeApiRequest) {
                            _selectNodeForRequest((NodeApiRequest<NODE>) apiTransmission);
                        }
                        else {
                            _sendMessage(apiTransmission);
                        }
                    }
                });
            }
        }
    }

    protected Boolean _isConnectedToNode(final NodeIpAddress nodeIpAddress) {
        for (final NODE existingNode : _nodes.values()) {
            final NodeIpAddress existingNodeIpAddress = existingNode.getRemoteNodeIpAddress();

            final Boolean isAlreadyConnectedToNode = (Util.areEqual(nodeIpAddress, existingNodeIpAddress));
            if (isAlreadyConnectedToNode) { return true; }
        }

        return false;
    }

    protected void _initNode(final NODE node) {
        final Container<Boolean> nodeConnected = new Container<Boolean>(null);

        final Object lock = new Object();

        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (lock) {
                        lock.wait(10000L);
                    }

                    synchronized (nodeConnected) {
                        if (nodeConnected.value != null) { return; }

                        nodeConnected.value = false;

                        if (LOGGING_ENABLED) {
                            Logger.log("P2P: Node failed to connect. Purging node.");
                        }

                        synchronized (_mutex) {
                            _pendingNodes.remove(node.getId());

                            node.disconnect();

                            if (LOGGING_ENABLED) {
                                Logger.log("P2P: Node purged.");
                            }

                            if (_nodes.isEmpty()) {
                                if (!_isShuttingDown) {
                                    _onAllNodesDisconnected();
                                }
                            }
                        }
                    }
                }
                catch (final Exception exception) { }
            }

            @Override
            public String toString() {
                return (NodeManager.this.getClass().getCanonicalName() + "." + "TimeoutRunnable"); // Used a hack for FakeThreadPools...
            }
        };

        node.setNodeAddressesReceivedCallback(new NODE.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddresses(final List<NodeIpAddress> nodeIpAddresses) {
                if (_isShuttingDown) { return; }

                final List<NodeIpAddress> unseenNodeAddresses;
                {
                    final ImmutableListBuilder<NodeIpAddress> listBuilder = new ImmutableListBuilder<NodeIpAddress>(nodeIpAddresses.getSize());
                    for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                        final Boolean haveAlreadySeenNode = _nodeAddresses.contains(nodeIpAddress);
                        if (haveAlreadySeenNode) { continue; }

                        listBuilder.add(nodeIpAddress);
                        _nodeAddresses.add(nodeIpAddress);
                        _newNodeAddresses.add(nodeIpAddress);
                    }
                    unseenNodeAddresses = listBuilder.build();
                }
                if (unseenNodeAddresses.isEmpty()) { return; }

                synchronized (_mutex) {
                    if (_isShuttingDown) { return; }

                    { // Batch at least 30 seconds worth of new NodeIpAddresses, then broadcast the group to current peers...
                        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
                        final Long msElapsedSinceLastBroadcast = (now - _lastAddressBroadcastTimestamp);
                        if (msElapsedSinceLastBroadcast >= 30000L) {
                            _lastAddressBroadcastTimestamp = now;

                            final List<NodeIpAddress> newNodeAddresses = new MutableList<NodeIpAddress>(_newNodeAddresses);
                            _newNodeAddresses = new ConcurrentHashSet<NodeIpAddress>();
                            _broadcastNewNodesToExistingNodes(newNodeAddresses);
                        }
                    }

                    // Connect to the node if the node if the NodeManager is still looking for peers...
                    for (final NodeIpAddress nodeIpAddress : unseenNodeAddresses) {
                        final Integer healthyNodeCount = _countNodesAboveHealth(50);
                        if (healthyNodeCount >= _maxNodeCount) { break; }

                        final String address = nodeIpAddress.getIp().toString();
                        final Integer port = nodeIpAddress.getPort();

                        final Boolean isAlreadyConnectedToNode = _isConnectedToNode(nodeIpAddress);
                        if (isAlreadyConnectedToNode) { continue; }

                        final NODE newNode = _nodeFactory.newNode(address, port);

                        _initNode(newNode);

                        _broadcastExistingNodesToNewNode(newNode);

                        _checkMaxNodeCount(_maxNodeCount - 1);

                        _addNotHandshakedNode(newNode);
                    }
                }
            }
        });

        node.setNodeConnectedCallback(new NODE.NodeConnectedCallback() {
            @Override
            public void onNodeConnected() {
                { // Handle connection timeout...
                    if (nodeConnected.value == null) {
                        synchronized (nodeConnected) {
                            if (nodeConnected.value == null) {
                                nodeConnected.value = true;
                            }
                        }

                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                }

                _onNodeConnected(node);

                if (! node.hasActiveConnection()) { return; }

                synchronized (_mutex) {
                    _processQueuedMessages();
                }
            }
        });

        node.setNodeHandshakeCompleteCallback(new NODE.NodeHandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                if (LOGGING_ENABLED) {
                    Logger.log("P2P: HandshakeComplete: " + node.getConnectionString());
                }

                synchronized (_mutex) {
                    _pendingNodes.remove(node.getId());
                    _addHandshakedNode(node);
                }

                final Long nodeNetworkTimeOffset = node.getNetworkTimeOffset();
                if (nodeNetworkTimeOffset != null) {
                    _networkTime.includeOffsetInSeconds(nodeNetworkTimeOffset);
                }

                _onNodeHandshakeComplete(node);

                synchronized (_mutex) {
                    _processQueuedMessages();
                }
            }
        });

        node.setNodeDisconnectedCallback(new NODE.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                _onNodeDisconnected(node);
            }
        });

        _threadPool.execute(timeoutRunnable);

        node.connect();
        node.handshake();
    }

    // NOTE: Requires Mutex Lock...
    protected Integer _countNodesAboveHealth(final Integer minimumHealth) {
        int nodeCount = 0;
        final List<NODE> activeNodes = _getActiveNodes();
        for (final NODE node : activeNodes) {
            final MutableNodeHealth nodeHealth = _nodeHealthMap.get(node.getId());
            if (nodeHealth.getHealth() > minimumHealth) {
                nodeCount += 1;
            }
        }
        return nodeCount;
    }

    // NOTE: Requires Mutex Lock...
    protected List<NODE> _getInactiveNodes() {
        final MutableList<NODE> inactiveNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (! node.hasActiveConnection()) {
                inactiveNodes.add(node);
            }
        }
        return inactiveNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected List<NODE> _getActiveNodes() {
        final MutableList<NODE> activeNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (node.hasActiveConnection()) {
                activeNodes.add(node);
            }
        }

        return activeNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected NODE _selectWorstActiveNode() {
        final List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.getSize();
        if (activeNodeCount == 0) { return null; }

        final MutableList<NodeHealth> nodeHealthList = new MutableList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final MutableNodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth.asConst());
        }
        nodeHealthList.sort(MutableNodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(0);
        return _nodes.get(bestNodeHealth.getNodeId());
    }

    // NOTE: Requires Mutex Lock...
    protected NODE _selectBestNode() {
        final List<NODE> nodes = _selectBestNodes(1);
        if ( (nodes == null) || (nodes.isEmpty()) ) { return null; }

        final NODE selectedNode = nodes.get(0);

        if (LOGGING_ENABLED) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(selectedNode.getId());
            Logger.log("P2P: Selected Node: " + (selectedNode.getId()) + " (" + (nodeHealth != null ? nodeHealth.getHealth() : "??? ") + "hp) - " + (selectedNode.getConnectionString()) + " - " + _nodes.size());
        }

        return selectedNode;
    }

    // NOTE: Requires Mutex Lock...
    protected NODE _selectBestNode(final NodeFilter<NODE> nodeFilter) {
        final List<NODE> nodes = _selectBestNodes(_maxNodeCount);
        if ( (nodes == null) || (nodes.isEmpty()) ) { return null; }

        for (final NODE node : nodes) {
            if (! nodeFilter.meetsCriteria(node)) { continue; }

            if (LOGGING_ENABLED) {
                final NodeHealth nodeHealth = _nodeHealthMap.get(node.getId());
                Logger.log("P2P: Selected Node: " + (node.getId()) + " (" + (nodeHealth != null ? nodeHealth.getHealth() : "??? ") + "hp) - " + (node.getConnectionString()) + " - " + _nodes.size());
            }

            return node;
        }

        return null;
    }

    // NOTE: Requires Mutex Lock...
    protected List<NODE> _selectBestNodes(final Integer requestedNodeCount) {
        final List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.getSize();
        if (activeNodeCount == 0) { return null; }

        final MutableList<NodeHealth> nodeHealthList = new MutableList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth.asConst()); // NOTE: Items must be a snapshot to prevent concurrent modifications during sort...
        }
        nodeHealthList.sort(NodeHealth.COMPARATOR);

        final Integer nodeCount;
        {
            if ( (requestedNodeCount >= _nodes.size()) || (requestedNodeCount < 0) ) {
                nodeCount = _nodes.size();
            }
            else {
                nodeCount = requestedNodeCount;
            }
        }

        final MutableList<NODE> selectedNodes = new MutableList<NODE>(nodeCount);
        for (int i = 0; i < nodeCount; ++i) {
            final NodeHealth bestNodeHealth = nodeHealthList.get(nodeHealthList.getSize() - i - 1);
            final NODE selectedNode = _nodes.get(bestNodeHealth.getNodeId());

            selectedNodes.add(selectedNode);
        }

        return selectedNodes;
    }

    // NOTE: Requires Mutex lock...
    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = _systemTime.getCurrentTimeInMilliSeconds();

        final MutableList<NODE> idleNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
            final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

            if (idleDuration > maxIdleTime) {
                idleNodes.add(node);
            }
        }

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Idle Node Count: " + idleNodes.getSize() + " / " + _nodes.size());
        }

        for (final NODE idleNode : idleNodes) {
            // final NodeId nodeId = idleNode.getId();
            // _nodeHealthMap.get(nodeId).onRequestSent();

            if (! idleNode.handshakeIsComplete()) { return; }

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Pinging Idle Node: " + idleNode.getConnectionString());
            }

            idleNode.ping(new NODE.PingCallback() {
                @Override
                public void onResult(final Long pingInMilliseconds) {
                    if (LOGGING_ENABLED) {
                        Logger.log("P2P: Node Pong: " + pingInMilliseconds);
                    }

                    final NodeId nodeId = idleNode.getId();
                    final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
                    nodeHealth.updatePingInMilliseconds(pingInMilliseconds);
                }
            });
        }
    }

    // NOTE: Requires Mutex lock...
    protected void _removeDisconnectedNodes() {
        final MutableList<NODE> purgeableNodes = new MutableList<NODE>();

        for (final NODE node : _nodes.values()) {
            if (! node.isConnected()) {
                final Long nodeAge = (_systemTime.getCurrentTimeInMilliSeconds() - node.getInitializationTimestamp());
                if (nodeAge > 10000L) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final NODE node : purgeableNodes) {
            _removeNode(node);
        }
    }

    public NodeManager(final Integer maxNodeCount, final NodeFactory<NODE> nodeFactory, final MutableNetworkTime networkTime, final ThreadPool threadPool) {
        _systemTime = new SystemTime();
        _nodes = new HashMap<NodeId, NODE>(maxNodeCount);
        _nodeHealthMap = new ConcurrentHashMap<NodeId, MutableNodeHealth>(maxNodeCount);

        _maxNodeCount = maxNodeCount;
        _nodeFactory = nodeFactory;
        _networkTime = networkTime;
        _pendingRequestsManager = new PendingRequestsManager<NODE>(_systemTime, threadPool);
        _threadPool = threadPool;
    }

    public NodeManager(final Integer maxNodeCount, final NodeFactory<NODE> nodeFactory, final MutableNetworkTime networkTime, final SystemTime systemTime, final ThreadPool threadPool) {
        _nodes = new HashMap<NodeId, NODE>(maxNodeCount);
        _nodeHealthMap = new ConcurrentHashMap<NodeId, MutableNodeHealth>(maxNodeCount);

        _maxNodeCount = maxNodeCount;
        _nodeFactory = nodeFactory;
        _networkTime = networkTime;
        _systemTime = systemTime;
        _pendingRequestsManager = new PendingRequestsManager<NODE>(_systemTime, threadPool);
        _threadPool = threadPool;
    }

    public void addNode(final NODE node) {

        synchronized (_mutex) {
            if (_isShuttingDown) { return; }

            _initNode(node);

            _checkMaxNodeCount(_maxNodeCount - 1);
            _addNotHandshakedNode(node);
        }
    }

    public NetworkTime getNetworkTime() {
        return _networkTime;
    }

    public void startNodeMaintenanceThread() {
        _nodeMaintenanceThread.start();
    }

    public void stopNodeMaintenanceThread() {
        _nodeMaintenanceThread.interrupt();
        try { _nodeMaintenanceThread.join(); } catch (final Exception exception) { }
    }

    protected void _selectNodeForRequest(final NodeApiRequest<NODE> apiRequest) {
        final NODE selectedNode;
        final MutableNodeHealth nodeHealth;
        {
            synchronized (_mutex) {
                selectedNode = _selectBestNode();

                if (selectedNode == null) {
                    _queuedTransmissions.add(apiRequest);
                    return;
                }

                final NodeId nodeId = selectedNode.getId();
                nodeHealth = _nodeHealthMap.get(nodeId);
            }
        }

        apiRequest.nodeHealthRequest = nodeHealth.onRequestSent();
        _pendingRequestsManager.addPendingRequest(apiRequest);
        apiRequest.run(selectedNode);

        _pendingRequestsManager.wakeUp();
    }

    protected void _selectNodeForRequest(final NODE selectedNode, final NodeApiRequest<NODE> apiRequest) {
        final MutableNodeHealth nodeHealth;
        {
            synchronized (_mutex) {
                if (selectedNode == null) {
                    _queuedTransmissions.add(apiRequest);
                    return;
                }

                final NodeId nodeId = selectedNode.getId();
                nodeHealth = _nodeHealthMap.get(nodeId);
            }
        }

        if (nodeHealth == null) {
            Logger.log("Selected node no longer connected: " + selectedNode.getConnectionString());
            apiRequest.onFailure();
            return;
        }

        apiRequest.nodeHealthRequest = nodeHealth.onRequestSent();
        _pendingRequestsManager.addPendingRequest(apiRequest);
        apiRequest.run(selectedNode);

        _pendingRequestsManager.wakeUp();
    }

    protected void _onResponseReceived(final NODE selectedNode, final NodeApiRequest<NODE> apiRequest) {
        _pendingRequestsManager.removePendingRequest(apiRequest);
        final NodeId nodeId = selectedNode.getId();
        final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        if (nodeHealth != null) {
            nodeHealth.onResponseReceived(apiRequest.nodeHealthRequest);
        }
    }

    protected void _sendMessage(final NodeApiMessage<NODE> apiMessage) {
        final NODE selectedNode;
        final MutableNodeHealth nodeHealth;
        synchronized (_mutex) {
            selectedNode = _selectBestNode();

            if (selectedNode == null) {
                _queuedTransmissions.add(apiMessage);
                return;
            }

            final NodeId nodeId = selectedNode.getId();
            nodeHealth = _nodeHealthMap.get(nodeId);
        }

        nodeHealth.onMessageSent();
        apiMessage.run(selectedNode);
    }

    public void executeRequest(final NodeApiRequest<NODE> nodeNodeApiRequest) {
        synchronized (_mutex) {
            _selectNodeForRequest(nodeNodeApiRequest);
        }
    }

    public void sendMessage(final NodeApiMessage<NODE> nodeNodeApiMessage) {
        _sendMessage(nodeNodeApiMessage);
    }

    public List<NODE> getNodes() {
        synchronized (_mutex) {
            return new MutableList<NODE>(_nodes.values());
        }
    }

    public List<NodeId> getNodeIds() {
        synchronized (_mutex) {
            final ImmutableListBuilder<NodeId> nodeIds = new ImmutableListBuilder<NodeId>(_nodes.size());
            nodeIds.addAll(_nodes.keySet());
            return nodeIds.build();
        }
    }

    public NODE getNode(final NodeId nodeId) {
        synchronized (_mutex) {
            return _nodes.get(nodeId);
        }
    }

    public Long getNodeHealth(final NodeId nodeId) {
        final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        if (nodeHealth == null) { return null; }

        return nodeHealth.getHealth();
    }

    public NODE getBestNode() {
        synchronized (_mutex) {
            return _selectBestNode();
        }
    }

    public List<NODE> getBestNodes(final Integer nodeCount) {
        synchronized (_mutex) {
            return _selectBestNodes(nodeCount);
        }
    }

    public NODE getWorstNode() {
        synchronized (_mutex) {
            return _selectWorstActiveNode();
        }
    }

    public Integer getActiveNodeCount() {
        synchronized (_mutex) {
            final List<NODE> nodes = _getActiveNodes();
            return nodes.getSize();
        }
    }

    public void shutdown() {
        final MutableList<NODE> nodes;
        synchronized (_mutex) {
            _isShuttingDown = true;
            nodes = new MutableList<NODE>(_nodes.values());
            nodes.addAll(_pendingNodes.values());
            _nodes.clear();
            _pendingNodes.clear();
        }

        // Nodes must be disconnected outside of the _mutex lock in order to prevent deadlock...
        for (final NODE node : nodes) {
            node.disconnect();
        }

        _pendingRequestsManager.stop();
    }
}

package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
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
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeManager<NODE extends Node> {
    public static final Long PING_AFTER_MS_IDLE = 5L * 60000L; // 5 Minutes

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
                _pingIdleNodes();
                _removeDisconnectedNodes();
                _removeHighLatencyNodes();

                try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
            }

            Logger.debug("Node Maintenance Thread exiting...");
        }
    }

    // protected final Object _mutex = new Object();
    protected final SystemTime _systemTime;
    protected final NodeFactory<NODE> _nodeFactory;

    protected final ConcurrentHashMap<NodeId, NODE> _nodes;
    protected ConcurrentHashMap<NodeId, NODE> _pendingNodes = new ConcurrentHashMap<NodeId, NODE>(); // Nodes that have been added but have not yet completed their handshake.

    // _connectedNodeAddresses contains NodeIpAddresses that are either currently-connected, are pending handshake, or are about to be connected to.
    // All methods about to connect to a node should ensure the node will not be a duplicate by checking _connectedNodeAddresses for an existing entry.
    protected final ConcurrentHashSet<NodeIpAddress> _connectedNodeAddresses = new ConcurrentHashSet<NodeIpAddress>();

    protected final ConcurrentHashSet<NodeIpAddress> _seedNodes = new ConcurrentHashSet<NodeIpAddress>();
    protected final ConcurrentHashMap<NodeId, MutableNodeHealth> _nodeHealthMap;
    protected final ConcurrentLinkedQueue<NodeApiMessage<NODE>> _queuedTransmissions = new ConcurrentLinkedQueue<NodeApiMessage<NODE>>();
    protected final PendingRequestsManager<NODE> _pendingRequestsManager;
    protected final ConcurrentHashSet<NodeIpAddress> _nodeAddresses = new ConcurrentHashSet<NodeIpAddress>(); // The list of all node addresses advertised by peers.
    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();
    protected final Integer _maxNodeCount;
    protected final MutableNetworkTime _networkTime;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;
    protected Boolean _isShuttingDown = false;

    protected Integer _defaultExternalPort = BitcoinProperties.PORT;
    protected NodeIpAddress _localNodeIpAddress = null;

    protected ConcurrentHashSet<NodeIpAddress> _newNodeAddresses = new ConcurrentHashSet<NodeIpAddress>(); // The current batch of new addresses advertised by peers that have not yet been seen.
    protected Long _lastAddressBroadcastTimestamp = 0L;

    protected void _onAllNodesDisconnected() { }
    protected void _onNodeHandshakeComplete(final NODE node) { }
    protected void _onNodeConnected(final NODE node) { }

    protected void _recalculateLocalNodeIpAddress() {
        final HashMap<Ip, Integer> nodeIpAddressCounts = new HashMap<Ip, Integer>(_maxNodeCount / 2);
        final HashMap<Integer, Integer> nodePortCounts = new HashMap<Integer, Integer>(_maxNodeCount / 2);
        for (final NODE node : _nodes.values()) {
            final NodeIpAddress nodeIpAddress = node.getLocalNodeIpAddress();
            if (nodeIpAddress != null) {
                { // Ip Counts
                    final Ip ip = nodeIpAddress.getIp();
                    final Integer count = Util.coalesce(nodeIpAddressCounts.get(ip));
                    nodeIpAddressCounts.put(ip, (count + 1));
                }
                { // Port Counts
                    final Integer port = nodeIpAddress.getPort();
                    final Integer count = Util.coalesce(nodePortCounts.get(port));
                    nodePortCounts.put(port, (count + 1));
                }
            }
        }

        Ip bestNodeIpAddress = null;
        {
            Integer bestCount = Integer.MIN_VALUE;
            for (final Ip nodeIpAddress : nodeIpAddressCounts.keySet()) {
                final Integer count = nodeIpAddressCounts.get(nodeIpAddress);
                if (count > bestCount) {
                    bestNodeIpAddress = nodeIpAddress;
                    bestCount = count;
                }
            }
        }

        Integer bestNodePort = null;
        {
            Integer bestCount = 1; // Mandate that at least two peer's ports match, since if all connections are outbound then port will be unreliable.
            for (final Integer nodePort : nodePortCounts.keySet()) {
                final Integer count = nodePortCounts.get(nodePort);
                if (count > bestCount) {
                    bestNodePort = nodePort;
                    bestCount = count;
                }
            }
            if (bestNodePort == null) {
                bestNodePort = _defaultExternalPort;
            }
        }

        if ( (bestNodeIpAddress == null) || (bestNodePort == null) ) {
            if (_localNodeIpAddress != null) {
                _localNodeIpAddress = null;
                Logger.info("External address unset.");
            }
            return;
        }

        final NodeIpAddress nodeIpAddress = new NodeIpAddress(bestNodeIpAddress, bestNodePort);
        if (! Util.areEqual(_localNodeIpAddress, nodeIpAddress)) {
            Logger.info("External address set to: " + nodeIpAddress);
            _localNodeIpAddress = nodeIpAddress;
        }
    }

    protected void _cleanupNotHandshakedNodes() {
        final Long nowInMilliseconds = _systemTime.getCurrentTimeInMilliSeconds();

        { // Cleanup any pending nodes that still haven't completed their handshake...
            final Iterator<NODE> pendingNodesIterator = _pendingNodes.values().iterator();
            while (pendingNodesIterator.hasNext()) {
                final NODE oldPendingNode = pendingNodesIterator.next();

                final Long pendingSinceTimeMilliseconds = oldPendingNode.getInitializationTimestamp();
                if ((nowInMilliseconds - pendingSinceTimeMilliseconds) >= 30000L) {
                    final NodeIpAddress nodeIpAddress = oldPendingNode.getRemoteNodeIpAddress();

                    pendingNodesIterator.remove();
                    oldPendingNode.disconnect();
                    if (nodeIpAddress != null) {
                        _connectedNodeAddresses.remove(nodeIpAddress);
                    }
                }
            }
        }
    }

    protected void _addHandshakedNode(final NODE node) {
        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
        final NodeId newNodeId = node.getId();

        if (nodeIpAddress != null) {
            _connectedNodeAddresses.add(nodeIpAddress);
        }
        _nodes.put(newNodeId, node);
        _nodeHealthMap.put(newNodeId, new MutableNodeHealth(newNodeId, _systemTime));
    }

    protected void _addNotHandshakedNode(final NODE node) {
        _cleanupNotHandshakedNodes();

        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
        if (nodeIpAddress != null) {
            _connectedNodeAddresses.add(nodeIpAddress);
        }
        _pendingNodes.put(node.getId(), node);
    }

    protected void _removeNode(final NODE node) {
        final NodeId nodeId = node.getId();

        final NodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        Logger.info("Dropped Node: " + node.getConnectionString() + " - " + (nodeHealth != null ? nodeHealth.getHealth() : "??? ") +  "hp");

        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();

        _nodes.remove(nodeId);
        _pendingNodes.remove(nodeId);
        _nodeHealthMap.remove(nodeId);
        if (nodeIpAddress != null) {
            _connectedNodeAddresses.remove(nodeIpAddress);
        }

        node.setNodeDisconnectedCallback(null);
        node.setNodeHandshakeCompleteCallback(null);
        node.setNodeConnectedCallback(null);
        node.setNodeAddressesReceivedCallback(null);

        node.disconnect();

        if (_nodes.isEmpty()) {
            if (! _isShuttingDown) {
                _onAllNodesDisconnected();
            }
        }
    }

    protected void _checkMaxNodeCount(final Integer maxNodeCount) {
        if (maxNodeCount > 0) {
            while (_nodes.size() > maxNodeCount) {
                final List<NODE> inactiveNodes = _getInactiveNodes();
                if (inactiveNodes.getCount() > 0) {
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

    protected void _broadcastNewNodesToExistingNodes(final List<NodeIpAddress> nodeIpAddresses) {
        for (final NODE node : _nodes.values()) {
            node.broadcastNodeAddresses(nodeIpAddresses);

            Logger.debug("Broadcasting " + nodeIpAddresses.getCount() + " new Nodes to existing Node (" + node + ")");
        }
    }

    protected void _broadcastExistingNodesToNewNode(final NODE newNode) {
        final Collection<NODE> nodes = _nodes.values();

        final MutableList<NodeIpAddress> nodeAddresses = new MutableList<NodeIpAddress>(nodes.size());
        for (final NODE node : nodes) {
            final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            nodeAddresses.add(nodeIpAddress);

            Logger.debug("Broadcasting Existing Node (" + nodeIpAddress + ") to New Node (" + newNode + ")");
        }

        newNode.broadcastNodeAddresses(nodeAddresses);
    }

    protected void _onNodeDisconnected(final NODE node) {
        Logger.debug("Node Disconnected: " + node.getConnectionString());

        _removeNode(node);
    }

    protected void _processQueuedMessages() {
        // Copy the list of queued transactions since _selectNodeForRequest and _sendMessage could potentially requeue the transmission...
        final MutableList<NodeApiMessage<NODE>> queuedTransmissions = new MutableList<NodeApiMessage<NODE>>(_queuedTransmissions.size());
        while (! _queuedTransmissions.isEmpty()) {
            final NodeApiMessage<NODE> message = _queuedTransmissions.poll();
            if (message != null) {
                queuedTransmissions.add(message);
            }
        }

        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                for (final NodeApiMessage<NODE> apiTransmission : queuedTransmissions) {
                    if (apiTransmission instanceof NodeApiRequest) {
                        _selectNodeForRequest((NodeApiRequest<NODE>) apiTransmission);
                    }
                    else {
                        _sendMessage(apiTransmission);
                    }
                }
            }
        });
    }

    /**
     * Return true if the nodeIpAddress will not be a duplicate connection.
     *  If the node is not a duplicate, it will be added as a to-be-connected node, which will prevents other threads from creating connections to the same address.
     */
    protected Boolean _markAddressForConnecting(final NodeIpAddress nodeIpAddress) {
        if (nodeIpAddress == null) { return false; }
        return (! _connectedNodeAddresses.add(nodeIpAddress));
    }

    protected Boolean _isConnectedToNode(final NodeIpAddress nodeIpAddress) {
        if (nodeIpAddress == null) { return false; }
        return _connectedNodeAddresses.contains(nodeIpAddress);
    }

    protected void _initNode(final NODE node) {
        final Container<Boolean> nodeDidConnect = new Container<Boolean>(null);

        final Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (nodeDidConnect) {
                        if (! Util.coalesce(nodeDidConnect.value, false)) {
                            nodeDidConnect.wait(10000L);
                        }
                    }

                    synchronized (nodeDidConnect) {
                        if (nodeDidConnect.value != null) { // Node connected successfully or has already been marked as disconnected...
                            return;
                        }

                        nodeDidConnect.value = false;

                        Logger.info("Node failed to connect. Purging node: " + node.getConnectionString());

                        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();

                        _pendingNodes.remove(node.getId());
                        if (nodeIpAddress != null) {
                            _connectedNodeAddresses.remove(nodeIpAddress);
                        }

                        node.disconnect();

                        Logger.debug("Node purged.");

                        if (_nodes.isEmpty()) {
                            if (!_isShuttingDown) {
                                _onAllNodesDisconnected();
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

        { // Determine external address via peers...
            _recalculateLocalNodeIpAddress();
            final NodeIpAddress reportedLocalNodeIpAddress = _localNodeIpAddress;
            if (reportedLocalNodeIpAddress != null) {
                node.setLocalNodeIpAddress(reportedLocalNodeIpAddress);
            }
        }

        node.setNodeAddressesReceivedCallback(new NODE.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddresses(final List<NodeIpAddress> nodeIpAddresses) {
                if (_isShuttingDown) { return; }

                final List<NodeIpAddress> unseenNodeAddresses;
                {
                    final ImmutableListBuilder<NodeIpAddress> listBuilder = new ImmutableListBuilder<NodeIpAddress>(nodeIpAddresses.getCount());
                    for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                        final boolean haveAlreadySeenNode = _nodeAddresses.contains(nodeIpAddress);
                        if (haveAlreadySeenNode) { continue; }

                        listBuilder.add(nodeIpAddress);
                        _nodeAddresses.add(nodeIpAddress);
                        _newNodeAddresses.add(nodeIpAddress);
                    }
                    unseenNodeAddresses = listBuilder.build();
                }
                if (unseenNodeAddresses.isEmpty()) { return; }

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

                if (_shouldOnlyConnectToSeedNodes) { return; }

                // Connect to the node if the node if the NodeManager is still looking for peers...
                for (final NodeIpAddress nodeIpAddress : unseenNodeAddresses) {
                    final Integer healthyNodeCount = _countNodesAboveHealth(50);
                    if (healthyNodeCount >= _maxNodeCount) { break; }

                    final Ip ip = nodeIpAddress.getIp();
                    final Integer port = nodeIpAddress.getPort();

                    final Boolean isAlreadyConnectedToNode = _markAddressForConnecting(nodeIpAddress);
                    if (isAlreadyConnectedToNode) { continue; }

                    final NODE newNode = _nodeFactory.newNode(ip.toString(), port);

                    _initNode(newNode);

                    _broadcastExistingNodesToNewNode(newNode);

                    _checkMaxNodeCount(_maxNodeCount - 1);

                    _addNotHandshakedNode(newNode);
                }
            }
        });

        node.setNodeConnectedCallback(new NODE.NodeConnectedCallback() {
            @Override
            public void onNodeConnected() {
                { // Handle connection timeout...
                    synchronized (nodeDidConnect) {
                        if (nodeDidConnect.value == null) {
                            nodeDidConnect.value = true;
                        }
                        else if (! nodeDidConnect.value) {
                            // Node connection timed out; abort.
                            return;
                        }

                        nodeDidConnect.notifyAll();
                    }
                }

                _onNodeConnected(node);
                _processQueuedMessages();
            }
        });

        node.setNodeHandshakeCompleteCallback(new NODE.NodeHandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                Logger.debug("HandshakeComplete: " + node.getConnectionString());

                _pendingNodes.remove(node.getId());
                _addHandshakedNode(node);

                final Long nodeNetworkTimeOffset = node.getNetworkTimeOffset();
                if (nodeNetworkTimeOffset != null) {
                    _networkTime.includeOffsetInSeconds(nodeNetworkTimeOffset);
                }

                _onNodeHandshakeComplete(node);
                _processQueuedMessages();
            }
        });

        node.setNodeDisconnectedCallback(new NODE.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                _onNodeDisconnected(node);
            }
        });

        node.connect();
        node.handshake();

        _threadPool.execute(timeoutRunnable);
    }

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

    protected List<NODE> _getInactiveNodes() {
        final MutableList<NODE> inactiveNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (! node.hasActiveConnection()) {
                inactiveNodes.add(node);
            }
        }
        return inactiveNodes;
    }

    protected List<NODE> _getActiveNodes() {
        final MutableList<NODE> activeNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (node.hasActiveConnection()) {
                activeNodes.add(node);
            }
        }

        return activeNodes;
    }

    protected NODE _selectWorstActiveNode() {
        final List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.getCount();
        if (activeNodeCount == 0) { return null; }

        final MutableList<NodeHealth> nodeHealthList = new MutableList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final MutableNodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            if (nodeHealth != null) {
                nodeHealthList.add(nodeHealth.asConst());
            }
        }
        nodeHealthList.sort(MutableNodeHealth.HEALTH_ASCENDING_COMPARATOR);

        for (int i = 0; i < nodeHealthList.getCount(); ++i) {
            final NodeHealth worstNodeHealth = nodeHealthList.get(i);
            final NODE worstNode = _nodes.get(worstNodeHealth.getNodeId());
            if (worstNode != null) { // _nodes may have been updated while the health was sorted, so it is possible that the worst node is no longer connected...
                return worstNode;
            }
        }

        return null;
    }

    protected NODE _selectBestNode() {
        final List<NODE> nodes = _selectBestNodes(1, null);
        if ( (nodes == null) || (nodes.isEmpty()) ) { return null; }

        final NODE selectedNode = nodes.get(0);

        final NodeHealth nodeHealth = _nodeHealthMap.get(selectedNode.getId());
        Logger.debug("Selected Node: " + (selectedNode.getId()) + " (" + (nodeHealth != null ? nodeHealth.getHealth() : "??? ") + "hp) - " + (selectedNode.getConnectionString()) + " - " + _nodes.size());

        return selectedNode;
    }

    protected NODE _selectBestNode(final NodeFilter<NODE> nodeFilter) {
        final List<NODE> nodes = _selectBestNodes(1, nodeFilter);
        if ( (nodes == null) || (nodes.isEmpty()) ) { return null; }

        return nodes.get(0);
    }

    protected List<NODE> _selectBestNodes(final Integer requestedNodeCount, final NodeFilter<NODE> nodeFilter) {
        final List<NODE> activeNodes = _getActiveNodes();

        final int activeNodeCount = activeNodes.getCount();
        if (activeNodeCount == 0) { return null; }

        final MutableList<NodeHealth> nodeHealthList = new MutableList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            if (nodeHealth != null) {
                nodeHealthList.add(nodeHealth.asConst()); // NOTE: Items must be a snapshot to prevent concurrent modifications during sort...
            }
        }
        nodeHealthList.sort(NodeHealth.HEALTH_ASCENDING_COMPARATOR);

        final MutableList<NODE> selectedNodes = new MutableList<NODE>(requestedNodeCount);
        int i = 0;
        while (selectedNodes.getCount() < requestedNodeCount) {
            final int index = (nodeHealthList.getCount() - i - 1);
            i += 1;

            if ( (index < 0) || (index >= nodeHealthList.getCount()) ) { break; }

            final NodeHealth bestNodeHealth = nodeHealthList.get(index);
            final NODE selectedNode = _nodes.get(bestNodeHealth.getNodeId());
            if (selectedNode != null) { // _nodes may have been updated during the selection process...
                if ( (nodeFilter == null) || nodeFilter.meetsCriteria(selectedNode) ) {
                    selectedNodes.add(selectedNode);
                }
            }
        }

        return selectedNodes;
    }

    protected void _pingIdleNodes() {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();

        final MutableList<NODE> idleNodes = new MutableList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
            final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

            if (idleDuration > PING_AFTER_MS_IDLE) {
                idleNodes.add(node);
            }
        }

        Logger.debug("Idle Node Count: " + idleNodes.getCount() + " / " + _nodes.size());

        for (final NODE idleNode : idleNodes) {
            // final NodeId nodeId = idleNode.getId();
            // _nodeHealthMap.get(nodeId).onRequestSent();

            if (! idleNode.handshakeIsComplete()) { return; }

            Logger.debug("Pinging Idle Node: " + idleNode.getConnectionString());

            idleNode.ping(new NODE.PingCallback() {
                @Override
                public void onResult(final Long pingInMilliseconds) {
                    Logger.debug("Node Pong: " + pingInMilliseconds);

                    final NodeId nodeId = idleNode.getId();
                    final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
                    if (nodeHealth != null) {
                        nodeHealth.updatePingInMilliseconds(pingInMilliseconds);
                    }
                }
            });
        }
    }

    protected void _removeDisconnectedNodes() {
        final MutableList<NODE> purgeableNodes = new MutableList<NODE>();

        for (final NODE node : _nodes.values()) {
            if (! node.isConnected()) {
                final long nodeAge = (_systemTime.getCurrentTimeInMilliSeconds() - node.getInitializationTimestamp());
                if (nodeAge > 10000L) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final NODE node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _removeHighLatencyNodes() {
        final MutableList<NODE> purgeableNodes = new MutableList<NODE>();

        for (final NODE node : _nodes.values()) {
            if (node.isConnected()) {
                final Long nodePing = node.getAveragePing();
                if ( (nodePing != null) && (nodePing > 10000L) ) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final NODE node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _addNode(final NODE node) {
        if (_isShuttingDown) {
            node.disconnect();
            return;
        }

        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
        final boolean isAlreadyConnectedToAddress = _markAddressForConnecting(nodeIpAddress);
        if (isAlreadyConnectedToAddress) {
            final NodeId nodeId = node.getId();
            final boolean isDuplicateCallToAddNode = ( (_nodes.containsKey(nodeId)) || (_pendingNodes.containsKey(nodeId)) );
            if (isDuplicateCallToAddNode) { return; }

            // `node` is actually a new connection to a node that we're already connected to
            node.disconnect();
            return;
        }

        _initNode(node);

        _checkMaxNodeCount(_maxNodeCount - 1);
        _addNotHandshakedNode(node);
    }

    protected void _selectNodeForRequest(final NodeApiRequest<NODE> apiRequest) {
        final NODE selectedNode;
        final MutableNodeHealth nodeHealth;
        {
            selectedNode = _selectBestNode();

            if (selectedNode == null) {
                _queuedTransmissions.add(apiRequest);
                return;
            }

            final NodeId nodeId = selectedNode.getId();
            nodeHealth = _nodeHealthMap.get(nodeId);
        }

        apiRequest.nodeHealthRequest = nodeHealth.onRequestSent();
        _pendingRequestsManager.addPendingRequest(apiRequest);
        apiRequest.run(selectedNode);

        _pendingRequestsManager.wakeUp();
    }

    protected void _selectNodeForRequest(final NODE selectedNode, final NodeApiRequest<NODE> apiRequest) {
        final MutableNodeHealth nodeHealth;
        {
            if (selectedNode == null) {
                _queuedTransmissions.add(apiRequest);
                return;
            }

            final NodeId nodeId = selectedNode.getId();
            nodeHealth = _nodeHealthMap.get(nodeId);
        }

        if (nodeHealth == null) {
            Logger.debug("Selected node no longer connected: " + selectedNode.getConnectionString());
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
        final NODE selectedNode = _selectBestNode();

        if (selectedNode == null) {
            _queuedTransmissions.add(apiMessage);
            return;
        }

        final NodeId nodeId = selectedNode.getId();
        final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        if (nodeHealth != null) {
            nodeHealth.onMessageSent();
        }

        apiMessage.run(selectedNode);
    }

    public NodeManager(final Integer maxNodeCount, final NodeFactory<NODE> nodeFactory, final MutableNetworkTime networkTime, final ThreadPool threadPool) {
        _systemTime = new SystemTime();
        _nodes = new ConcurrentHashMap<NodeId, NODE>(maxNodeCount);
        _nodeHealthMap = new ConcurrentHashMap<NodeId, MutableNodeHealth>(maxNodeCount);

        _maxNodeCount = maxNodeCount;
        _nodeFactory = nodeFactory;
        _networkTime = networkTime;
        _pendingRequestsManager = new PendingRequestsManager<NODE>(_systemTime, threadPool);
        _threadPool = threadPool;
    }

    public NodeManager(final Integer maxNodeCount, final NodeFactory<NODE> nodeFactory, final MutableNetworkTime networkTime, final SystemTime systemTime, final ThreadPool threadPool) {
        _nodes = new ConcurrentHashMap<NodeId, NODE>(maxNodeCount);
        _nodeHealthMap = new ConcurrentHashMap<NodeId, MutableNodeHealth>(maxNodeCount);

        _maxNodeCount = maxNodeCount;
        _nodeFactory = nodeFactory;
        _networkTime = networkTime;
        _systemTime = systemTime;
        _pendingRequestsManager = new PendingRequestsManager<NODE>(_systemTime, threadPool);
        _threadPool = threadPool;
    }

    public void setDefaultExternalPort(final Integer externalPortNumber) {
        _defaultExternalPort = externalPortNumber;
    }

    public void defineSeedNode(final NodeIpAddress nodeIpAddress) {
        _seedNodes.add(nodeIpAddress);
    }

    public void addNode(final NODE node) {
        _addNode(node);
    }

    public NetworkTime getNetworkTime() {
        return _networkTime;
    }

    public void startNodeMaintenanceThread() {
        _nodeMaintenanceThread.start();
    }

    public void stopNodeMaintenanceThread() {
        _nodeMaintenanceThread.interrupt();
        try { _nodeMaintenanceThread.join(10000L); } catch (final Exception exception) { }
    }

    public void executeRequest(final NodeApiRequest<NODE> nodeNodeApiRequest) {
        _selectNodeForRequest(nodeNodeApiRequest);
    }

    public void sendMessage(final NodeApiMessage<NODE> nodeNodeApiMessage) {
        _sendMessage(nodeNodeApiMessage);
    }

    public List<NODE> getNodes() {
        return new MutableList<NODE>(_nodes.values());
    }

    public List<NodeId> getNodeIds() {
        final ImmutableListBuilder<NodeId> nodeIds = new ImmutableListBuilder<NodeId>(_nodes.size());
        nodeIds.addAll(_nodes.keySet());
        return nodeIds.build();
    }

    public NODE getNode(final NodeId nodeId) {
        return _nodes.get(nodeId);
    }

    public NODE getNode(final NodeFilter<NODE> nodeFilter) {
        return _selectBestNode(nodeFilter);
    }

    /**
     * Returns true if the NodeManager is connected, or is connecting, to a node at the provided Ip and port.
     */
    public Boolean isConnectedToNode(final NodeIpAddress nodeIpAddress) {
        return _isConnectedToNode(nodeIpAddress);
    }

    public Long getNodeHealth(final NodeId nodeId) {
        final MutableNodeHealth nodeHealth = _nodeHealthMap.get(nodeId);
        if (nodeHealth == null) { return null; }

        return nodeHealth.getHealth();
    }

    public NODE getBestNode() {
        return _selectBestNode();
    }

    public List<NODE> getBestNodes(final Integer nodeCount) {
        return Util.coalesce(_selectBestNodes(nodeCount, null), new MutableList<NODE>(0));
    }

    public List<NODE> getBestNodes(final Integer nodeCount, final NodeFilter<NODE> nodeFilter) {
        return Util.coalesce(_selectBestNodes(nodeCount, nodeFilter), new MutableList<NODE>(0));
    }

    public NODE getWorstNode() {
        return _selectWorstActiveNode();
    }

    public Integer getActiveNodeCount() {
        final List<NODE> nodes = _getActiveNodes();
        return nodes.getCount();
    }

    public void setShouldOnlyConnectToSeedNodes(final Boolean shouldOnlyConnectToSeedNodes) {
        _shouldOnlyConnectToSeedNodes = shouldOnlyConnectToSeedNodes;
    }

    public void shutdown() {
        _isShuttingDown = true;
        final MutableList<NODE> nodes = new MutableList<NODE>(_nodes.values());
        nodes.addAll(_pendingNodes.values());
        _nodes.clear();
        _pendingNodes.clear();

        // Nodes must be disconnected outside of the _mutex lock in order to prevent deadlock...
        for (final NODE node : nodes) {
            node.disconnect();
        }

        _pendingRequestsManager.stop();
    }
}

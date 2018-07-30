package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeFactory;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.p2p.node.manager.health.NodeHealth;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;

import java.lang.ref.WeakReference;
import java.util.*;

public class NodeManager<NODE extends Node> {
    public static Boolean LOGGING_ENABLED = true;

    /**
     * NodeApiInvocation.run() should invoke an Api Call on the provided Node.
     *  It is required that within the callback of the Node Api Call, nodeApiInvocationCallback.didTimeout() is invoked immediately.
     *  nodeApiInvocationCallback.didTimeout() cancels the retry-thread timeout and returns true if the request has already timed out.
     *  If nodeApiInvocationCallback.didTimeout() returns true, then the the Node Api Callback should abort.
     */
    public interface NodeApiInvocation<NODE> {
        void run(NODE node, NodeApiInvocationCallback nodeApiInvocationCallback);
        default void onFailure() { }
    }

    public static abstract class NodeApiInvocationCallback {
        protected NodeApiInvocationCallback() { }
        public abstract Boolean didTimeout();
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
    protected final NodeFactory<NODE> _nodeFactory;
    protected final Map<NodeId, NODE> _nodes;
    protected final Map<NodeId, NodeHealth> _nodeHealthMap;
    protected final java.util.List<Runnable> _queuedNodeRequests = new ArrayList<Runnable>();
    protected final Set<NodeIpAddress> _nodeAddresses = new HashSet<NodeIpAddress>();
    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();
    protected final Integer _maxNodeCount;
    protected final MutableNetworkTime _networkTime = new MutableNetworkTime();

    protected void _onAllNodesDisconnected() { }
    protected void _onNodeHandshakeComplete(final NODE node) { }

    protected void _addNode(final NODE node) {
        final NodeId newNodeId = node.getId();
        _nodes.put(newNodeId, node);
        _nodeHealthMap.put(newNodeId, new NodeHealth(newNodeId));
    }

    protected void _removeNode(final NODE node) {
        final NodeId nodeId = node.getId();

        _nodes.remove(nodeId);
        final NodeHealth nodeHealth = _nodeHealthMap.remove(nodeId);

        node.disconnect();

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Dropped Node: " + node.getConnectionString() + " - " + nodeHealth.calculateHealth() + "hp");
        }

        if (_nodes.isEmpty()) {
            _onAllNodesDisconnected();
        }
    }

    // NOTE: Requires Mutex lock...
    protected void _checkMaxNodeCount(final Integer maxNodeCount) {
        if (maxNodeCount > 0) {
            while (_nodes.size() > maxNodeCount) {
                final java.util.List<NODE> inactiveNodes = _getInactiveNodes();
                if (inactiveNodes.size() > 0) {
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
    protected void _broadcastNewNodeToExistingNodes(final NodeIpAddress nodeIpAddress) {
        for (final NODE node : _nodes.values()) {
            node.broadcastNodeAddress(nodeIpAddress);
            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting New Node (" + nodeIpAddress + ") to Existing Node (" + node + ")");
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

    protected void _initNode(final NODE node) {
        final Container<Boolean> nodeConnected = new Container<Boolean>(null);
        final Thread timeoutThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(10000);

                    synchronized (nodeConnected) {
                        if (nodeConnected.value == null) {
                            nodeConnected.value = false;

                            if (LOGGING_ENABLED) {
                                Logger.log("P2P: Node failed to connect. Purging node.");
                            }

                            synchronized (_mutex) {
                                _removeNode(node);
                            }

                            node.disconnect();

                            if (LOGGING_ENABLED) {
                                Logger.log("P2P: Node purged.");
                            }
                        }
                    }
                }
                catch (final Exception exception) { }
            }
        });
        final WeakReference<Thread> timeoutThreadReference = new WeakReference<Thread>(timeoutThread);

        node.setNodeAddressesReceivedCallback(new NODE.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddress(final NodeIpAddress nodeIpAddress) {

                synchronized (_mutex) {
                    final Boolean haveAlreadySeenNode = _nodeAddresses.contains(nodeIpAddress);
                    if (haveAlreadySeenNode) { return; }

                    _nodeAddresses.add(nodeIpAddress);
                    _broadcastNewNodeToExistingNodes(nodeIpAddress);

                    final Integer healthyNodeCount = _countNodesAboveHealth(50);
                    if (healthyNodeCount >= _maxNodeCount) { return; }

                    final String address = nodeIpAddress.getIp().toString();
                    final Integer port = nodeIpAddress.getPort();
                    final String connectionString = (address + ":" + port);

                    for (final NODE existingNode : _nodes.values()) {
                        final Boolean isAlreadyConnectedToNode = (existingNode.getConnectionString().equals(connectionString));
                        if (isAlreadyConnectedToNode) {
                            return;
                        }
                    }

                    final NODE newNode = _nodeFactory.newNode(address, port);

                    _initNode(newNode);

                    _broadcastExistingNodesToNewNode(newNode);

                    _checkMaxNodeCount(_maxNodeCount - 1);

                    _addNode(newNode);
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

                        final Thread timeoutThread = timeoutThreadReference.get();
                        if (timeoutThread != null) {
                            timeoutThread.interrupt();
                        }
                    }
                }

                if (! node.hasActiveConnection()) { return; }

                synchronized (_mutex) {
                    for (final Runnable runnable : _queuedNodeRequests) {
                        new Thread(runnable).start();
                    }
                    _queuedNodeRequests.clear();
                }
            }
        });

        node.setNodeHandshakeCompleteCallback(new NODE.NodeHandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                final Long nodeNetworkTimeOffset = node.getNetworkTimeOffset();
                if (nodeNetworkTimeOffset != null) {
                    _networkTime.includeOffsetInSeconds(nodeNetworkTimeOffset);
                }

                synchronized (_mutex) {
                    for (final Runnable runnable : _queuedNodeRequests) {
                        new Thread(runnable).start();
                    }
                    _queuedNodeRequests.clear();
                }

                _onNodeHandshakeComplete(node);
            }
        });

        node.setNodeDisconnectedCallback(new NODE.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                synchronized (_mutex) {
                    if (LOGGING_ENABLED) {
                        Logger.log("P2P: Node Disconnected: " + node.getConnectionString());
                    }

                    _removeNode(node);
                }
            }
        });

        node.connect();
        node.handshake();
        timeoutThread.start();
    }

    // NOTE: Requires Mutex Lock...
    protected Integer _countNodesAboveHealth(final Integer minimumHealth) {
        int nodeCount = 0;
        final java.util.List<NODE> activeNodes = _getActiveNodes();
        for (final NODE node : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(node.getId());
            if (nodeHealth.calculateHealth() > minimumHealth) {
                nodeCount += 1;
            }
        }
        return nodeCount;
    }

    // NOTE: Requires Mutex Lock...
    protected java.util.List<NODE> _getInactiveNodes() {
        final java.util.List<NODE> inactiveNodes = new ArrayList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (! node.hasActiveConnection()) {
                inactiveNodes.add(node);
            }
        }
        return inactiveNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected java.util.List<NODE> _getActiveNodes() {
        final java.util.List<NODE> activeNodes = new ArrayList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (node.hasActiveConnection()) {
                activeNodes.add(node);
            }
        }

        return activeNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected NODE _selectWorstActiveNode() {
        final java.util.List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final java.util.List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth);
        }
        Collections.sort(nodeHealthList, NodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(0);
        return _nodes.get(bestNodeHealth.getNodeId());
    }

    // NOTE: Requires Mutex Lock...
    protected NODE _selectBestNode() {
        final java.util.List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final java.util.List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
        for (final NODE activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth);
        }
        Collections.sort(nodeHealthList, NodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(nodeHealthList.size() - 1);
        final NODE selectedNode = _nodes.get(bestNodeHealth.getNodeId());

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Selected Node: " + (selectedNode.getId()) + " (" + bestNodeHealth.calculateHealth() + "hp) - " + (selectedNode.getConnectionString()) + " - " + activeNodeCount + " / " + _nodes.size());
        }

        return selectedNode;
    }

    // NOTE: Requires Mutex lock...
    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = System.currentTimeMillis();

        final java.util.List<NODE> idleNodes = new ArrayList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
            final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

            if (idleDuration > maxIdleTime) {
                idleNodes.add(node);
            }
        }

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Idle Node Count: " + idleNodes.size() + " / " + _nodes.size());
        }

        for (final NODE idleNode : idleNodes) {
            // final NodeId nodeId = idleNode.getId();
            // _nodeHealthMap.get(nodeId).onMessageSent();

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
                }
            });
        }
    }

    // NOTE: Requires Mutex lock...
    protected void _removeDisconnectedNodes() {
        final java.util.List<NODE> purgeableNodes = new ArrayList<NODE>();

        for (final NODE node : _nodes.values()) {
            final Long nodeAge = (System.currentTimeMillis() - node.getInitializationTime());
            if (! node.isConnected()) {
                if (nodeAge > 10000L) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final NODE node : purgeableNodes) {
            _removeNode(node);
        }
    }

    public NodeManager(final Integer maxNodeCount, final NodeFactory<NODE> nodeFactory) {
        _nodes = new HashMap<NodeId, NODE>(maxNodeCount);
        _nodeHealthMap = new HashMap<NodeId, NodeHealth>(maxNodeCount);
        _maxNodeCount = maxNodeCount;
        _nodeFactory = nodeFactory;
    }

    public void addNode(final NODE node) {
        _initNode(node);

        synchronized (_mutex) {
            _checkMaxNodeCount(_maxNodeCount - 1);
            _addNode(node);
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

    protected void _executeRequest(final NodeApiInvocation<NODE> nodeApiInvocation, final ReplayInvocation replayInvocation) {
        final NODE selectedNode;
        final NodeHealth nodeHealth;
        {
            synchronized (_mutex) {
                selectedNode = _selectBestNode();

                if (selectedNode == null) {
                    _queuedNodeRequests.add(replayInvocation);
                    return;
                }

                final NodeId nodeId = selectedNode.getId();
                nodeHealth = _nodeHealthMap.get(nodeId);
            }
        }

        final RequestTimeoutThread timeoutThread;
        final NodeApiInvocationCallback cancelRequestTimeout;
        {
            final Container<Boolean> didMessageTimeOut = new Container<Boolean>(null);
            timeoutThread = new RequestTimeoutThread(didMessageTimeOut, nodeHealth, replayInvocation);

            cancelRequestTimeout = new NodeApiInvocationCallback() {
                @Override
                public Boolean didTimeout() {
                    synchronized (timeoutThread.mutex) {
                        if (didMessageTimeOut.value != null) { return true; }
                        didMessageTimeOut.value = false;
                    }

                    nodeHealth.onMessageReceived(true);
                    timeoutThread.interrupt();
                    return false;
                }
            };
        }

        timeoutThread.start();
        nodeHealth.onMessageSent();
        nodeApiInvocation.run(selectedNode, cancelRequestTimeout);
    }

    public void executeRequest(final NodeApiInvocation<NODE> nodeApiInvocation) {
        final Container<ReplayInvocation> replayInvocation = new Container<ReplayInvocation>();

        replayInvocation.value = new ReplayInvocation(
            new Runnable() {
                @Override
                public void run() {
                    _executeRequest(nodeApiInvocation, replayInvocation.value);
                }
            },
            new Runnable() {
                @Override
                public void run() {
                    nodeApiInvocation.onFailure();
                }
            }
        );

        _executeRequest(nodeApiInvocation, replayInvocation.value);
    }

    public List<NODE> getNodes() {
        return new MutableList<NODE>(_nodes.values());
    }
}

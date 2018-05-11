package com.softwareverde.network.p2p.node.manager;

import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeFactory;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.p2p.node.manager.health.NodeHealth;
import com.softwareverde.util.Container;

import java.util.*;

public class NodeManager<NODE extends Node> {
    public static final Long REQUEST_TIMEOUT_THRESHOLD = 5_000L;
    public static Boolean LOGGING_ENABLED = true;

    /**
     * NodeApiInvocation.run() should invoke an Api Call on the provided Node.
     *  It is required that within the callback of the Node Api Call, nodeApiInvocationCallback.didTimeout() is invoked immediately.
     *  nodeApiInvocationCallback.didTimeout() cancels the retry-thread timeout and returns true if the request has already timed out.
     *  If nodeApiInvocationCallback.didTimeout() returns true, then the the Node Api Callback should abort.
     */
    public interface NodeApiInvocation<NODE> {
        void run(NODE node, NodeApiInvocationCallback nodeApiInvocationCallback);
    }

    public static abstract class NodeApiInvocationCallback {
        protected NodeApiInvocationCallback() { }
        public abstract Boolean didTimeout();
    }

    protected static class RequestTimeoutThread extends Thread {
        public final Object mutex = new Object();

        private final Container<Boolean> _didMessageTimeOut;
        private final NodeHealth _nodeHealth;
        private final Runnable _replayInvocation;

        public RequestTimeoutThread(final Container<Boolean> didMessageTimeoutContainer, final NodeHealth nodeHealth, final Runnable replayInvocation) {
            _didMessageTimeOut = didMessageTimeoutContainer;
            _nodeHealth = nodeHealth;
            _replayInvocation = replayInvocation;

            this.setName("Node Manager - Request Timeout Thread - " + this.getId());
        }

        @Override
        public void run() {
            try { Thread.sleep(REQUEST_TIMEOUT_THRESHOLD); }
            catch (final Exception exception) { return; }

            synchronized (this.mutex) {
                if (_didMessageTimeOut.value != null) { return; }
                _didMessageTimeOut.value = true;
            }

            _nodeHealth.onMessageReceived(false);
            if (LOGGING_ENABLED) {
                Logger.log("P2P: NOTICE: Node " + _nodeHealth.getNodeId() + ": Request timed out.");
            }

            if (_replayInvocation != null) {
                _replayInvocation.run();
            }
        }
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
    protected final List<Runnable> _queuedNodeRequests = new ArrayList<Runnable>();
    protected final Set<NodeIpAddress> _nodeAddresses = new HashSet<NodeIpAddress>();
    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();
    protected final Integer _maxNodeCount;

    protected void _removeNode(final NODE node) {
        final NodeId nodeId = node.getId();

        _nodes.remove(nodeId);
        final NodeHealth nodeHealth = _nodeHealthMap.remove(nodeId);

        node.disconnect();

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Dropped Node: " + node.getConnectionString() + " - " + nodeHealth.calculateHealth() + "hp");
        }
    }

    protected void _checkNodeCount(final Integer maxNodeCount) {
        if (maxNodeCount > 0) {
            while (_nodes.size() > maxNodeCount) {
                final List<NODE> inactiveNodes = _getInactiveNodes();
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

        final List<NodeIpAddress> nodeAddresses = new ArrayList<NodeIpAddress>(nodes.size());
        for (final NODE node : nodes) {
            final NodeIpAddress nodeIpAddress = node.getNodeAddress();
            if (nodeIpAddress == null) { continue; }

            nodeAddresses.add(nodeIpAddress);

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting Existing Node (" + nodeIpAddress + ") to New Node (" + newNode + ")");
            }
        }

        newNode.broadcastNodeAddresses(nodeAddresses);
    }

    protected void _initNode(final NODE node) {
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

                    _checkNodeCount(_maxNodeCount - 1);

                    final NodeId newNodeId = newNode.getId();
                    _nodes.put(newNodeId, newNode);
                    _nodeHealthMap.put(newNodeId, new NodeHealth(newNodeId));
                }
            }
        });

        node.setNodeConnectedCallback(new NODE.NodeConnectedCallback() {
            @Override
            public void onNodeConnected() {
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
                synchronized (_mutex) {
                    for (final Runnable runnable : _queuedNodeRequests) {
                        new Thread(runnable).start();
                    }
                    _queuedNodeRequests.clear();
                }
            }
        });

        node.setNodeDisconnectedCallback(new NODE.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                synchronized (_mutex) {
                    final NodeId nodeId = node.getId();
                    final NODE disconnectedNode = _nodes.remove(nodeId);
                    _nodeHealthMap.remove(nodeId);

                    if (LOGGING_ENABLED) {
                        Logger.log("P2P: Node Disconnected: " + disconnectedNode.getConnectionString());
                    }
                }
            }
        });
    }

    // NOTE: Requires Mutex Lock...
    protected Integer _countNodesAboveHealth(final Integer minimumHealth) {
        int nodeCount = 0;
        final List<NODE> activeNodes = _getActiveNodes();
        for (final NODE node : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(node.getId());
            if (nodeHealth.calculateHealth() > minimumHealth) {
                nodeCount += 1;
            }
        }
        return nodeCount;
    }

    // NOTE: Requires Mutex Lock...
    protected List<NODE> _getInactiveNodes() {
        final List<NODE> inactiveNodes = new ArrayList<NODE>(_nodes.size());
        for (final NODE node : _nodes.values()) {
            if (! node.hasActiveConnection()) {
                inactiveNodes.add(node);
            }
        }
        return inactiveNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected List<NODE> _getActiveNodes() {
        final List<NODE> activeNodes = new ArrayList<NODE>(_nodes.size());
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

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
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
        final List<NODE> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
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

    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = System.currentTimeMillis();

        final List<NODE> idleNodes = new ArrayList<NODE>(_nodes.size());
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

            idleNode.ping(new NODE.PingCallback() {
                @Override
                public void onResult(final Long pingInMilliseconds) {
                    if (LOGGING_ENABLED) {
                        Logger.log("P2P: Node Pong: " + pingInMilliseconds);
                    }
                }
            });

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Pinging Idle Node: " + idleNode.getConnectionString());
            }
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
            _checkNodeCount(_maxNodeCount - 1);

            final NodeId nodeId = node.getId();
            _nodes.put(nodeId, node);
            _nodeHealthMap.put(nodeId, new NodeHealth(nodeId));
        }
    }

    public void startNodeMaintenanceThread() {
        _nodeMaintenanceThread.start();
    }

    public void stopNodeMaintenanceThread() {
        _nodeMaintenanceThread.interrupt();
        try { _nodeMaintenanceThread.join(); } catch (final Exception exception) { }
    }

    public void executeRequest(final NodeApiInvocation<NODE> nodeApiInvocation) {
        final Runnable replayInvocation = new Runnable() {
            @Override
            public void run() {
                NodeManager.this.executeRequest(nodeApiInvocation);
            }
        };

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
}

package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.module.node.manager.health.NodeHealth;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.util.Container;

import java.util.*;

public class NodeManager {
    public static final Long REQUEST_TIMEOUT_THRESHOLD = 5_000L;
    public static Boolean LOGGING_ENABLED = true;

    /**
     * NodeApiInvocation.run() should invoke an Api Call on the provided Node.
     *  It is required that within the callback of the Node Api Call, nodeApiInvocationCallback.didTimeout() is invoked immediately.
     *  nodeApiInvocationCallback.didTimeout() cancels the retry-thread timeout and returns true if the request has already timed out.
     *  If nodeApiInvocationCallback.didTimeout() returns true, then the the Node Api Callback should abort.
     */
    public interface NodeApiInvocation {
        void run(BitcoinNode node, NodeApiInvocationCallback nodeApiInvocationCallback);
    }

    public static abstract class NodeApiInvocationCallback {
        protected NodeApiInvocationCallback() { }
        abstract Boolean didTimeout();
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

    protected final Map<NodeId, BitcoinNode> _nodes;
    protected final Map<NodeId, NodeHealth> _nodeHealthMap;

    protected final List<Runnable> _queuedNodeRequests = new ArrayList<Runnable>();

    protected final Set<BitcoinNodeIpAddress> _nodeAddresses = new HashSet<BitcoinNodeIpAddress>();

    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();

    protected final Integer _maxNodeCount;

    protected void _removeNode(final Node node) {
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
                final List<Node> inactiveNodes = _getInactiveNodes();
                if (inactiveNodes.size() > 0) {
                    final Node inactiveNode = inactiveNodes.get(0);
                    _removeNode(inactiveNode);
                    continue;
                }

                final Node worstActiveNode = _selectWorstActiveNode();
                if (worstActiveNode != null) {
                    _removeNode(worstActiveNode);
                    continue;
                }

                final Set<NodeId> keySet = _nodes.keySet();
                final NodeId firstKey = keySet.iterator().next();
                final Node node = _nodes.get(firstKey);
                _removeNode(node);
            }
        }
    }

    // NOTE: Requires Mutex Lock...
    protected void _broadcastNewNodeToExistingNodes(final BitcoinNodeIpAddress nodeIpAddress) {
        for (final Node node : _nodes.values()) {
            node.broadcastNodeAddress(nodeIpAddress);
            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting New Node (" + nodeIpAddress + ") to Existing Node (" + node.getNodeAddress() + ")");
            }
        }
    }

    // NOTE: Requires Mutex Lock...
    protected void _broadcastExistingNodesToNewNode(final Node newNode) {
        final Collection<BitcoinNode> nodes = _nodes.values();

        final List<BitcoinNodeIpAddress> nodeAddresses = new ArrayList<BitcoinNodeIpAddress>(nodes.size());
        for (final BitcoinNode node : nodes) {
            final BitcoinNodeIpAddress nodeIpAddress = node.getNodeAddress();
            if (nodeIpAddress == null) { continue; }

            nodeAddresses.add(nodeIpAddress);

            if (LOGGING_ENABLED) {
                Logger.log("P2P: Broadcasting Existing Node (" + nodeIpAddress + ") to New Node (" + newNode.getConnectionString() + ")");
            }
        }

        newNode.broadcastNodeAddresses(nodeAddresses);
    }

    protected void _initNode(final Node node) {
        node.setNodeAddressesReceivedCallback(new BitcoinNode.NodeAddressesReceivedCallback() {
            @Override
            public void onNewNodeAddress(final NodeIpAddress nodeIpAddress) {
                if (! (nodeIpAddress instanceof BitcoinNodeIpAddress)) { Logger.log("NOTICE: Disregarding Non-Bitcoin NodeIpAddress..."); return; }
                final BitcoinNodeIpAddress bitcoinNodeIpAddress = (BitcoinNodeIpAddress) nodeIpAddress;

                synchronized (_mutex) {
                    final Boolean haveAlreadySeenNode = _nodeAddresses.contains(bitcoinNodeIpAddress);
                    if (haveAlreadySeenNode) { return; }

                    _nodeAddresses.add(bitcoinNodeIpAddress);
                    _broadcastNewNodeToExistingNodes(bitcoinNodeIpAddress);

                    final Integer healthyNodeCount = _countNodesAboveHealth(50);
                    if (healthyNodeCount >= _maxNodeCount) { return; }

                    final String address = nodeIpAddress.getIp().toString();
                    final Integer port = nodeIpAddress.getPort();
                    final String connectionString = (address + ":" + port);

                    for (final Node existingNode : _nodes.values()) {
                        final Boolean isAlreadyConnectedToNode = (existingNode.getConnectionString().equals(connectionString));
                        if (isAlreadyConnectedToNode) {
                            return;
                        }
                    }

                    final BitcoinNode newNode = new BitcoinNode(address, port);
                    _initNode(newNode);

                    _broadcastExistingNodesToNewNode(newNode);

                    _checkNodeCount(_maxNodeCount - 1);

                    final NodeId newNodeId = newNode.getId();
                    _nodes.put(newNodeId, newNode);
                    _nodeHealthMap.put(newNodeId, new NodeHealth(newNodeId));
                }
            }
        });

        node.setNodeConnectedCallback(new Node.NodeConnectedCallback() {
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

        node.setNodeHandshakeCompleteCallback(new Node.NodeHandshakeCompleteCallback() {
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

        node.setNodeDisconnectedCallback(new Node.NodeDisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                synchronized (_mutex) {
                    final NodeId nodeId = node.getId();
                    final Node disconnectedNode = _nodes.remove(nodeId);
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
        final List<Node> activeNodes = _getActiveNodes();
        for (final Node node : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(node.getId());
            if (nodeHealth.calculateHealth() > minimumHealth) {
                nodeCount += 1;
            }
        }
        return nodeCount;
    }

    // NOTE: Requires Mutex Lock...
    protected List<Node> _getInactiveNodes() {
        final List<Node> inactiveNodes = new ArrayList<Node>(_nodes.size());
        for (final Node node : _nodes.values()) {
            if (! node.hasActiveConnection()) {
                inactiveNodes.add(node);
            }
        }
        return inactiveNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected List<Node> _getActiveNodes() {
        final List<Node> activeNodes = new ArrayList<Node>(_nodes.size());
        for (final Node node : _nodes.values()) {
            if (node.hasActiveConnection()) {
                activeNodes.add(node);
            }
        }

        return activeNodes;
    }

    // NOTE: Requires Mutex Lock...
    protected Node _selectWorstActiveNode() {
        final List<Node> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
        for (final Node activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth);
        }
        Collections.sort(nodeHealthList, NodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(0);
        return _nodes.get(bestNodeHealth.getNodeId());
    }

    // NOTE: Requires Mutex Lock...
    protected BitcoinNode _selectBestNode() {
        final List<Node> activeNodes = _getActiveNodes();

        final Integer activeNodeCount = activeNodes.size();
        if (activeNodeCount == 0) { return null; }

        final List<NodeHealth> nodeHealthList = new ArrayList<NodeHealth>(activeNodeCount);
        for (final Node activeNode : activeNodes) {
            final NodeHealth nodeHealth = _nodeHealthMap.get(activeNode.getId());
            nodeHealthList.add(nodeHealth);
        }
        Collections.sort(nodeHealthList, NodeHealth.COMPARATOR);

        final NodeHealth bestNodeHealth = nodeHealthList.get(nodeHealthList.size() - 1);
        final BitcoinNode selectedNode = _nodes.get(bestNodeHealth.getNodeId());

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Selected Node: " + (selectedNode.getId()) + " (" + bestNodeHealth.calculateHealth() + "hp) - " + (selectedNode.getConnectionString()) + " - " + activeNodeCount + " / " + _nodes.size());
        }

        return selectedNode;
    }

    protected void _pingIdleNodes() {
        final Long maxIdleTime = 30000L;

        final Long now = System.currentTimeMillis();

        final List<Node> idleNodes = new ArrayList<Node>(_nodes.size());
        for (final Node node : _nodes.values()) {
            final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
            final Long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

            if (idleDuration > maxIdleTime) {
                idleNodes.add(node);
            }
        }

        if (LOGGING_ENABLED) {
            Logger.log("P2P: Idle Node Count: " + idleNodes.size() + " / " + _nodes.size());
        }
        for (final Node idleNode : idleNodes) {
            // final NodeId nodeId = idleNode.getId();
            // _nodeHealthMap.get(nodeId).onMessageSent();

            idleNode.ping(new Node.PingCallback() {
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

    public NodeManager(final Integer maxNodeCount) {
        _nodes = new HashMap<NodeId, BitcoinNode>(maxNodeCount);
        _nodeHealthMap = new HashMap<NodeId, NodeHealth>(maxNodeCount);
        _maxNodeCount = maxNodeCount;
    }

    public void addNode(final BitcoinNode node) {
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

    public void executeRequest(final NodeApiInvocation nodeApiInvocation) {
        final Runnable replayInvocation = new Runnable() {
            @Override
            public void run() {
                NodeManager.this.executeRequest(nodeApiInvocation);
            }
        };

        final BitcoinNode selectedNode;
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

package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeHeadBlockFinder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BitcoinNodeManager {
    public static final Long PING_AFTER_MS_IDLE = (5L * 60000L); // 5 Minutes
    public static final Integer MINIMUM_THIN_BLOCK_TRANSACTION_COUNT = 64;

    public interface NewNodeCallback {
        void onNodeHandshakeComplete(BitcoinNode bitcoinNode);
    }

    public interface NodeApiTransmission { }

    public static class NodeFilters {
        public static final NodeFilter KEEP_ACTIVE_NODES = new NodeFilter() {
            @Override
            public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                return bitcoinNode.hasActiveConnection();
            }
        };

        public static final NodeFilter KEEP_INACTIVE_NODES = new NodeFilter() {
            @Override
            public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                return (! bitcoinNode.hasActiveConnection());
            }
        };
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
    protected final BitcoinNodeFactory _nodeFactory;
    protected final ThreadPool _threadPool;

    protected final ConcurrentHashMap<NodeId, BitcoinNode> _preferredNodes;
    protected final ConcurrentHashMap<NodeId, BitcoinNode> _otherNodes;
    protected ConcurrentHashMap<NodeId, BitcoinNode> _pendingNodes = new ConcurrentHashMap<NodeId, BitcoinNode>(); // Nodes that have been added but have not yet completed their handshake.

    // _connectedNodeAddresses contains NodeIpAddresses that are either currently-connected, are pending handshake, or are about to be connected to.
    // All methods about to connect to a node should ensure the node will not be a duplicate by checking _connectedNodeAddresses for an existing entry.
    protected final ConcurrentHashSet<NodeIpAddress> _connectedNodeAddresses = new ConcurrentHashSet<NodeIpAddress>();

    protected final ConcurrentHashSet<NodeIpAddress> _seedNodes = new ConcurrentHashSet<NodeIpAddress>();
    protected final ConcurrentHashSet<NodeIpAddress> _nodeAddresses = new ConcurrentHashSet<NodeIpAddress>(); // The list of all node addresses advertised by peers.
    protected final Thread _nodeMaintenanceThread = new NodeMaintenanceThread();
    protected final MutableNetworkTime _networkTime;
    protected Integer _maxNodeCount;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;
    protected Boolean _isShuttingDown = false;

    protected Integer _defaultExternalPort = BitcoinProperties.PORT;
    protected NodeIpAddress _localNodeIpAddress = null;

    protected final ConcurrentHashSet<NodeIpAddress> _newNodeAddresses = new ConcurrentHashSet<NodeIpAddress>(); // The current batch of new addresses advertised by peers that have not yet been seen.
    protected Long _lastAddressBroadcastTimestamp = 0L;

    public static class Context {
        public Integer maxNodeCount;
        public DatabaseManagerFactory databaseManagerFactory;
        public BitcoinNodeFactory nodeFactory;
        public MutableNetworkTime networkTime;
        public NodeInitializer nodeInitializer;
        public BanFilter banFilter;
        public MemoryPoolEnquirer memoryPoolEnquirer;
        public SynchronizationStatus synchronizationStatusHandler;
        public ThreadPool threadPool;
        public SystemTime systemTime;
    }

    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;
    protected final SynchronizationStatus _synchronizationStatusHandler;
    protected final BitcoinNodeHeadBlockFinder _bitcoinNodeHeadBlockFinder;
    protected final AtomicBoolean _hasHadActiveConnectionSinceLastDisconnect = new AtomicBoolean(false);
    protected final MutableList<String> _dnsSeeds = new MutableList<String>(0);

    protected Boolean _transactionRelayIsEnabled = true;
    protected Boolean _slpValidityCheckingIsEnabled = false;
    protected Boolean _newBlocksViaHeadersIsEnabled = true;
    protected MutableBloomFilter _bloomFilter = null;

    protected final Object _pollForReconnectionThreadMutex = new Object();
    protected final Runnable _pollForReconnection = new Runnable() {
        @Override
        public void run() {
            final long maxWait = (5L * 60L * 1000L); // 5 Minutes...
            long nextWait = 500L;
            while (! Thread.interrupted()) {
                if (_isShuttingDown) { return; }

                try { Thread.sleep(nextWait); }
                catch (final Exception exception) { break; }

                final MutableList<NodeIpAddress> nodeIpAddresses;
                if (_shouldOnlyConnectToSeedNodes) {
                    nodeIpAddresses = new MutableList<NodeIpAddress>(_seedNodes);
                }
                else {
                    final HashSet<String> seedNodeSet = new HashSet<String>();
                    nodeIpAddresses = new MutableList<NodeIpAddress>(0);

                    { // Add seed nodes...
                        for (final NodeIpAddress nodeIpAddress : _seedNodes) {
                            final Ip ip = nodeIpAddress.getIp();
                            final String ipString = ip.toString();
                            final Integer port = nodeIpAddress.getPort();
                            seedNodeSet.add(ipString + port);

                            nodeIpAddresses.add(nodeIpAddress);
                        }
                    }

                    { // Add previously-connected nodes...
                        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

                            final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableList<NodeFeatures.Feature>();
                            requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                            requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                            for (final BitcoinNodeIpAddress nodeIpAddress : nodeDatabaseManager.findNodes(requiredFeatures, _maxNodeCount)) {
                                if (nodeIpAddresses.getCount() >= _maxNodeCount) { break; }
                                nodeIpAddresses.add(nodeIpAddress);
                            }
                        }
                        catch (final DatabaseException databaseException) {
                            Logger.warn(databaseException);
                        }
                    }

                    { // Connect to DNS seeded nodes...
                        final Integer defaultPort = BitcoinProperties.PORT;
                        for (final String seedHost : _dnsSeeds) {
                            final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                            if (seedIps == null) { continue; }

                            for (final Ip ip : seedIps) {
                                if (nodeIpAddresses.getCount() >= _maxNodeCount) { break; }

                                final String host = ip.toString();
                                if (seedNodeSet.contains(host + defaultPort)) { continue; } // Exclude SeedNodes...

                                nodeIpAddresses.add(new NodeIpAddress(ip, defaultPort));
                            }
                        }
                    }
                }

                for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                    final Ip ip = nodeIpAddress.getIp();
                    if (ip == null) { continue; }

                    final String host = ip.toString();
                    final Integer port = nodeIpAddress.getPort();
                    final BitcoinNode bitcoinNode = _nodeFactory.newNode(host, port);

                    _addNode(bitcoinNode); // NOTE: _addNotHandshakedNode(BitcoinNode) is not the same as addNode(BitcoinNode)...

                    Logger.info("All nodes disconnected.  Falling back on previously-seen node: " + host + ":" + ip);
                }

                nextWait = Math.min((2L * nextWait), maxWait);
            }
            _pollForReconnectionThread = null;
        }
    };
    protected Thread _pollForReconnectionThread;

    protected Runnable _onNodeListChanged;
    protected NewNodeCallback _newNodeCallback;

    protected void _pollForReconnection() {
        if (_isShuttingDown) { return; }

        synchronized (_pollForReconnectionThreadMutex) {
            if (_pollForReconnectionThread != null) { return; }

            _pollForReconnectionThread = new Thread(_pollForReconnection);
            _pollForReconnectionThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.error("Uncaught exception in thread.", exception);
                }
            });
            _pollForReconnectionThread.start();
        }
    }

    protected Map<NodeId, BitcoinNode> _getAllHandshakedNodes() {
        final int totalConnectedNodeCount = (_otherNodes.size() + _preferredNodes.size());
        final HashMap<NodeId, BitcoinNode> allConnectedNodes = new HashMap<NodeId, BitcoinNode>(totalConnectedNodeCount);
        allConnectedNodes.putAll(_preferredNodes);
        allConnectedNodes.putAll(_otherNodes);
        return allConnectedNodes;
    }

    protected List<BitcoinNode> _filterNodes(final Map<?, BitcoinNode> nodes, final NodeFilter nodeFilter) {
        return _filterNodes(nodes, nodes.size(), nodeFilter);
    }

    protected List<BitcoinNode> _filterNodes(final Map<?, BitcoinNode> nodes, final Integer requestedNodeCount, final NodeFilter nodeFilter) {
        final MutableList<BitcoinNode> filteredNodes = new MutableList<BitcoinNode>(requestedNodeCount);
        for (final BitcoinNode node : nodes.values()) {
            if (nodeFilter.meetsCriteria(node)) {
                filteredNodes.add(node);
            }
        }
        return filteredNodes;
    }

    protected void _recalculateLocalNodeIpAddress() {
        final HashMap<Ip, Integer> nodeIpAddressCounts = new HashMap<Ip, Integer>(_maxNodeCount / 2);
        final HashMap<Integer, Integer> nodePortCounts = new HashMap<Integer, Integer>(_maxNodeCount / 2);
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode node : allNodes.values()) {
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
            final Iterator<BitcoinNode> pendingNodesIterator = _pendingNodes.values().iterator();
            while (pendingNodesIterator.hasNext()) {
                final BitcoinNode oldPendingNode = pendingNodesIterator.next();

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

    protected void _addHandshakedNode(final BitcoinNode bitcoinNode) {
        if (_isShuttingDown) {
            bitcoinNode.disconnect();
            return;
        }

        if (bitcoinNode.isOutboundConnection()) {
            boolean shouldKeepNode = true;
            final Boolean blockchainIsEnabled = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
            if (! Util.coalesce(blockchainIsEnabled, false)) {
                shouldKeepNode = false;
            }

            final Boolean isBitcoinCashNode = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
            if (! Util.coalesce(isBitcoinCashNode, false)) {
                shouldKeepNode = false;
            }

            final long blockHeightThreshold = 6L;
            final Long currentBlockHeight = Util.coalesce(_synchronizationStatusHandler.getCurrentBlockHeight(), 0L);
            if (currentBlockHeight > 0L) {
                final Long nodeBlockHeight = bitcoinNode.getBlockHeight();
                if (nodeBlockHeight == null) {
                    shouldKeepNode = false;
                }
                else if (nodeBlockHeight < (currentBlockHeight - blockHeightThreshold)) {
                    shouldKeepNode = false;
                }
            }

            if (! shouldKeepNode) {
                bitcoinNode.disconnect();
                return;
            }
        }

        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        final NodeId newNodeId = bitcoinNode.getId();

        if (nodeIpAddress != null) {
            _connectedNodeAddresses.add(nodeIpAddress);
        }

        boolean isPreferredNodeType = true;
        final Boolean isOutboundConnection = bitcoinNode.isOutboundConnection();
        if (isOutboundConnection) {
            if (! Util.coalesce(bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED), false)) {
                isPreferredNodeType = false;
            }
            if (! Util.coalesce(bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BITCOIN_CASH_ENABLED), false)) {
                isPreferredNodeType = false;
            }

            final Long currentBlockHeight = _synchronizationStatusHandler.getCurrentBlockHeight();
            if (currentBlockHeight != null) {
                final Long bitcoinBlockHeight = bitcoinNode.getBlockHeight();
                if (bitcoinBlockHeight < (currentBlockHeight - 6L)) {
                    isPreferredNodeType = false;
                }
            }
        }

        if (isPreferredNodeType) {
            _preferredNodes.put(newNodeId, bitcoinNode);
        }
        else {
            _otherNodes.put(newNodeId, bitcoinNode);
        }
    }

    protected void _addNotHandshakedNode(final BitcoinNode bitcoinNode) {
        final NodeId nodeId = bitcoinNode.getId();
        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();

        if (nodeIpAddress != null) {
            final Ip ip = nodeIpAddress.getIp();
            final Boolean isBanned = _banFilter.isIpBanned(ip);
            if ((_isShuttingDown) || (isBanned)) {
                _removeNode(bitcoinNode);
                return;
            }

            _connectedNodeAddresses.add(nodeIpAddress);
        }

        _cleanupNotHandshakedNodes();

        _pendingNodes.put(nodeId, bitcoinNode);
    }

    protected void _removeNode(final BitcoinNode node) {
        final NodeId nodeId = node.getId();

        Logger.info("Dropped Node: " + node.getConnectionString());

        final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();

        _preferredNodes.remove(nodeId);
        _otherNodes.remove(nodeId);
        _pendingNodes.remove(nodeId);
        if (nodeIpAddress != null) {
            _connectedNodeAddresses.remove(nodeIpAddress);
        }

        node.setDisconnectedCallback(null);
        node.setHandshakeCompleteCallback(null);
        node.setNodeConnectedCallback(null);
        node.setNodeAddressesReceivedCallback(null);

        node.disconnect();

        if (_preferredNodes.isEmpty()) {
            if (! _isShuttingDown) {
                _onAllPreferredNodesDisconnected();
            }
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    /**
     * Returns a list of BitcoinNodes that provide no utility to the NodeManager.
     *  Returns an empty list if there are no nodes that provide zero utility.
     *  Nodes that provide no utility may include non-relaying observer-nodes,
     *  nodes from different chains that have the same network-magic, etc.
     */
    protected List<BitcoinNode> _getUndesirableNodes() {
        return new MutableList<BitcoinNode>(0);
    }

    protected void _broadcastNewNodesToExistingNodes(final List<NodeIpAddress> nodeIpAddresses) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode node : allNodes.values()) {
            node.broadcastNodeAddresses(nodeIpAddresses);

            Logger.debug("Broadcasting " + nodeIpAddresses.getCount() + " new Nodes to existing Node (" + node + ")");
        }
    }

    protected void _broadcastExistingNodesToNewNode(final BitcoinNode newBitcoinNode) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final Collection<BitcoinNode> bitcoinNodes = allNodes.values();

        final MutableList<NodeIpAddress> nodeAddresses = new MutableList<NodeIpAddress>(bitcoinNodes.size());
        for (final BitcoinNode node : bitcoinNodes) {
            final NodeIpAddress nodeIpAddress = node.getRemoteNodeIpAddress();
            if (nodeIpAddress == null) { continue; }

            nodeAddresses.add(nodeIpAddress);

            Logger.debug("Broadcasting Existing Node (" + nodeIpAddress + ") to New Node (" + newBitcoinNode + ")");
        }

        newBitcoinNode.broadcastNodeAddresses(nodeAddresses);
    }

    protected void _onNodeDisconnected(final BitcoinNode bitcoinNode) {
        Logger.debug("Node Disconnected: " + bitcoinNode.getConnectionString());
        _removeNode(bitcoinNode);

        final Ip ip = bitcoinNode.getIp();
        _banFilter.onNodeDisconnected(ip);
    }

    /**
     * Adds the nodeIpAddress as a to-be-connected node, if the node is not a duplicate.
     * To-be-connected nodes will not be available to other threads for creating a new connection.
     *  Returns true if the nodeIpAddress will not be a duplicate connection.
     */
    protected Boolean _markAddressForConnecting(final NodeIpAddress nodeIpAddress) {
        if (nodeIpAddress == null) { return false; }
        return (! _connectedNodeAddresses.add(nodeIpAddress));
    }

    protected Boolean _isConnectedToNode(final NodeIpAddress nodeIpAddress) {
        if (nodeIpAddress == null) { return false; }
        return _connectedNodeAddresses.contains(nodeIpAddress);
    }

    protected void _configureNode(final BitcoinNode bitcoinNode) {
        bitcoinNode.enableTransactionRelay(_transactionRelayIsEnabled);

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

                        Logger.info("Node failed to connect. Purging node: " + bitcoinNode.getConnectionString());

                        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();

                        _pendingNodes.remove(bitcoinNode.getId());
                        if (nodeIpAddress != null) {
                            _connectedNodeAddresses.remove(nodeIpAddress);
                        }

                        bitcoinNode.disconnect();

                        Logger.debug("Node purged.");

                        if (_preferredNodes.isEmpty()) {
                            if (! _isShuttingDown) {
                                _onAllPreferredNodesDisconnected();
                            }
                        }
                    }
                }
                catch (final Exception exception) { }
            }

            @Override
            public String toString() {
                return (BitcoinNodeManager.this.getClass().getSimpleName() + "." + "TimeoutRunnable"); // Used as a hack within FakeThreadPools...
            }
        };

        { // Determine external address via peers...
            _recalculateLocalNodeIpAddress();
            final NodeIpAddress reportedLocalNodeIpAddress = _localNodeIpAddress;
            if (reportedLocalNodeIpAddress != null) {
                bitcoinNode.setLocalNodeIpAddress(reportedLocalNodeIpAddress);
            }
        }

        bitcoinNode.setNodeAddressesReceivedCallback(new BitcoinNode.NodeAddressesReceivedCallback() {
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

                { // Batch at least 30 seconds worth of new NodeIpAddresses, then broadcast the group to current peers...
                    final Long now = _systemTime.getCurrentTimeInMilliSeconds();
                    final long msElapsedSinceLastBroadcast = (now - _lastAddressBroadcastTimestamp);
                    if (msElapsedSinceLastBroadcast >= 30000L) {
                        _lastAddressBroadcastTimestamp = now;

                        final List<NodeIpAddress> newNodeAddresses = new MutableList<NodeIpAddress>(_newNodeAddresses);
                        _newNodeAddresses.clear();

                        _broadcastNewNodesToExistingNodes(newNodeAddresses);
                    }
                }

                if (! _shouldOnlyConnectToSeedNodes) {
                    // Connect to the node if the NodeManager is still looking for peers...
                    for (final NodeIpAddress nodeIpAddress : unseenNodeAddresses) {
                        final int potentialNodeCount = (_pendingNodes.size() + _preferredNodes.size() + _otherNodes.size());
                        if (potentialNodeCount >= _maxNodeCount) { break; }

                        final Ip ip = nodeIpAddress.getIp();
                        final String host = ip.toString();
                        final Integer port = nodeIpAddress.getPort();

                        final Boolean isAlreadyConnectedToNode = _markAddressForConnecting(nodeIpAddress);
                        if (isAlreadyConnectedToNode) { continue; }

                        final BitcoinNode newBitcoinNode = _nodeFactory.newNode(host, port);
                        _addNode(newBitcoinNode);
                    }
                }
            }
        });

        bitcoinNode.setNodeConnectedCallback(new BitcoinNode.NodeConnectedCallback() {
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

                _onNodeConnected(bitcoinNode);
            }
        });

        bitcoinNode.setHandshakeCompleteCallback(new Node.HandshakeCompleteCallback() {
            @Override
            public void onHandshakeComplete() {
                Logger.debug("HandshakeComplete: " + bitcoinNode.getConnectionString());

                _pendingNodes.remove(bitcoinNode.getId());
                _addHandshakedNode(bitcoinNode);

                final Long nodeNetworkTimeOffset = bitcoinNode.getNetworkTimeOffset();
                if (nodeNetworkTimeOffset != null) {
                    _networkTime.includeOffsetInSeconds(nodeNetworkTimeOffset);
                }

                _broadcastExistingNodesToNewNode(bitcoinNode);
                _onNodeHandshakeComplete(bitcoinNode);
            }
        });

        bitcoinNode.setDisconnectedCallback(new Node.DisconnectedCallback() {
            @Override
            public void onNodeDisconnected() {
                _onNodeDisconnected(bitcoinNode);
            }
        });

        _nodeInitializer.initializeNode(bitcoinNode);

        bitcoinNode.connect();
        bitcoinNode.handshake();

        _threadPool.execute(timeoutRunnable);
    }

    protected void _pingIdleNodes() {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final MutableList<BitcoinNode> idleNodes = new MutableList<BitcoinNode>();
        for (final BitcoinNode node : allNodes.values()) {
            final Long lastMessageTime = node.getLastMessageReceivedTimestamp();
            final long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

            if (idleDuration > PING_AFTER_MS_IDLE) {
                idleNodes.add(node);
            }
        }

        Logger.debug("Idle Node Count: " + idleNodes.getCount() + " / " + allNodes.size());

        for (final BitcoinNode idleNode : idleNodes) {
            if (! idleNode.handshakeIsComplete()) { return; }

            Logger.debug("Pinging Idle Node: " + idleNode.getConnectionString());

            idleNode.ping(new BitcoinNode.PingCallback() {
                @Override
                public void onResult(final Long pingInMilliseconds) {
                    Logger.debug("Node Pong: " + pingInMilliseconds);
                }
            });
        }
    }

    protected void _removeDisconnectedNodes() {
        final MutableList<BitcoinNode> purgeableNodes = new MutableList<BitcoinNode>();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode node : allNodes.values()) {
            if (! node.isConnected()) {
                final long nodeAge = (_systemTime.getCurrentTimeInMilliSeconds() - node.getInitializationTimestamp());
                if (nodeAge > 10000L) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final BitcoinNode node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _removeHighLatencyNodes() {
        final MutableList<BitcoinNode> purgeableNodes = new MutableList<BitcoinNode>();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode node : allNodes.values()) {
            if (node.isConnected()) {
                final Long nodePing = node.getAveragePing();
                if ( (nodePing != null) && (nodePing > 10000L) ) {
                    purgeableNodes.add(node);
                }
            }
        }

        for (final BitcoinNode node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _addNode(final BitcoinNode bitcoinNode) {
        if (_isShuttingDown) {
            bitcoinNode.disconnect();
            return;
        }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            nodeDatabaseManager.storeNode(bitcoinNode);
        }
        catch (final DatabaseException databaseException) {
            Logger.warn(databaseException);
        }

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();

        // Ensure the NodeManager does not exceed maxNodeCount.
        if ((allNodes.size() + _pendingNodes.size()) >= _maxNodeCount) {
            bitcoinNode.disconnect();
            return;
        }

        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        final boolean isAlreadyConnectedToAddress = _markAddressForConnecting(nodeIpAddress);
        if (isAlreadyConnectedToAddress) {
            final NodeId nodeId = bitcoinNode.getId();
            final boolean isDuplicateCallToAddNode = ( (allNodes.containsKey(nodeId)) || (_pendingNodes.containsKey(nodeId)) );
            if (isDuplicateCallToAddNode) { return; }

            // The bitcoinNode is actually a new connection to a node that we're already connected to
            bitcoinNode.disconnect();
            return;
        }

        _configureNode(bitcoinNode);
        _addNotHandshakedNode(bitcoinNode);
    }

    protected void _onAllPreferredNodesDisconnected() {
        if (! _hasHadActiveConnectionSinceLastDisconnect.getAndSet(false)) { return; } // Prevent infinitely looping by aborting if no new connections were successful since the last attempt...
        _pollForReconnection();
    }

    protected void _onNodeConnected(final BitcoinNode bitcoinNode) {
        _hasHadActiveConnectionSinceLastDisconnect.set(true); // Allow for reconnection attempts after all connections die...

        { // Abort the reconnection Thread, if it is running...
            final Thread pollForReconnectionThread = _pollForReconnectionThread;
            if (pollForReconnectionThread != null) {
                pollForReconnectionThread.interrupt();
            }
        }

        bitcoinNode.ping(null);

        final BloomFilter bloomFilter = _bloomFilter;
        if (bloomFilter != null) {
            bitcoinNode.setBloomFilter(bloomFilter);
        }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
            final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();

            bitcoinNode.transmitBlockFinder(blockFinderHashes);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    protected void _onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
        if (_slpValidityCheckingIsEnabled) {
            if (Util.coalesce(bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.SLP_INDEX_ENABLED), false)) {
                bitcoinNode.enableSlpValidityChecking(true);
            }
        }

        if (_newBlocksViaHeadersIsEnabled) {
            bitcoinNode.enableNewBlockViaHeaders();
        }

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            nodeDatabaseManager.updateLastHandshake(bitcoinNode); // WARNING: If removing Last Handshake update, ensure BanFilter no longer requires the handshake timestamp...
            nodeDatabaseManager.updateNodeFeatures(bitcoinNode);
            nodeDatabaseManager.updateUserAgent(bitcoinNode);
        }
        catch (final DatabaseException databaseException) {
            Logger.debug(databaseException);
        }

        final NewNodeCallback newNodeCallback = _newNodeCallback;
        if (newNodeCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    newNodeCallback.onNodeHandshakeComplete(bitcoinNode);
                }
            });
        }

        _banFilter.onNodeHandshakeComplete(bitcoinNode);

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    public void setDefaultExternalPort(final Integer externalPortNumber) {
        _defaultExternalPort = externalPortNumber;
    }

    public void defineSeedNode(final NodeIpAddress nodeIpAddress) {
        _seedNodes.add(nodeIpAddress);
    }

    public void addNode(final BitcoinNode node) {
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

    public List<NodeId> getNodeIds() {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return new ImmutableList<NodeId>(allNodes.keySet());
    }

    public BitcoinNode getNode(final NodeId nodeId) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return allNodes.get(nodeId);
    }

    public BitcoinNode getNode(final NodeFilter nodeFilter) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final List<BitcoinNode> filteredNodes = _filterNodes(allNodes, nodeFilter);
        if (filteredNodes == null) { return null; }
        return filteredNodes.get(0);
    }

    public List<BitcoinNode> getPreferredNodes() {
        return new ImmutableList<BitcoinNode>(_preferredNodes.values());
    }

    public List<BitcoinNode> getPreferredNodes(final NodeFilter nodeFilter) {
        return _filterNodes(_preferredNodes, nodeFilter);
    }

    public List<BitcoinNode> getNodes() {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return new ImmutableList<BitcoinNode>(allNodes.values());
    }

    public List<BitcoinNode> getNodes(final NodeFilter nodeFilter) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return _filterNodes(allNodes, nodeFilter);
    }

    /**
     * Returns true if the NodeManager is connected, or is connecting, to a node at the provided Ip and port.
     */
    public Boolean isConnectedToNode(final NodeIpAddress nodeIpAddress) {
        return _isConnectedToNode(nodeIpAddress);
    }

    public Integer getActiveNodeCount() {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final List<BitcoinNode> filteredNodes = _filterNodes(allNodes, NodeFilters.KEEP_ACTIVE_NODES);
        return filteredNodes.getCount();
    }

    public void setMaxNodeCount(final Integer maxNodeCount) {
        _maxNodeCount = maxNodeCount;
    }

    public void setShouldOnlyConnectToSeedNodes(final Boolean shouldOnlyConnectToSeedNodes) {
        _shouldOnlyConnectToSeedNodes = shouldOnlyConnectToSeedNodes;
    }

    public void shutdown() {
        _isShuttingDown = true;

        final Thread pollForReconnectionThread = _pollForReconnectionThread;
        if (pollForReconnectionThread != null) {
            pollForReconnectionThread.interrupt();

            try { pollForReconnectionThread.join(5000L); } catch (final Exception exception) { }
        }

        final Map<NodeId, BitcoinNode> allHandshakedNodes = _getAllHandshakedNodes();
        final MutableList<BitcoinNode> allNodes = new MutableList<BitcoinNode>(allHandshakedNodes.values());
        allNodes.addAll(_pendingNodes.values());

        for (final BitcoinNode bitcoinNode : allNodes) {
            _removeNode(bitcoinNode);
        }
    }

    public BitcoinNodeManager(final Context context) {
        _systemTime = context.systemTime;
        _preferredNodes = new ConcurrentHashMap<NodeId, BitcoinNode>(context.maxNodeCount);
        _otherNodes = new ConcurrentHashMap<NodeId, BitcoinNode>(context.maxNodeCount);

        _maxNodeCount = context.maxNodeCount;
        _nodeFactory = context.nodeFactory;
        _networkTime = context.networkTime;
        _threadPool = context.threadPool;

        _databaseManagerFactory = context.databaseManagerFactory;
        _nodeInitializer = context.nodeInitializer;
        _banFilter = context.banFilter;
        _memoryPoolEnquirer = context.memoryPoolEnquirer;
        _synchronizationStatusHandler = context.synchronizationStatusHandler;

        _bitcoinNodeHeadBlockFinder = new BitcoinNodeHeadBlockFinder(_databaseManagerFactory, _threadPool, _banFilter);
    }

    public Boolean hasBloomFilter() {
        return (_bloomFilter != null);
    }

    public void setBloomFilter(final MutableBloomFilter bloomFilter) {
        _bloomFilter = bloomFilter;

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode bitcoinNode : allNodes.values()) {
            bitcoinNode.setBloomFilter(_bloomFilter);
        }
    }

    public void banNode(final Ip ip) {
        _banFilter.banIp(ip);

        // Disconnect all currently-connected nodes at that ip...
        final MutableList<BitcoinNode> droppedNodes = new MutableList<BitcoinNode>();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode bitcoinNode : allNodes.values()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        // Disconnect all pending nodes at that ip...
        for (final BitcoinNode bitcoinNode : _pendingNodes.values()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        for (final BitcoinNode bitcoinNode : droppedNodes) {
            _removeNode(bitcoinNode);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
    }

    public void unbanNode(final Ip ip) {
        _banFilter.unbanIp(ip);
    }

    public void addIpToWhitelist(final Ip ip) {
        _banFilter.addIpToWhitelist(ip);
    }

    public void removeIpFromWhitelist(final Ip ip) {
        _banFilter.removeIpFromWhitelist(ip);
    }

    public void setNodeListChangedCallback(final Runnable callback) {
        _onNodeListChanged = callback;
    }

    public void setNewNodeHandshakedCallback(final NewNodeCallback newNodeCallback) {
        _newNodeCallback = newNodeCallback;
    }

    public void enableTransactionRelay(final Boolean transactionRelayIsEnabled) {
        _transactionRelayIsEnabled = transactionRelayIsEnabled;

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode bitcoinNode : allNodes.values()) {
            bitcoinNode.enableTransactionRelay(transactionRelayIsEnabled);
        }
    }

    public Boolean isTransactionRelayEnabled() {
        return _transactionRelayIsEnabled;
    }

    public void enableSlpValidityChecking(final Boolean shouldEnableSlpValidityChecking) {
        _slpValidityCheckingIsEnabled = shouldEnableSlpValidityChecking;
    }

    public Boolean isSlpValidityCheckingEnabled() {
        return _slpValidityCheckingIsEnabled;
    }

    public void enableNewBlockViaHeaders(final Boolean newBlocksViaHeadersIsEnabled) {
        _newBlocksViaHeadersIsEnabled = newBlocksViaHeadersIsEnabled;
        if (newBlocksViaHeadersIsEnabled) {
            final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
            for (final BitcoinNode bitcoinNode : allNodes.values()) {
                bitcoinNode.enableNewBlockViaHeaders();
            }
        }
    }

    public void defineDnsSeeds(final List<String> dnsSeeds) {
        _dnsSeeds.addAll(dnsSeeds);
    }
}

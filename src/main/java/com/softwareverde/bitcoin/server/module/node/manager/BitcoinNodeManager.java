package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.async.ConcurrentHashSet;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.configuration.BitcoinProperties;
import com.softwareverde.bitcoin.server.message.type.MessageType;
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
import com.softwareverde.bitcoin.server.node.BitcoinNodeObserver;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

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

    public static class Context {
        public Integer minNodeCount;
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

    protected static class NodePerformance {
        public final Long connectionTimestampMs;
        public final AtomicLong byteCountReceived = new AtomicLong(0L);
        public final AtomicLong byteCountSent = new AtomicLong(0L);
        public final AtomicLong requestsReceivedCount = new AtomicLong(0L);
        public final AtomicLong requestsFilledCount = new AtomicLong(0L);
        public final AtomicLong failedRequestCount = new AtomicLong(0L);

        public NodePerformance(final Long now) {
            this.connectionTimestampMs = now;
        }
    }

    protected final SystemTime _systemTime;
    protected final BitcoinNodeFactory _nodeFactory;
    protected final ThreadPool _threadPool;

    protected final ConcurrentHashMap<NodeId, BitcoinNode> _preferredNodes;
    protected final ConcurrentHashMap<NodeId, BitcoinNode> _otherNodes;
    protected ConcurrentHashMap<NodeId, BitcoinNode> _pendingNodes = new ConcurrentHashMap<>(); // Nodes that have been added but have not yet completed their handshake.

    // _connectedNodeAddresses contains NodeIpAddresses that are either currently-connected, are pending handshake, or are about to be connected to.
    // All methods about to connect to a node should ensure the node will not be a duplicate by checking _connectedNodeAddresses for an existing entry.
    protected final ConcurrentHashSet<NodeIpAddress> _connectedNodeAddresses = new ConcurrentHashSet<>();
    protected final Map<BitcoinNode, NodePerformance> _performanceStatistics = new WeakHashMap<>();

    protected final ConcurrentHashSet<NodeIpAddress> _seedNodes = new ConcurrentHashSet<>();
    protected final MutableNetworkTime _networkTime;
    protected final BitcoinNodeObserver _bitcoinNodeObserver;
    protected Integer _minNodeCount;
    protected Integer _maxNodeCount;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;
    protected volatile Boolean _isShuttingDown = false;

    protected Integer _defaultExternalPort = BitcoinProperties.PORT;
    protected NodeIpAddress _localNodeIpAddress = null;

    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;
    protected final SynchronizationStatus _synchronizationStatusHandler;
    protected final BitcoinNodeHeadBlockFinder _bitcoinNodeHeadBlockFinder;
    protected final MutableList<String> _dnsSeeds = new MutableList<String>(0);

    protected final Object _threadMutex = new Object();
    protected Thread _nodeMaintenanceThread;
    protected Thread _preferredPeerMonitorThread;

    protected Boolean _transactionRelayIsEnabled = true;
    protected Boolean _slpValidityCheckingIsEnabled = false;
    protected Boolean _newBlocksViaHeadersIsEnabled = true;
    protected MutableBloomFilter _bloomFilter = null;
    protected Runnable _onNodeListChanged;
    protected NewNodeCallback _newNodeCallback;

    protected final Runnable _preferredPeerMonitor = new Runnable() {
        @Override
        public void run() {
            try {
                final long minWait = 2500L;
                final long maxWait = (60L * 1000L); // 1 Minute...
                long nextWait = minWait;
                while (! Thread.interrupted()) {
                    if (_isShuttingDown) { return; }

                    if (Logger.isTraceEnabled()) {
                        Logger.trace("PeerMonitor: _preferredNodes=" + _preferredNodes.size() + ", _otherNodes=" + _otherNodes.size() + ", _pendingNodes=" + _pendingNodes.size());
                    }

                    synchronized (_performanceStatistics) {
                        for (final Map.Entry<BitcoinNode, NodePerformance> entry : _performanceStatistics.entrySet()) {
                            final BitcoinNode bitcoinNode = entry.getKey();
                            final NodePerformance nodePerformance = entry.getValue();
                            if ( (bitcoinNode == null) || (nodePerformance == null) ) { continue; }

                            if (Logger.isTraceEnabled()) {
                                Logger.trace(bitcoinNode + " - failedRequestCount=" + nodePerformance.failedRequestCount + ", requestsFilledCount=" + nodePerformance.requestsFilledCount + ", requestsReceivedCount=" + nodePerformance.requestsReceivedCount + ", byteCountReceived=" + nodePerformance.byteCountReceived + ", byteCountSent=" + nodePerformance.byteCountSent);
                            }

                            final long failedRequestCount = nodePerformance.failedRequestCount.get();
                            final long fulfilledRequestCount = nodePerformance.requestsFilledCount.get();
                            if ( (failedRequestCount > 0) && (failedRequestCount >= (fulfilledRequestCount * 0.15D)) ) {
                                final NodeId nodeId = bitcoinNode.getId();
                                _preferredNodes.remove(nodeId);
                                _otherNodes.put(nodeId, bitcoinNode);

                                Logger.debug("Demoting node: " + bitcoinNode);
                            }
                        }
                    }

                    final int preferredNodeCount = _preferredNodes.size();
                    final int peerCount = (preferredNodeCount + _otherNodes.size() + _pendingNodes.size());
                    if (peerCount == 0) {
                        nextWait = minWait;
                    }

                    if (preferredNodeCount < _minNodeCount) {
                        _connectToNewPreferredNodes();
                    }
                    else {
                        nextWait = maxWait;
                    }

                    try { Thread.sleep(nextWait); }
                    catch (final Exception exception) { break; }

                    nextWait = Math.min((2L * nextWait), maxWait);
                }
            }
            finally {
                synchronized (_threadMutex) {
                    if (_preferredPeerMonitorThread == Thread.currentThread()) {
                        _preferredPeerMonitorThread = null;
                    }
                }
            }
        }
    };

    protected final Runnable _nodeMaintenanceRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                while (! Thread.interrupted()) {
                    _pingIdleNodes();
                    _removeDisconnectedNodes();
                    _removeHighLatencyNodes();

                    try { Thread.sleep(10000L); } catch (final Exception exception) { break; }
                }
            }
            finally {
                synchronized (_threadMutex) {
                    if (_nodeMaintenanceThread == Thread.currentThread()) {
                        _nodeMaintenanceThread = null;
                    }
                }
            }
        }
    };

    protected void _connectToNewPreferredNodes() {
        final Integer defaultPort = _defaultExternalPort;

        final int currentPeerCount;
        final HashSet<String> excludeSet = new HashSet<String>();
        { // Exclude currently connected nodes and pending nodes...
            final Map<NodeId, BitcoinNode> bitcoinNodes = _getAllHandshakedNodes();
            bitcoinNodes.putAll(_pendingNodes);

            for (final BitcoinNode bitcoinNode : bitcoinNodes.values()) {
                final String connectionString = (bitcoinNode.getHost() + bitcoinNode.getPort()); // NOTE: not the same as BitcoinNode::getConnectionString.
                excludeSet.add(connectionString);
            }
        }

        final int preferredNodeCount = _preferredNodes.size();

        final int newPreferredNodeCountTarget = (_minNodeCount - preferredNodeCount);
        if (newPreferredNodeCountTarget <= 0) { return; }

        final MutableList<NodeIpAddress> nodeIpAddresses = new MutableList<NodeIpAddress>(_seedNodes);
        { // Add seed nodes...
            for (final NodeIpAddress nodeIpAddress : _seedNodes) {
                final Ip ip = nodeIpAddress.getIp();
                final String ipString = ip.toString();
                final Integer port = nodeIpAddress.getPort();

                final String connectionString = (ipString + port);
                final boolean isUnique = excludeSet.add(connectionString);
                if (! isUnique) { continue; }

                nodeIpAddresses.add(nodeIpAddress);
            }
        }

        final boolean shouldConnectToUnknownPeers = (! _shouldOnlyConnectToSeedNodes);
        if (shouldConnectToUnknownPeers) {
            { // Add previously-connected nodes...
                try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

                    final List<BitcoinNodeIpAddress> foundNodeIpAddresses;
                    { // Look for nodes that would be good preferred nodes, falling back to unknown nodes if there aren't enough available.
                        final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableList<NodeFeatures.Feature>();
                        {
                            requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                            requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                        }

                        final List<BitcoinNodeIpAddress> possiblePreferredNodes = nodeDatabaseManager.findNodes(requiredFeatures, (newPreferredNodeCountTarget * 2));
                        if (possiblePreferredNodes.getCount() >= newPreferredNodeCountTarget) {
                            foundNodeIpAddresses = possiblePreferredNodes;
                        }
                        else { // If there weren't enough preferred nodes, then relax the requirements to include unknown peers.
                            final int underflowDesiredNodeCount = ((newPreferredNodeCountTarget * 2) - possiblePreferredNodes.getCount());
                            final List<BitcoinNodeIpAddress> unknownBitcoinNodeIpAddresses = nodeDatabaseManager.findNodes(underflowDesiredNodeCount);

                            final MutableList<BitcoinNodeIpAddress> newBitcoinNodeIpAddresses = new MutableList<BitcoinNodeIpAddress>();
                            newBitcoinNodeIpAddresses.addAll(possiblePreferredNodes);
                            newBitcoinNodeIpAddresses.addAll(unknownBitcoinNodeIpAddresses);
                            foundNodeIpAddresses = newBitcoinNodeIpAddresses;
                        }
                    }

                    for (final BitcoinNodeIpAddress nodeIpAddress : foundNodeIpAddresses) {
                        if (nodeIpAddresses.getCount() >= newPreferredNodeCountTarget) { break; }

                        final Ip ip = nodeIpAddress.getIp();
                        final String ipString = ip.toString();
                        final Integer port = nodeIpAddress.getPort();

                        if (Util.areEqual(defaultPort, port)) { continue; }

                        final String connectionString = (ipString + port);
                        final boolean isUnique = excludeSet.add(connectionString);
                        if (! isUnique) { continue; }

                        nodeIpAddresses.add(nodeIpAddress);
                    }
                }
                catch (final DatabaseException databaseException) {
                    Logger.warn(databaseException);
                }
            }

            { // Connect to DNS seeded nodes...
                for (final String seedHost : _dnsSeeds) {
                    final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                    if (seedIps == null) { continue; }

                    for (final Ip ip : seedIps) {
                        if (nodeIpAddresses.getCount() >= newPreferredNodeCountTarget) { break; }

                        final String ipString = ip.toString();
                        final Integer port = defaultPort;

                        final String connectionString = (ipString + port);
                        final boolean isUnique = excludeSet.add(connectionString);
                        if (! isUnique) { continue; }

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

            // The BitcoinNode will be added as a preferred node iff it meets the required criteria after the handshake is complete.
            _addNode(bitcoinNode); // NOTE: _addNotHandshakedNode(BitcoinNode) is not the same as addNode(BitcoinNode)...

            Logger.info("Connecting to: " + host + ":" + ip);
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
        final HashMap<Ip, Integer> nodeIpAddressCounts = new HashMap<>();
        final HashMap<Integer, Integer> nodePortCounts = new HashMap<>();
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

    protected void _removeNode(final BitcoinNode bitcoinNode) {
        final NodeId nodeId = bitcoinNode.getId();

        Logger.info("Dropped Node: " + bitcoinNode.getConnectionString());

        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();

        _preferredNodes.remove(nodeId);
        _otherNodes.remove(nodeId);
        _pendingNodes.remove(nodeId);
        if (nodeIpAddress != null) {
            _connectedNodeAddresses.remove(nodeIpAddress);
        }

        bitcoinNode.setDisconnectedCallback(null);
        bitcoinNode.setHandshakeCompleteCallback(null);
        bitcoinNode.setNodeConnectedCallback(null);
        bitcoinNode.setNodeAddressesReceivedCallback(null);

        bitcoinNode.disconnect();

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            _threadPool.execute(onNodeListChangedCallback);
        }
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

                try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final BitcoinNodeDatabaseManager bitcoinNodeDatabaseManager = databaseManager.getNodeDatabaseManager();
                    for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                        final Integer port = nodeIpAddress.getPort();

                        if (! Util.areEqual(_defaultExternalPort, port)) { return; }
                        bitcoinNodeDatabaseManager.storeAddress(nodeIpAddress);
                    }
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
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

        bitcoinNode.addObserver(_bitcoinNodeObserver);

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

    protected void _onNodeConnected(final BitcoinNode bitcoinNode) {
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
        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            nodeDatabaseManager.updateLastHandshake(bitcoinNode); // WARNING: If removing Last Handshake update, ensure BanFilter no longer requires the handshake timestamp...
            nodeDatabaseManager.updateNodeFeatures(bitcoinNode);
            nodeDatabaseManager.updateUserAgent(bitcoinNode);
        }
        catch (final DatabaseException databaseException) {
            Logger.debug(databaseException);
        }

        _banFilter.onNodeHandshakeComplete(bitcoinNode);
        if (! bitcoinNode.isConnected()) { return; } // Node was banned.

        if (_slpValidityCheckingIsEnabled) {
            if (Util.coalesce(bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.SLP_INDEX_ENABLED), false)) {
                bitcoinNode.enableSlpValidityChecking(true);
            }
        }

        if (_newBlocksViaHeadersIsEnabled) {
            bitcoinNode.enableNewBlockViaHeaders();
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

    public void removeNode(final BitcoinNode node) {
        _removeNode(node);
    }

    public NetworkTime getNetworkTime() {
        return _networkTime;
    }

    public void start() {
        if (_isShuttingDown) { return; }

        synchronized (_threadMutex) {
            {
                if (_nodeMaintenanceThread != null) {
                    _nodeMaintenanceThread.interrupt();
                }

                _nodeMaintenanceThread = new Thread(_nodeMaintenanceRunnable);
                _nodeMaintenanceThread.setName("BitcoinNodeManager - NodeMaintenanceThread");
                _nodeMaintenanceThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread thread, final Throwable exception) {
                        Logger.error("Uncaught exception in NodeMaintenanceThread.", exception);
                    }
                });
                _nodeMaintenanceThread.start();
            }

            {
                if (_preferredPeerMonitorThread != null) {
                    _preferredPeerMonitorThread.interrupt();
                }

                _preferredPeerMonitorThread = new Thread(_preferredPeerMonitor);
                _preferredPeerMonitorThread.setName("BitcoinNodeManager - PreferredPeerMonitorThread");
                _preferredPeerMonitorThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(final Thread thread, final Throwable exception) {
                        Logger.error("Uncaught exception in PreferredPeerMonitorThread.", exception);
                    }
                });
                _preferredPeerMonitorThread.start();
            }
        }
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

    public List<BitcoinNode> getUnpreferredNodes() {
        return new ImmutableList<BitcoinNode>(_otherNodes.values());
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

    public void setMinNodeCount(final Integer minNodeCount) {
        _minNodeCount = minNodeCount;
    }

    public Integer getMinNodeCount() {
        return _minNodeCount;
    }

    public void setMaxNodeCount(final Integer maxNodeCount) {
        _maxNodeCount = maxNodeCount;
    }

    public Integer getMaxNodeCount() {
        return _maxNodeCount;
    }

    public void setShouldOnlyConnectToSeedNodes(final Boolean shouldOnlyConnectToSeedNodes) {
        _shouldOnlyConnectToSeedNodes = shouldOnlyConnectToSeedNodes;
    }

    public void shutdown() {
        _isShuttingDown = true;

        final Thread nodeMaintenanceThread = _nodeMaintenanceThread;
        if (nodeMaintenanceThread != null) {
            nodeMaintenanceThread.interrupt();
            try { nodeMaintenanceThread.join(5000L); } catch (final Exception exception) { }
        }

        final Thread preferredPeerMonitorThread = _preferredPeerMonitorThread;
        if (preferredPeerMonitorThread != null) {
            preferredPeerMonitorThread.interrupt();
            try { preferredPeerMonitorThread.join(5000L); } catch (final Exception exception) { }
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

        _minNodeCount = context.minNodeCount;
        _maxNodeCount = context.maxNodeCount;
        _nodeFactory = context.nodeFactory;
        _networkTime = context.networkTime;
        _threadPool = context.threadPool;

        _bitcoinNodeObserver = new BitcoinNodeObserver() {

            protected NodePerformance _getNodePerformance(final BitcoinNode bitcoinNode) {
                synchronized (_performanceStatistics) {
                    NodePerformance nodePerformance = _performanceStatistics.get(bitcoinNode);
                    if (nodePerformance != null) { return nodePerformance; }

                    final Long nowMs = _systemTime.getCurrentTimeInMilliSeconds();
                    nodePerformance = new NodePerformance(nowMs);
                    _performanceStatistics.put(bitcoinNode, nodePerformance);
                    return nodePerformance;
                }
            }

            @Override
            public void onDataRequested(final BitcoinNode bitcoinNode, final MessageType messageType) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                nodePerformance.requestsReceivedCount.incrementAndGet();
            }

            @Override
            public void onDataReceived(final BitcoinNode bitcoinNode, final MessageType messageType, final Integer byteCount, final Boolean wasRequested) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                nodePerformance.requestsFilledCount.incrementAndGet();
                nodePerformance.byteCountReceived.addAndGet(byteCount);
            }

            @Override
            public void onDataSent(final BitcoinNode bitcoinNode, final MessageType messageType, final Integer byteCount) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                nodePerformance.byteCountSent.addAndGet(byteCount);
            }

            @Override
            public void onFailedRequest(final BitcoinNode bitcoinNode, final MessageType expectedResponseType) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                nodePerformance.failedRequestCount.incrementAndGet();
            }
        };

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

    public void addToWhitelist(final Ip ip) {
        _banFilter.addToWhitelist(ip);
    }

    public void removeIpFromWhitelist(final Ip ip) {
        _banFilter.removeIpFromWhitelist(ip);
    }

    public void addToUserAgentBlacklist(final Pattern pattern) {
        _banFilter.addToUserAgentBlacklist(pattern);
    }

    public void removeUserAgentFromBlacklist(final Pattern pattern) {
        _banFilter.removePatternFromUserAgentBlacklist(pattern);
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

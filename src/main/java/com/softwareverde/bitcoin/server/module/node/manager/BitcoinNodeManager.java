package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.MemoryPoolEnquirer;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilter;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeHeadBlockFinder;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.server.node.BitcoinNodeObserver;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.dsproof.DoubleSpendProofWithTransactions;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.constable.map.Map;
import com.softwareverde.constable.map.mutable.ConcurrentMutableHashMap;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.map.mutable.MutableWeakHashMap;
import com.softwareverde.constable.set.mutable.ConcurrentMutableHashSet;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.Node;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Collections;
import java.util.Comparator;
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
        public Boolean shouldPrioritizeNewConnections;
        public BitcoinNodeFactory nodeFactory;
        public MutableNetworkTime networkTime;
        public Blockchain blockchain;
        public NodeInitializer nodeInitializer;
        public BanFilter banFilter;
        public MemoryPoolEnquirer memoryPoolEnquirer;
        public SynchronizationStatus synchronizationStatusHandler;
        public SystemTime systemTime;
        public BitcoinNodeStore bitcoinNodeStore;
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

    protected static Boolean isBitcoinCashFullNode(final BitcoinNode bitcoinNode) {
        final Boolean blockchainIsEnabled = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
        if (! Util.coalesce(blockchainIsEnabled, false)) {
            return false;
        }

        final Boolean isBitcoinCashNode = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
        if (! Util.coalesce(isBitcoinCashNode, false)) {
            return false;
        }

        return true;
    }

    protected static void runAsync(final Runnable runnable) {
        final Thread thread = new Thread(runnable);
        thread.setName("BlockHeaderDownloader Callback");
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        thread.start();
    }

    protected final SystemTime _systemTime;
    protected final BitcoinNodeFactory _nodeFactory;
    protected final Blockchain _blockchain;
    protected final BitcoinNodeStore _bitcoinNodeStore;

    protected final ConcurrentMutableHashMap<NodeId, BitcoinNode> _preferredNodes;
    protected final ConcurrentMutableHashMap<NodeId, BitcoinNode> _otherNodes;
    protected final ConcurrentMutableHashMap<NodeId, BitcoinNode> _pendingNodes = new ConcurrentMutableHashMap<>(); // Nodes that have been added but have not yet completed their handshake.

    // _connectedNodeAddresses contains NodeIpAddresses that are either currently-connected, are pending handshake, or are about to be connected to.
    // All methods about to connect to a node should ensure the node will not be a duplicate by checking _connectedNodeAddresses for an existing entry.
    protected final ConcurrentMutableHashSet<NodeIpAddress> _connectedNodeAddresses = new ConcurrentMutableHashSet<>();
    protected final MutableWeakHashMap<BitcoinNode, NodePerformance> _performanceStatistics = new MutableWeakHashMap<>();

    protected final ConcurrentMutableHashSet<NodeIpAddress> _seedNodes = new ConcurrentMutableHashSet<>();
    protected final MutableNetworkTime _networkTime;
    protected final BitcoinNodeObserver _bitcoinNodeObserver;
    protected Integer _minNodeCount;
    protected Integer _maxNodeCount;
    protected Boolean _shouldOnlyConnectToSeedNodes = false;
    protected Boolean _shouldPrioritizeNewConnections = false;
    protected volatile Boolean _isShuttingDown = false;

    protected Integer _defaultExternalPort = BitcoinConstants.getDefaultNetworkPort();
    protected NodeIpAddress _localNodeIpAddress = null;

    protected final NodeInitializer _nodeInitializer;
    protected final BanFilter _banFilter;
    protected final MemoryPoolEnquirer _memoryPoolEnquirer;
    protected final SynchronizationStatus _synchronizationStatusHandler;
    protected final BitcoinNodeHeadBlockFinder _bitcoinNodeHeadBlockFinder;
    protected final MutableList<String> _dnsSeeds = new MutableArrayList<>(0);

    protected final Object _threadMutex = new Object();
    protected Thread _nodeMaintenanceThread;
    protected Thread _preferredPeerMonitorThread;

    protected Boolean _transactionRelayIsEnabled = true;
    protected Boolean _newBlocksViaHeadersIsEnabled = true;
    protected MutableBloomFilter _bloomFilter = null;
    protected Runnable _onNodeListChanged;
    protected NewNodeCallback _newNodeCallback;
    protected Boolean _fastSyncIsEnabled = false;

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
                        Logger.trace("PeerMonitor: _preferredNodes=" + _preferredNodes.getCount() + ", _otherNodes=" + _otherNodes.getCount() + ", _pendingNodes=" + _pendingNodes.getCount());
                    }

                    synchronized (_performanceStatistics) {
                        _preferredNodes.visit(new Visitor<Tuple<NodeId, BitcoinNode>>() {
                            @Override
                            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                                final BitcoinNode preferredNode = mapEntry.second;
                                final NodePerformance nodePerformance = _performanceStatistics.get(preferredNode);
                                if ( (preferredNode == null) || (nodePerformance == null) ) { return true; }

                                if (_preferredNodes.containsKey(preferredNode.getId())) {
                                    if (Logger.isTraceEnabled()) {
                                        Logger.trace(preferredNode + " - failedRequestCount=" + nodePerformance.failedRequestCount + ", requestsFilledCount=" + nodePerformance.requestsFilledCount + ", requestsReceivedCount=" + nodePerformance.requestsReceivedCount + ", byteCountReceived=" + nodePerformance.byteCountReceived + ", byteCountSent=" + nodePerformance.byteCountSent);
                                    }

                                    final long failedRequestCount = nodePerformance.failedRequestCount.get();
                                    final long fulfilledRequestCount = nodePerformance.requestsFilledCount.get();
                                    final long totalRequestCount = (fulfilledRequestCount + failedRequestCount);
                                    // The first ten requests must be successful
                                    // After ten requests, permit 5% (+ 2) of the requests to fail
                                    //  At 11, nodes are allowed 3 failures
                                    //  At 50, nodes are allowed 5 failures
                                    //  At 100, nodes are allowed 7 failures
                                    final boolean shouldDemoteNode;
                                    if (totalRequestCount <= 10) {
                                        shouldDemoteNode = (failedRequestCount > 0);
                                    }
                                    else {
                                        shouldDemoteNode = ( failedRequestCount > (totalRequestCount * 0.05D + 2D) );
                                    }
                                    if (shouldDemoteNode) {
                                        final NodeId nodeId = preferredNode.getId();
                                        _preferredNodes.remove(nodeId);
                                        _otherNodes.put(nodeId, preferredNode);

                                        Logger.debug("Demoting node: " + preferredNode);
                                    }
                                }

                                return true;
                            }
                        });
                    }

                    final int preferredNodeCount = _preferredNodes.getCount();
                    final int peerCount = (preferredNodeCount + _otherNodes.getCount() + _pendingNodes.getCount());
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

    protected String _toCanonicalConnectionString(final BitcoinNode bitcoinNode) {
        final Ip ip = bitcoinNode.getIp();
        final Integer port = bitcoinNode.getPort();
        return (ip + ":" + port);
    }

    protected String _toCanonicalConnectionString(final NodeIpAddress nodeIpAddress) {
        final Ip ip = nodeIpAddress.getIp();
        final Integer port = nodeIpAddress.getPort();
        return (ip + ":" + port);
    }

    protected void _connectToNewPreferredNodes() {
        final Integer defaultPort = _defaultExternalPort;

        final MutableHashSet<String> excludeSet = new MutableHashSet<>();
        final MutableList<NodeIpAddress> nodeIpAddresses = new MutableArrayList<>();

        { // Add the node's own IP to the exclude-set.
            final NodeIpAddress localNodeIpAddress = _localNodeIpAddress;
            if (localNodeIpAddress != null) {
                final String connectionString = _toCanonicalConnectionString(_localNodeIpAddress);
                Logger.trace("Excluding self-ip: " + connectionString);
                excludeSet.add(connectionString);
            }
        }

        { // Exclude currently connected nodes and pending nodes...
            final MutableMap<NodeId, BitcoinNode> bitcoinNodes = _getAllHandshakedNodes();
            bitcoinNodes.putAll(_pendingNodes);

            bitcoinNodes.visit(new Visitor<Tuple<NodeId, BitcoinNode>>() {
                @Override
                public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                    final BitcoinNode bitcoinNode = mapEntry.second;
                    final String connectionString = _toCanonicalConnectionString(bitcoinNode);
                    Logger.trace("Excluding already-connected address: " + connectionString);
                    excludeSet.add(connectionString);

                    return true;
                }
            });
        }

        final int preferredNodeCount = _preferredNodes.getCount();

        final int newPreferredNodeCountTarget = (_minNodeCount - preferredNodeCount);
        final int numberOfNodesToAttemptConnectionsTo = Math.min((newPreferredNodeCountTarget * 2), _minNodeCount);
        if (numberOfNodesToAttemptConnectionsTo <= 0) { return; }

        { // Add seed nodes...
            for (final NodeIpAddress nodeIpAddress : _seedNodes) {
                final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                final boolean isUnique = excludeSet.add(connectionString);
                if (! isUnique) {
                    Logger.trace("Skipping excluded seed-node address: " + connectionString);
                    continue;
                }

                nodeIpAddresses.add(nodeIpAddress);
            }
        }

        final boolean shouldConnectToUnknownPeers = (! _shouldOnlyConnectToSeedNodes);
        if (shouldConnectToUnknownPeers) {
            { // Add previously-connected nodes...
                final List<BitcoinNodeIpAddress> foundNodeIpAddresses;
                { // Look for nodes that would be good preferred nodes, falling back to unknown nodes if there aren't enough available.
                    final MutableList<NodeFeatures.Feature> requiredFeatures = new MutableArrayList<>();
                    {
                        requiredFeatures.add(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                        requiredFeatures.add(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                    }

                    final long minSecondsSinceLastConnectionAttempt = (10L * 60L);
                    final List<BitcoinNodeIpAddress> possiblePreferredNodes = _bitcoinNodeStore.findNodes(requiredFeatures, minSecondsSinceLastConnectionAttempt, defaultPort, numberOfNodesToAttemptConnectionsTo);
                    if (possiblePreferredNodes.getCount() >= numberOfNodesToAttemptConnectionsTo) {
                        foundNodeIpAddresses = possiblePreferredNodes;
                    }
                    else { // If there weren't enough preferred nodes, then relax the requirements to include unknown peers.
                        final int missingDesiredNodeCount = (numberOfNodesToAttemptConnectionsTo - possiblePreferredNodes.getCount());
                        final List<BitcoinNodeIpAddress> unknownBitcoinNodeIpAddresses = _bitcoinNodeStore.findNodes(minSecondsSinceLastConnectionAttempt, missingDesiredNodeCount);

                        final MutableList<BitcoinNodeIpAddress> newBitcoinNodeIpAddresses = new MutableArrayList<>();
                        newBitcoinNodeIpAddresses.addAll(possiblePreferredNodes);
                        newBitcoinNodeIpAddresses.addAll(unknownBitcoinNodeIpAddresses);
                        foundNodeIpAddresses = newBitcoinNodeIpAddresses;
                    }
                }

                for (final BitcoinNodeIpAddress nodeIpAddress : foundNodeIpAddresses) {
                    if (nodeIpAddresses.getCount() >= numberOfNodesToAttemptConnectionsTo) { break; }

                    final Integer port = nodeIpAddress.getPort();
                    if (Util.areEqual(defaultPort, port)) { continue; }

                    final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                    final boolean isUnique = excludeSet.add(connectionString);
                    if (! isUnique) {
                        Logger.trace("Skipping excluded database address: " + connectionString);
                        continue;
                    }

                    nodeIpAddresses.add(nodeIpAddress);
                }
            }

            { // Connect to DNS seeded nodes...
                for (final String seedHost : _dnsSeeds) {
                    final List<Ip> seedIps = Ip.allFromHostName(seedHost);
                    if (seedIps == null) { continue; }

                    for (final Ip ip : seedIps) {
                        if (nodeIpAddresses.getCount() >= numberOfNodesToAttemptConnectionsTo) { break; }
                        final NodeIpAddress nodeIpAddress = new NodeIpAddress(ip, defaultPort);

                        final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                        final boolean isUnique = excludeSet.add(connectionString);
                        if (! isUnique) {
                            Logger.trace("Skipping excluded DNS address: " + connectionString);
                            continue;
                        }

                        nodeIpAddresses.add(nodeIpAddress);
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

            Logger.info("Connecting to: " + host + ":" + port);
        }
    }

    protected MutableMap<NodeId, BitcoinNode> _getAllHandshakedNodes() {
        final int totalConnectedNodeCount = (_otherNodes.getCount() + _preferredNodes.getCount());
        final MutableMap<NodeId, BitcoinNode> allConnectedNodes = new MutableHashMap<>(totalConnectedNodeCount);
        allConnectedNodes.putAll(_preferredNodes);
        allConnectedNodes.putAll(_otherNodes);
        return allConnectedNodes;
    }

    protected List<BitcoinNode> _filterNodes(final Map<NodeId, BitcoinNode> nodes, final NodeFilter nodeFilter) {
        return _filterNodes(nodes, nodes.getCount(), nodeFilter);
    }

    protected List<BitcoinNode> _filterNodes(final Map<NodeId, BitcoinNode> nodes, final Integer requestedNodeCount, final NodeFilter nodeFilter) {
        final java.util.ArrayList<BitcoinNode> filteredNodes = new java.util.ArrayList<>(requestedNodeCount);
        nodes.visit(new Visitor<>() {
            @Override
            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                final BitcoinNode bitcoinNode = mapEntry.second;
                if (nodeFilter.meetsCriteria(bitcoinNode)) {
                    filteredNodes.add(bitcoinNode);
                }
                return true;
            }
        });
        // TODO: randomizing the list should help prevent simple issues caused by consistent ordering,
        //  but ideally this would be something more intelligent (e.g. sorting by node health)
        Collections.shuffle(filteredNodes);
        return new MutableArrayList<>(filteredNodes);
    }

    protected void _recalculateLocalNodeIpAddress() {
        final MutableMap<Ip, Integer> nodeIpAddressCounts = new MutableHashMap<>();
        final MutableMap<Integer, Integer> nodePortCounts = new MutableHashMap<>();
        final MutableMap<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        allNodes.visit(new Visitor<>() {
            @Override
            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                final BitcoinNode bitcoinNode = mapEntry.second;
                final NodeIpAddress nodeIpAddress = bitcoinNode.getLocalNodeIpAddress();
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

                return true;
            }
        });

        Ip bestNodeIpAddress = null;
        {
            Integer bestCount = Integer.MIN_VALUE;
            for (final Ip nodeIpAddress : nodeIpAddressCounts.getKeys()) {
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
            for (final Integer nodePort : nodePortCounts.getKeys()) {
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
            _pendingNodes.mutableVisit(new MutableMap.MutableVisitor<NodeId, BitcoinNode>() {
                @Override
                public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                    final BitcoinNode oldPendingNode = mapEntry.second;

                    final Long pendingSinceTimeMilliseconds = oldPendingNode.getInitializationTimestamp();
                    if ((nowInMilliseconds - pendingSinceTimeMilliseconds) >= 30000L) {
                        final NodeIpAddress nodeIpAddress = oldPendingNode.getRemoteNodeIpAddress();

                        mapEntry.first = null; // Remove item...
                        oldPendingNode.disconnect();
                        if (nodeIpAddress != null) {
                            _connectedNodeAddresses.remove(nodeIpAddress);
                        }
                    }

                    return true;
                }
            });
        }
    }

    protected Boolean _isPreferredNodeType(final BitcoinNode bitcoinNode) {
        final long preferredPeerBlockHeightThreshold = 6L;

        final Boolean isBitcoinCashFullNode = BitcoinNodeManager.isBitcoinCashFullNode(bitcoinNode);
        if (! isBitcoinCashFullNode) {
            if (_fastSyncIsEnabled) {
                final Boolean hasUtxoCommitments = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.UTXO_COMMITMENTS_ENABLED);
                if (! hasUtxoCommitments) {
                    Logger.debug("Excluding node from preferred list; pruning node. (hasUtxoCommitments=0) " + bitcoinNode);
                    return false;
                }
            }
            else {
                Logger.debug("Excluding node from preferred list; pruning node. " + bitcoinNode);
                return false;
            }
        }

        final Boolean isOutboundConnection = bitcoinNode.isOutboundConnection();
        if (! isOutboundConnection) { return false; }

        final Long currentBlockHeight = _synchronizationStatusHandler.getCurrentBlockHeight();
        if (currentBlockHeight != null) {
            final Long bitcoinBlockHeight = Util.coalesce(bitcoinNode.getBlockHeight(), 0L);
            if ( bitcoinBlockHeight < (currentBlockHeight - preferredPeerBlockHeightThreshold) ) {
                Logger.debug("Excluding node from preferred list; blockHeight=" + bitcoinBlockHeight);
                return false;
            }
        }

        return true;
    }

    protected void _addHandshakedNode(final BitcoinNode bitcoinNode) {
        if (_isShuttingDown) {
            bitcoinNode.disconnect();
            return;
        }

        final Boolean isPreferredNodeType = _isPreferredNodeType(bitcoinNode);
        if ( bitcoinNode.isOutboundConnection() && (! isPreferredNodeType)) { // All outbound connections should be preferred nodes.
            bitcoinNode.disconnect();
            return;
        }

        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        final NodeId newNodeId = bitcoinNode.getId();

        if (nodeIpAddress != null) {
            _connectedNodeAddresses.add(nodeIpAddress);
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
            BitcoinNodeManager.runAsync(onNodeListChangedCallback);
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

        final Container<Boolean> nodeDidConnect = new Container<>(null);

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

                for (final NodeIpAddress nodeIpAddress : nodeIpAddresses) {
                    final Integer port = nodeIpAddress.getPort();

                    if (! Util.areEqual(_defaultExternalPort, port)) { return; }
                    _bitcoinNodeStore.storeAddress(nodeIpAddress);
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

        BitcoinNodeManager.runAsync(timeoutRunnable); // TODO: Make the timeouts handled by a single managed thread.
    }

    protected void _pingIdleNodes() {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();

        final MutableMap<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final MutableList<BitcoinNode> idleNodes = new MutableArrayList<>();
        allNodes.visit(new Visitor<>() {
            @Override
            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                final BitcoinNode bitcoinNode = mapEntry.second;
                final Long lastMessageTime = bitcoinNode.getLastMessageReceivedTimestamp();
                final long idleDuration = (now - lastMessageTime); // NOTE: Race conditions could result in a negative value...

                if (idleDuration > PING_AFTER_MS_IDLE) {
                    idleNodes.add(bitcoinNode);
                }

                return true;
            }
        });

        Logger.debug("Idle Node Count: " + idleNodes.getCount() + " / " + allNodes.getCount());

        for (final BitcoinNode idleNode : idleNodes) {
            if (! idleNode.isHandshakeComplete()) { return; }

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
        final MutableList<BitcoinNode> purgeableNodes = new MutableArrayList<>();

        final MutableMap<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        allNodes.visit(new Visitor<>() {
            @Override
            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                final BitcoinNode bitcoinNode = mapEntry.second;
                if (! bitcoinNode.isConnected()) {
                    final long nodeAge = (_systemTime.getCurrentTimeInMilliSeconds() - bitcoinNode.getInitializationTimestamp());
                    if (nodeAge > 10000L) {
                        purgeableNodes.add(bitcoinNode);
                    }
                }
                return true;
            }
        });

        for (final BitcoinNode node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _removeHighLatencyNodes() {
        final MutableList<BitcoinNode> purgeableNodes = new MutableArrayList<>();

        final MutableMap<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        allNodes.visit(new Visitor<>() {
            @Override
            public boolean run(final Tuple<NodeId, BitcoinNode> mapEntry) {
                final BitcoinNode bitcoinNode = mapEntry.second;
                if (bitcoinNode.isConnected()) {
                    final Long nodePing = bitcoinNode.getAveragePing();
                    if ( (nodePing != null) && (nodePing > 10000L) ) {
                        purgeableNodes.add(bitcoinNode);
                    }
                }
                return true;
            }
        });

        for (final BitcoinNode node : purgeableNodes) {
            _removeNode(node);
        }
    }

    protected void _addNode(final BitcoinNode bitcoinNode) {
        if (_isShuttingDown) {
            bitcoinNode.disconnect();
            return;
        }

        _bitcoinNodeStore.storeNode(bitcoinNode);

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();

        // Ensure the NodeManager does not exceed maxNodeCount.
        if ((allNodes.getCount() + _pendingNodes.getCount()) >= _maxNodeCount) {
            final MutableList<BitcoinNode> otherNodes = new MutableArrayList<>(_otherNodes.getValues()); // NOTE: List is copied before isEmpty evaluation to avoid race condition due to concurrent modification of _otherNodes.
            final boolean hasNonPreferredPeers = (! otherNodes.isEmpty());
            if (_shouldPrioritizeNewConnections && hasNonPreferredPeers) {
                // find oldest non-preferred peer and disconnect before continuing
                synchronized (_performanceStatistics) {
                    otherNodes.sort(new Comparator<BitcoinNode>() {
                        @Override
                        public int compare(final BitcoinNode node0, final BitcoinNode node1) {
                            final NodePerformance nodePerformance0 = _performanceStatistics.get(node0);
                            final NodePerformance nodePerformance1 = _performanceStatistics.get(node1);
                            final Long nodeTimestamp0 = nodePerformance0 != null ? nodePerformance0.connectionTimestampMs : Long.MAX_VALUE;
                            final Long nodeTimestamp1 = nodePerformance1 != null ? nodePerformance1.connectionTimestampMs : Long.MAX_VALUE;
                            return nodeTimestamp0.compareTo(nodeTimestamp1);
                        }
                    });
                }

                final BitcoinNode nodeToDisconnect = otherNodes.get(0);
                nodeToDisconnect.disconnect();
            }
            else {
                // prioritizing existing connections, disconnect from this new node and bail out
                bitcoinNode.disconnect();
                return;
            }
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

        final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(_blockchain);
        final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();

        bitcoinNode.transmitBlockFinder(blockFinderHashes);

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            BitcoinNodeManager.runAsync(onNodeListChangedCallback);
        }
    }

    protected void _onNodeHandshakeComplete(final BitcoinNode bitcoinNode) {
        _bitcoinNodeStore.updateLastHandshake(bitcoinNode); // WARNING: If removing Last Handshake update, ensure BanFilter no longer requires the handshake timestamp...
        _bitcoinNodeStore.updateNodeFeatures(bitcoinNode);
        _bitcoinNodeStore.updateUserAgent(bitcoinNode);

        _banFilter.onNodeHandshakeComplete(bitcoinNode);
        if (! bitcoinNode.isConnected()) { return; } // Node was banned.

        if (_newBlocksViaHeadersIsEnabled) {
            bitcoinNode.enableNewBlockViaHeaders();
        }

        final NewNodeCallback newNodeCallback = _newNodeCallback;
        if (newNodeCallback != null) {
            BitcoinNodeManager.runAsync(new Runnable() {
                @Override
                public void run() {
                    newNodeCallback.onNodeHandshakeComplete(bitcoinNode);
                }
            });
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            BitcoinNodeManager.runAsync(onNodeListChangedCallback);
        }
    }

    public BitcoinNodeManager(final Context context) {
        _systemTime = context.systemTime;
        _preferredNodes = new ConcurrentMutableHashMap<>(context.maxNodeCount);
        _otherNodes = new ConcurrentMutableHashMap<>(context.maxNodeCount);

        _minNodeCount = context.minNodeCount;
        _maxNodeCount = context.maxNodeCount;
        _nodeFactory = context.nodeFactory;
        _networkTime = context.networkTime;
        _blockchain = context.blockchain;
        _bitcoinNodeStore = context.bitcoinNodeStore;

        _bitcoinNodeObserver = new BitcoinNodeObserver() {
            private final List<MessageType> _trackedMessageResponseTypes = new ImmutableList<>(
                MessageType.BLOCK,
                MessageType.TRANSACTION
            );

            private boolean _isTrackedResponseType(final MessageType messageType) {
                return _trackedMessageResponseTypes.contains(messageType);
            }

            private NodePerformance _getNodePerformance(final BitcoinNode bitcoinNode) {
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
            public void onHandshakeComplete(final BitcoinNode bitcoinNode) {
                // ensure node performance is created
                _getNodePerformance(bitcoinNode);
            }

            @Override
            public void onDataRequested(final BitcoinNode bitcoinNode, final MessageType expectedResponseType) {
                if (_isTrackedResponseType(expectedResponseType)) {
                    final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                    nodePerformance.requestsReceivedCount.incrementAndGet();
                }
            }

            @Override
            public void onDataReceived(final BitcoinNode bitcoinNode, final MessageType messageType, final Integer byteCount, final Boolean wasRequested) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);

                if (wasRequested && _isTrackedResponseType(messageType)) {
                    nodePerformance.requestsFilledCount.incrementAndGet();
                }

                nodePerformance.byteCountReceived.addAndGet(byteCount);
            }

            @Override
            public void onDataSent(final BitcoinNode bitcoinNode, final MessageType messageType, final Integer byteCount) {
                final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                nodePerformance.byteCountSent.addAndGet(byteCount);
            }

            @Override
            public void onFailedRequest(final BitcoinNode bitcoinNode, final MessageType expectedResponseType, final RequestPriority requestPriority) {
                if (_isTrackedResponseType(expectedResponseType) && requestPriority.getPriority() > 0) {
                    final NodePerformance nodePerformance = _getNodePerformance(bitcoinNode);
                    nodePerformance.failedRequestCount.incrementAndGet();
                }
            }
        };

        _nodeInitializer = context.nodeInitializer;
        _banFilter = context.banFilter;
        _memoryPoolEnquirer = context.memoryPoolEnquirer;
        _synchronizationStatusHandler = context.synchronizationStatusHandler;

        _bitcoinNodeHeadBlockFinder = new BitcoinNodeHeadBlockFinder(_blockchain, _banFilter);
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

    public void reconnectToSeedNodes() {
        final MutableHashSet<String> excludeSet = new MutableHashSet<>();
        final MutableList<NodeIpAddress> nodeIpAddresses = new MutableArrayList<>();

        { // Add the node's own IP to the exclude-set.
            final NodeIpAddress localNodeIpAddress = _localNodeIpAddress;
            if (localNodeIpAddress != null) {
                final String connectionString = _toCanonicalConnectionString(_localNodeIpAddress);
                Logger.trace("Excluding self-ip: " + connectionString);
                excludeSet.add(connectionString);
            }
        }

        { // Exclude currently connected nodes and pending nodes...
            final MutableMap<NodeId, BitcoinNode> bitcoinNodes = _getAllHandshakedNodes();
            bitcoinNodes.putAll(_pendingNodes);

            for (final BitcoinNode bitcoinNode : bitcoinNodes.getValues()) {
                final String connectionString = _toCanonicalConnectionString(bitcoinNode);
                Logger.trace("Excluding already-connected address: " + connectionString);
                excludeSet.add(connectionString);
            }
        }

        { // Add seed nodes...
            for (final NodeIpAddress nodeIpAddress : _seedNodes) {
                final String connectionString = _toCanonicalConnectionString(nodeIpAddress);
                final boolean isUnique = excludeSet.add(connectionString);
                if (! isUnique) {
                    Logger.trace("Skipping excluded seed-node address: " + connectionString);
                    continue;
                }

                nodeIpAddresses.add(nodeIpAddress);
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

            Logger.info("Connecting to: " + host + ":" + port);
        }
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
        return new ImmutableList<>(allNodes.getKeys());
    }

    public BitcoinNode getNode(final NodeId nodeId) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return allNodes.get(nodeId);
    }

    public BitcoinNode getNode(final NodeFilter nodeFilter) {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        final List<BitcoinNode> filteredNodes = _filterNodes(allNodes, nodeFilter);
        if ( (filteredNodes == null) || filteredNodes.isEmpty() ) { return null; }
        return filteredNodes.get(0);
    }

    public List<BitcoinNode> getPreferredNodes() {
        return new ImmutableList<>(_preferredNodes.getValues());
    }

    public List<BitcoinNode> getUnpreferredNodes() {
        return new ImmutableList<>(_otherNodes.getValues());
    }

    public List<BitcoinNode> getPreferredNodes(final NodeFilter nodeFilter) {
        return _filterNodes(_preferredNodes, nodeFilter);
    }

    public List<BitcoinNode> getNodes() {
        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        return new ImmutableList<>(allNodes.getValues());
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

    public void setShouldPrioritizeNewConnections(final Boolean shouldPrioritizeNewConnections) {
        _shouldPrioritizeNewConnections = shouldPrioritizeNewConnections;
    }

    public Boolean shouldPrioritizeNewConnections() {
        return _shouldPrioritizeNewConnections;
    }

    public void setShouldOnlyConnectToSeedNodes(final Boolean shouldOnlyConnectToSeedNodes) {
        _shouldOnlyConnectToSeedNodes = shouldOnlyConnectToSeedNodes;
    }

    public Boolean shouldOnlyConnectToSeedNodes() {
        return _shouldOnlyConnectToSeedNodes;
    }

    public void close() {
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
        final MutableList<BitcoinNode> allNodes = new MutableArrayList<>(allHandshakedNodes.getValues());
        allNodes.addAll(_pendingNodes.getValues());

        for (final BitcoinNode bitcoinNode : allNodes) {
            _removeNode(bitcoinNode);
        }
    }

    public void broadcastDoubleSpendProof(final DoubleSpendProofWithTransactions doubleSpendProof) {
        final Sha256Hash doubleSpendProofHash = doubleSpendProof.getHash();
        final Boolean isExtendedDoubleSpendProof = doubleSpendProof.usesExtendedFormat();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (BitcoinNode bitcoinNode : allNodes.getValues()) {
            if (isExtendedDoubleSpendProof) {
                final Boolean nodeSupportsExtendedDoubleSpendProofs = bitcoinNode.hasFeatureEnabled(NodeFeatures.Feature.EXTENDED_DOUBLE_SPEND_PROOFS_ENABLED);
                if (! nodeSupportsExtendedDoubleSpendProofs) { continue; }
            }

            final Transaction firstSeenTransaction = doubleSpendProof.getTransaction0();
            final Transaction doubleSpendTransaction = doubleSpendProof.getTransaction1();

            final boolean matchesFilter = (bitcoinNode.matchesFilter(firstSeenTransaction) || bitcoinNode.matchesFilter(doubleSpendTransaction));
            if (matchesFilter) {
                bitcoinNode.transmitDoubleSpendProofHash(doubleSpendProofHash);
            }
        }
    }

    public Boolean hasBloomFilter() {
        return (_bloomFilter != null);
    }

    public void setBloomFilter(final MutableBloomFilter bloomFilter) {
        _bloomFilter = bloomFilter;

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode bitcoinNode : allNodes.getValues()) {
            bitcoinNode.setBloomFilter(_bloomFilter);
        }
    }

    public void banNode(final Ip ip) {
        _banFilter.banIp(ip);

        // Disconnect all currently-connected nodes at that ip...
        final MutableList<BitcoinNode> droppedNodes = new MutableArrayList<>();

        final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
        for (final BitcoinNode bitcoinNode : allNodes.getValues()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        // Disconnect all pending nodes at that ip...
        for (final BitcoinNode bitcoinNode : _pendingNodes.getValues()) {
            if (Util.areEqual(ip, bitcoinNode.getIp())) {
                droppedNodes.add(bitcoinNode);
            }
        }

        for (final BitcoinNode bitcoinNode : droppedNodes) {
            _removeNode(bitcoinNode);
        }

        final Runnable onNodeListChangedCallback = _onNodeListChanged;
        if (onNodeListChangedCallback != null) {
            BitcoinNodeManager.runAsync(onNodeListChangedCallback);
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
        for (final BitcoinNode bitcoinNode : allNodes.getValues()) {
            bitcoinNode.enableTransactionRelay(transactionRelayIsEnabled);
        }
    }

    public Boolean isTransactionRelayEnabled() {
        return _transactionRelayIsEnabled;
    }

    public void enableNewBlockViaHeaders(final Boolean newBlocksViaHeadersIsEnabled) {
        _newBlocksViaHeadersIsEnabled = newBlocksViaHeadersIsEnabled;
        if (newBlocksViaHeadersIsEnabled) {
            final Map<NodeId, BitcoinNode> allNodes = _getAllHandshakedNodes();
            for (final BitcoinNode bitcoinNode : allNodes.getValues()) {
                bitcoinNode.enableNewBlockViaHeaders();
            }
        }
    }

    public void defineDnsSeeds(final List<String> dnsSeeds) {
        _dnsSeeds.addAll(dnsSeeds);
    }

    public void setFastSyncIsEnabled(final Boolean isEnabled) {
        _fastSyncIsEnabled = isEnabled;
    }

    public Boolean isFastSyncEnabled() {
        return _fastSyncIsEnabled;
    }
}

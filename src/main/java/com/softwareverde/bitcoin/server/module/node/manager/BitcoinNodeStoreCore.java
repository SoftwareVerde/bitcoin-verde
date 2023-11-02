package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.ConcurrentHashSet;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.map.mutable.ConcurrentMutableHashMap;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;
import com.softwareverde.util.type.time.SystemTime;

public class BitcoinNodeStoreCore implements BitcoinNodeStore {
    public static class AvailableNode {
        public final NodeIpAddress nodeIpAddress;
        public NodeFeatures nodeFeatures;
        public Long firstSeenTime;
        public Long lastConnectionAttemptTime;
        public Long lastHandshakeTime;
        public String userAgent;

        public AvailableNode(final NodeIpAddress nodeIpAddress, final SystemTime systemTime) {
            this.nodeIpAddress = nodeIpAddress;
            this.firstSeenTime = systemTime.getCurrentTimeInMilliSeconds();
        }
    }

    protected final SystemTime _systemTime = new SystemTime();
    protected final ConcurrentMutableHashMap<NodeIpAddress, AvailableNode> _availableNodes = new ConcurrentMutableHashMap<>();
    protected final ConcurrentHashSet<Ip> _bannedIps = new ConcurrentHashSet<>();

    @Override
    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final long secondsSinceLastConnectionAttempt, final long port, final int maxCount) {
        final MutableArrayList<BitcoinNodeIpAddress> foundNodes = new MutableArrayList<>();

        for (final AvailableNode availableNode : _availableNodes.getValues()) {
            if (availableNode.nodeFeatures == null) { continue; }
            if (availableNode.nodeIpAddress.getPort() != port) { continue; }

            final long secondsSince = _systemTime.getCurrentTimeInMilliSeconds() - availableNode.lastConnectionAttemptTime;
            if (secondsSince < secondsSinceLastConnectionAttempt) { continue; }

            boolean allFeaturesFound = true;
            for (final NodeFeatures.Feature feature : requiredFeatures) {
                if (! availableNode.nodeFeatures.isFeatureEnabled(feature)) {
                    allFeaturesFound = false;
                    break;
                }
            }

            if (allFeaturesFound) {
                final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(availableNode.nodeIpAddress);
                bitcoinNodeIpAddress.setNodeFeatures(availableNode.nodeFeatures);
                foundNodes.add(bitcoinNodeIpAddress);

                if (foundNodes.getCount() >= maxCount) {
                    break;
                }
            }
        }

        return foundNodes;
    }

    @Override
    public List<BitcoinNodeIpAddress> findNodes(final long secondsSinceLastConnectionAttempt, final int maxCount) {
        final MutableArrayList<BitcoinNodeIpAddress> foundNodes = new MutableArrayList<>();

        for (final AvailableNode availableNode : _availableNodes.getValues()) {
            if (availableNode.nodeFeatures == null) { continue; }

            final long secondsSince = _systemTime.getCurrentTimeInMilliSeconds() - availableNode.lastConnectionAttemptTime;
            if (secondsSince < secondsSinceLastConnectionAttempt) { continue; }

            final BitcoinNodeIpAddress bitcoinNodeIpAddress = new BitcoinNodeIpAddress(availableNode.nodeIpAddress);
            bitcoinNodeIpAddress.setNodeFeatures(availableNode.nodeFeatures);
            foundNodes.add(bitcoinNodeIpAddress);

            if (foundNodes.getCount() >= maxCount) {
                break;
            }
        }

        return foundNodes;
    }

    @Override
    public synchronized void storeAddress(final NodeIpAddress nodeIpAddress) {
        if (_availableNodes.containsKey(nodeIpAddress)) { return; }
        _availableNodes.put(nodeIpAddress, new AvailableNode(nodeIpAddress, _systemTime));
    }

    @Override
    public synchronized void storeNode(final BitcoinNode bitcoinNode) {
        if (bitcoinNode == null) { return; }

        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        final AvailableNode availableNode;
        if (_availableNodes.containsKey(nodeIpAddress)) {
            availableNode = _availableNodes.get(nodeIpAddress);
        }
        else {
            availableNode = new AvailableNode(nodeIpAddress, _systemTime);
        }

        availableNode.lastConnectionAttemptTime = _systemTime.getCurrentTimeInMilliSeconds();

        if (bitcoinNode.isConnected()) {
            if (bitcoinNode.isHandshakeComplete()) {
                availableNode.userAgent = bitcoinNode.getUserAgent();
                availableNode.lastHandshakeTime = _systemTime.getCurrentTimeInMilliSeconds();

                availableNode.nodeFeatures = bitcoinNode.getNodeFeatures();
            }
        }
    }

    @Override
    public void updateLastHandshake(final BitcoinNode bitcoinNode) {
        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        if (nodeIpAddress == null) { return; }

        final AvailableNode availableNode = _availableNodes.get(nodeIpAddress);
        if (availableNode == null) { return; }

        availableNode.lastHandshakeTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    @Override
    public void updateNodeFeatures(final BitcoinNode bitcoinNode) {
        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        if (nodeIpAddress == null) { return; }

        final AvailableNode availableNode = _availableNodes.get(nodeIpAddress);
        if (availableNode == null) { return; }

        availableNode.nodeFeatures = bitcoinNode.getNodeFeatures();
    }

    @Override
    public void updateUserAgent(final BitcoinNode bitcoinNode) {
        final NodeIpAddress nodeIpAddress = bitcoinNode.getRemoteNodeIpAddress();
        if (nodeIpAddress == null) { return; }

        final AvailableNode availableNode = _availableNodes.get(nodeIpAddress);
        if (availableNode == null) { return; }

        availableNode.userAgent = bitcoinNode.getUserAgent();
    }

    @Override
    public void banIp(final Ip ip) {
        _bannedIps.add(ip);
    }

    @Override
    public void unbanIp(final Ip ip) {
        _bannedIps.remove(ip);
    }

    @Override
    public boolean isBanned(final Ip ip) {
        return _bannedIps.contains(ip);
    }
}

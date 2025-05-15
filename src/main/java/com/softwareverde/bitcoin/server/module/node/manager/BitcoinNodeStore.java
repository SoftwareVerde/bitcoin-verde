package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface BitcoinNodeStore {
    List<BitcoinNodeIpAddress> findNodes(List<NodeFeatures.Feature> requiredFeatures, long secondsSinceLastConnectionAttempt, long port, int maxCount);
    List<BitcoinNodeIpAddress> findNodes(long secondsSinceLastConnectionAttempt, int maxCount);
    void storeAddress(NodeIpAddress nodeIpAddress);
    void storeNode(BitcoinNode bitcoinNode);
    void updateLastHandshake(BitcoinNode bitcoinNode);
    void updateNodeFeatures(BitcoinNode bitcoinNode);
    void updateUserAgent(BitcoinNode bitcoinNode);

    void banIp(Ip ip);
    void unbanIp(Ip ip);
    boolean isBanned(Ip ip);
}

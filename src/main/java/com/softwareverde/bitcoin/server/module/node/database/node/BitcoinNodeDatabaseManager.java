package com.softwareverde.bitcoin.server.module.node.database.node;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface BitcoinNodeDatabaseManager {
    Object MUTEX = new Object();

    NodeId getNodeId(BitcoinNode node) throws DatabaseException;
    void storeNode(BitcoinNode node) throws DatabaseException;
    void storeAddress(NodeIpAddress nodeIpAddress) throws DatabaseException;
    void updateLastHandshake(BitcoinNode node) throws DatabaseException;
    void updateNodeFeatures(BitcoinNode node) throws DatabaseException;
    void updateUserAgent(BitcoinNode node) throws DatabaseException;
    List<BitcoinNodeIpAddress> findNodes(List<NodeFeatures.Feature> requiredFeatures, Long secondsSinceLastConnectionAttempt, Integer maxCount) throws DatabaseException;
    List<BitcoinNodeIpAddress> findNodes(List<NodeFeatures.Feature> requiredFeatures, Long secondsSinceLastConnectionAttempt, Integer requiredPort, Integer maxCount) throws DatabaseException;
    List<BitcoinNodeIpAddress> findNodes(Long secondsSinceLastConnectionAttempt, Integer maxCount) throws DatabaseException;
    Integer getFailedConnectionCountForIp(Ip ip) throws DatabaseException;
    Integer getFailedConnectionCountForIp(Ip ip, Long sinceTimestamp) throws DatabaseException;
    void setIsBanned(Ip ip, Boolean isBanned) throws DatabaseException;
    Boolean isBanned(Ip ip) throws DatabaseException;
    Boolean isBanned(Ip ip, final Long sinceTimestamp) throws DatabaseException;
}

package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.address.NodeIpAddress;

public interface FakeBitcoinNodeDatabaseManager extends BitcoinNodeDatabaseManager {
    @Override
    default NodeId getNodeId(BitcoinNode node) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void storeNode(BitcoinNode node) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void storeAddress(NodeIpAddress nodeIpAddress) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void updateLastHandshake(BitcoinNode node) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void updateNodeFeatures(BitcoinNode node) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void updateUserAgent(BitcoinNode node) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default List<BitcoinNodeIpAddress> findNodes(List<NodeFeatures.Feature> requiredFeatures, Long secondsSinceLastConnectionAttempt, Integer maxCount) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default List<BitcoinNodeIpAddress> findNodes(List<NodeFeatures.Feature> requiredFeatures, Long secondsSinceLastConnectionAttempt, Integer requiredPort, Integer maxCount) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default List<BitcoinNodeIpAddress> findNodes(Long secondsSinceLastConnectionAttempt, Integer maxCount) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Integer getFailedConnectionCountForIp(Ip ip) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Integer getFailedConnectionCountForIp(Ip ip, Long sinceTimestamp) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default void setIsBanned(Ip ip, Boolean isBanned) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Boolean isBanned(Ip ip) throws DatabaseException { throw new UnsupportedOperationException(); }

    @Override
    default Boolean isBanned(Ip ip, final Long sinceTimestamp) throws DatabaseException { throw new UnsupportedOperationException(); }
}

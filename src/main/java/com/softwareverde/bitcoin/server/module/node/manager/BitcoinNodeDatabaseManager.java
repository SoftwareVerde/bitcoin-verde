package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.BatchedInsertQuery;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.ip.IpInflater;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashSet;

public class BitcoinNodeDatabaseManager {
    public static final Object MUTEX = new Object();

    protected final MysqlDatabaseConnection _databaseConnection;
    protected final SystemTime _systemTime = new SystemTime();

    protected NodeFeatures _inflateNodeFeatures(final Long nodeId) throws DatabaseException {
        final NodeFeatures nodeFeatures = new NodeFeatures();
        final java.util.List<Row> nodeFeatureRows = _databaseConnection.query(
            new Query("SELECT id, feature FROM node_features WHERE node_id = ?")
                .setParameter(nodeId)
        );
        for (final Row nodeFeatureRow : nodeFeatureRows) {
            final String featureString = nodeFeatureRow.getString("feature");
            final NodeFeatures.Feature feature = NodeFeatures.Feature.fromString(featureString);
            nodeFeatures.enableFeature(feature);
        }

        return nodeFeatures;
    }

    protected NodeId _getNodeId(final String host, final Integer port) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.port = ?")
                .setParameter(host)
                .setParameter(port)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return NodeId.wrap(row.getLong("id"));
    }

    public BitcoinNodeDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public NodeId getNodeId(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        return _getNodeId(host, port);
    }

    public void storeNode(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();
        final String userAgent = node.getUserAgent(); // May be null...

        synchronized (MUTEX) {
            final Long hostId;
            {
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id FROM hosts WHERE host = ?")
                        .setParameter(host)
                );

                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    hostId = row.getLong("id");
                }
                else {
                    hostId = _databaseConnection.executeSql(
                        new Query("INSERT INTO hosts (host) VALUES (?)")
                            .setParameter(host)
                    );
                }
            }

            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id FROM nodes WHERE host_id = ? AND port = ?")
                    .setParameter(hostId)
                    .setParameter(port)
            );

            if (rows.isEmpty()) {
                final Long now = _systemTime.getCurrentTimeInSeconds();

                _databaseConnection.executeSql(
                    new Query("INSERT INTO nodes (host_id, port, first_seen_timestamp, last_seen_timestamp, user_agent) VALUES (?, ?, ?, ?, ?)")
                        .setParameter(hostId)
                        .setParameter(port)
                        .setParameter(now)
                        .setParameter(now)
                        .setParameter(userAgent)
                );
            }
            else {
                final Row row = rows.get(0);
                final Long nodeId = row.getLong("id");

                _databaseConnection.executeSql(
                    new Query("UPDATE nodes SET connection_count = (connection_count + 1) WHERE id = ?")
                        .setParameter(nodeId)
                );
            }
        }
    }

    public void updateLastHandshake(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(host, port);
        if (nodeId == null) { return; }

        _databaseConnection.executeSql(
            new Query("UPDATE nodes SET last_handshake_timestamp = ? WHERE id = ?")
                .setParameter(_systemTime.getCurrentTimeInSeconds())
                .setParameter(nodeId)
        );
    }

    public void updateNodeFeatures(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        final BitcoinNodeIpAddress nodeIpAddress = node.getLocalNodeIpAddress();

        if (nodeIpAddress != null) {
            final NodeId nodeId = _getNodeId(host, port);
            if (nodeId == null) { return; }

            final MutableList<NodeFeatures.Feature> disabledFeatures = new MutableList<NodeFeatures.Feature>();
            final NodeFeatures nodeFeatures = nodeIpAddress.getNodeFeatures();
            for (final NodeFeatures.Feature feature : NodeFeatures.Feature.values()) {
                if (nodeFeatures.hasFeatureFlagEnabled(feature)) {
                    _databaseConnection.executeSql(
                        new Query("INSERT IGNORE INTO node_features (node_id, feature) VALUES (?, ?)")
                            .setParameter(nodeId)
                            .setParameter(feature)
                    );
                }
                else {
                    disabledFeatures.add(feature);
                }
            }

            if (! disabledFeatures.isEmpty()) {
                final String inClause = DatabaseUtil.createInClause(disabledFeatures);

                _databaseConnection.executeSql(
                    new Query("DELETE FROM node_features WHERE node_id = ? AND feature IN (" + inClause + ")")
                        .setParameter(nodeId)
                );
            }
        }
    }

    public void updateUserAgent(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();
        final String userAgent = node.getUserAgent();

        final NodeId nodeId = _getNodeId(host, port);
        if (nodeId == null) { return; }

        _databaseConnection.executeSql(
            new Query("UPDATE nodes SET user_agent = ? WHERE id = ?")
                .setParameter(userAgent)
                .setParameter(nodeId)
        );
    }

    public void updateBlockInventory(final BitcoinNode node, final List<PendingBlockId> pendingBlockIds) throws DatabaseException {
        if (pendingBlockIds.isEmpty()) { return; }

        final String host = node.getHost();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(host, port);
        if (nodeId == null) { return; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO node_blocks_inventory (node_id, pending_block_id) VALUES (?, ?)");
        for (final PendingBlockId pendingBlockId : pendingBlockIds) {
            batchedInsertQuery.setParameter(nodeId);
            batchedInsertQuery.setParameter(pendingBlockId);
        }
        _databaseConnection.executeSql(batchedInsertQuery);
    }

    public void deleteBlockInventory(final PendingBlockId pendingBlockId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM node_blocks_inventory WHERE pending_block_id = ?")
                .setParameter(pendingBlockId)
        );
    }

    public void deleteBlockInventory(final List<PendingBlockId> pendingBlockIds) throws DatabaseException {
        if (pendingBlockIds.isEmpty()) { return; }
        _databaseConnection.executeSql(new Query("DELETE FROM node_blocks_inventory WHERE pending_block_id IN (" + DatabaseUtil.createInClause(pendingBlockIds) + ")"));
    }

    public void updateTransactionInventory(final BitcoinNode node, final List<PendingTransactionId> pendingTransactionIds) throws DatabaseException {
        if (pendingTransactionIds.isEmpty()) { return; }

        final String host = node.getHost();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(host, port);
        if (nodeId == null) { return; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO node_transactions_inventory (node_id, pending_transaction_id) VALUES (?, ?)");
        for (final PendingTransactionId pendingTransactionId : pendingTransactionIds) {
            batchedInsertQuery.setParameter(nodeId);
            batchedInsertQuery.setParameter(pendingTransactionId);
        }
        _databaseConnection.executeSql(batchedInsertQuery);
    }

    public List<NodeId> filterNodesViaTransactionInventory(final List<NodeId> nodeIds, final Sha256Hash transactionHash, final FilterType filterType) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT node_transactions_inventory.node_id FROM node_transactions_inventory INNER JOIN pending_transactions ON pending_transactions.id = node_transactions_inventory.pending_transaction_id WHERE pending_transactions.hash = ? AND node_transactions_inventory.node_id IN (" + DatabaseUtil.createInClause(nodeIds) + ")")
                .setParameter(transactionHash)
        );

        final HashSet<NodeId> filteredNodes = new HashSet<NodeId>(rows.size());
        if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
            for (final NodeId nodeId : nodeIds) {
                filteredNodes.add(nodeId);
            }
        }

        for (final Row row : rows) {
            final NodeId nodeWithTransaction = NodeId.wrap(row.getLong("node_id"));

            if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
                filteredNodes.remove(nodeWithTransaction);
            }
            else {
                filteredNodes.add(nodeWithTransaction);
            }
        }

        return new ImmutableList<NodeId>(filteredNodes);
    }

    public List<NodeId> filterNodesViaBlockInventory(final List<NodeId> nodeIds, final Sha256Hash blockHash, final FilterType filterType) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT node_blocks_inventory.node_id FROM node_blocks_inventory INNER JOIN pending_blocks ON pending_blocks.id = node_blocks_inventory.pending_block_id WHERE pending_blocks.hash = ? AND node_blocks_inventory.node_id IN (" + DatabaseUtil.createInClause(nodeIds) + ")")
                .setParameter(blockHash)
        );

        final HashSet<NodeId> filteredNodes = new HashSet<NodeId>(rows.size());
        if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
            for (final NodeId nodeId : nodeIds) {
                filteredNodes.add(nodeId);
            }
        }

        for (final Row row : rows) {
            final NodeId nodeWithTransaction = NodeId.wrap(row.getLong("node_id"));

            if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
                filteredNodes.remove(nodeWithTransaction);
            }
            else {
                filteredNodes.add(nodeWithTransaction);
            }
        }

        return new ImmutableList<NodeId>(filteredNodes);
    }

    public void deleteTransactionInventory(final PendingTransactionId pendingTransactionId) throws DatabaseException {
        _databaseConnection.executeSql(
            new Query("DELETE FROM node_transactions_inventory WHERE pending_transaction_id = ?")
                .setParameter(pendingTransactionId)
        );
    }

    public void deleteTransactionInventory(final List<PendingTransactionId> pendingTransactionIds) throws DatabaseException {
        if (pendingTransactionIds.isEmpty()) { return; }
        _databaseConnection.executeSql(new Query("DELETE FROM node_transactions_inventory WHERE pending_transaction_id IN (" + DatabaseUtil.createInClause(pendingTransactionIds) + ")"));
    }

    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final Integer maxCount) throws DatabaseException {
        final String inClause = DatabaseUtil.createInClause(requiredFeatures);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id INNER JOIN node_features ON nodes.id = node_features.node_id WHERE nodes.last_handshake_timestamp IS NOT NULL AND hosts.is_banned = 0 AND node_features.feature IN (" + inClause + ") ORDER BY nodes.last_handshake_timestamp DESC, nodes.connection_count DESC LIMIT " + maxCount)
        );

        final IpInflater ipInflater = new IpInflater();

        final MutableList<BitcoinNodeIpAddress> nodeIpAddresses = new MutableList<BitcoinNodeIpAddress>(rows.size());

        for (final Row row : rows) {
            final Long nodeId = row.getLong("id");
            final String host = row.getString("host");
            final Integer port  = row.getInteger("port");

            final Ip ip = ipInflater.fromString(host);

            final BitcoinNodeIpAddress nodeIpAddress = new BitcoinNodeIpAddress();
            nodeIpAddress.setIp(ip);
            nodeIpAddress.setPort(port);

            final NodeFeatures nodeFeatures = _inflateNodeFeatures(nodeId);
            nodeIpAddress.setNodeFeatures(nodeFeatures);

            nodeIpAddresses.add(nodeIpAddress);
        }

        return nodeIpAddresses;
    }

    public List<BitcoinNodeIpAddress> findNodes(final Integer maxCount) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.is_banned = 0 WHERE nodes.last_handshake_timestamp IS NOT NULL ORDER BY nodes.last_handshake_timestamp DESC, nodes.connection_count DESC LIMIT " + maxCount)
        );

        final IpInflater ipInflater = new IpInflater();

        final MutableList<BitcoinNodeIpAddress> nodeIpAddresses = new MutableList<BitcoinNodeIpAddress>(rows.size());

        for (final Row row : rows) {
            final Long nodeId = row.getLong("id");
            final String host = row.getString("host");
            final Integer port  = row.getInteger("port");

            final Ip ip = ipInflater.fromString(host);

            final BitcoinNodeIpAddress nodeIpAddress = new BitcoinNodeIpAddress();
            nodeIpAddress.setIp(ip);
            nodeIpAddress.setPort(port);

            final NodeFeatures nodeFeatures = _inflateNodeFeatures(nodeId);
            nodeIpAddress.setNodeFeatures(nodeFeatures);

            nodeIpAddresses.add(nodeIpAddress);
        }

        return nodeIpAddresses;
    }

    public Integer getFailedConnectionCountForHost(final String host) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT SUM(nodes.connection_count) AS failed_connection_count FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.last_handshake_timestamp IS NULL GROUP BY hosts.id")
                .setParameter(host)
        );
        if (rows.isEmpty()) { return 0; }

        final Row row = rows.get(0);
        return row.getInteger("failed_connection_count");
    }

    /**
     * Marks all nodes at the Node's host as banned/unbanned--regardless of their port.
     */
    public void setIsBanned(final String host, final Boolean isBanned) throws DatabaseException {
        if (isBanned) {
            final Long now = _systemTime.getCurrentTimeInSeconds();
            _databaseConnection.executeSql(
                new Query("UPDATE hosts SET is_banned = 1, banned_timestamp = ? WHERE host = ?")
                    .setParameter(now)
                    .setParameter(host)
            );
        }
        else {
            _databaseConnection.executeSql(
                new Query("UPDATE hosts SET is_banned = 0 WHERE host = ?")
                    .setParameter(host)
            );
        }
    }

    public Boolean isBanned(final String host) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT is_banned FROM hosts WHERE host = ?")
                .setParameter(host)
        );

        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final Boolean isBanned = row.getBoolean("is_banned");

        return isBanned;
    }
}

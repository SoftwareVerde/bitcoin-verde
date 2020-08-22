package com.softwareverde.bitcoin.server.module.node.database.node.fullnode;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashSet;

public class FullNodeBitcoinNodeDatabaseManagerCore implements FullNodeBitcoinNodeDatabaseManager {

    protected final DatabaseManager _databaseManager;
    protected final SystemTime _systemTime = new SystemTime();

    protected NodeFeatures _inflateNodeFeatures(final Long nodeId) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final NodeFeatures nodeFeatures = new NodeFeatures();
        final java.util.List<Row> nodeFeatureRows = databaseConnection.query(
            new Query("SELECT id, feature FROM node_features WHERE node_id = ?")
                .setParameter(nodeId)
        );
        for (final Row nodeFeatureRow : nodeFeatureRows) {
            final String featureString = nodeFeatureRow.getString("feature");
            final NodeFeatures.Feature feature = NodeFeatures.Feature.fromString(featureString);
            if (feature == null) {
                Logger.debug("Unknown feature: " + featureString);
                continue;
            }

            nodeFeatures.enableFeature(feature);
        }

        return nodeFeatures;
    }

    protected NodeId _getNodeId(final Ip ip, final Integer port) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT nodes.id FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.port = ?")
                .setParameter(ip)
                .setParameter(port)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return NodeId.wrap(row.getLong("id"));
    }

    public FullNodeBitcoinNodeDatabaseManagerCore(final DatabaseManager databaseManager) {
        _databaseManager = databaseManager;
    }

    @Override
    public NodeId getNodeId(final BitcoinNode node) throws DatabaseException {
        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        return _getNodeId(ip, port);
    }

    @Override
    public void storeNode(final BitcoinNode node) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        if (ip == null) {
            Logger.debug("Unable to store node: " + node.getConnectionString());
            return;
        }

        final Integer port = node.getPort();
        final String userAgent = node.getUserAgent(); // May be null...

        synchronized (MUTEX) {
            final Long hostId;
            {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM hosts WHERE host = ?")
                        .setParameter(ip)
                );

                if (! rows.isEmpty()) {
                    final Row row = rows.get(0);
                    hostId = row.getLong("id");
                }
                else {
                    hostId = databaseConnection.executeSql(
                        new Query("INSERT INTO hosts (host) VALUES (?)")
                            .setParameter(ip)
                    );
                }
            }

            final java.util.List<Row> rows = databaseConnection.query(
                new Query("SELECT id FROM nodes WHERE host_id = ? AND port = ?")
                    .setParameter(hostId)
                    .setParameter(port)
            );

            final Long now = _systemTime.getCurrentTimeInSeconds();

            if (rows.isEmpty()) {
                databaseConnection.executeSql(
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

                databaseConnection.executeSql(
                    new Query("UPDATE nodes SET connection_count = (connection_count + 1), last_seen_timestamp = ? WHERE id = ?")
                        .setParameter(now)
                        .setParameter(nodeId)
                );
            }
        }
    }

    @Override
    public void updateLastHandshake(final BitcoinNode node) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return; }

        databaseConnection.executeSql(
            new Query("UPDATE nodes SET last_handshake_timestamp = ? WHERE id = ?")
                .setParameter(_systemTime.getCurrentTimeInSeconds())
                .setParameter(nodeId)
        );
    }

    @Override
    public void updateNodeFeatures(final BitcoinNode node) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final BitcoinNodeIpAddress nodeIpAddress = node.getLocalNodeIpAddress();

        if (nodeIpAddress != null) {
            final NodeId nodeId = _getNodeId(ip, port);
            if (nodeId == null) { return; }

            final MutableList<NodeFeatures.Feature> disabledFeatures = new MutableList<NodeFeatures.Feature>();
            final NodeFeatures nodeFeatures = nodeIpAddress.getNodeFeatures();
            for (final NodeFeatures.Feature feature : NodeFeatures.Feature.values()) {
                if (nodeFeatures.hasFeatureFlagEnabled(feature)) {
                    databaseConnection.executeSql(
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
                databaseConnection.executeSql(
                    new Query("DELETE FROM node_features WHERE node_id = ? AND feature IN (?)")
                        .setParameter(nodeId)
                        .setInClauseParameters(disabledFeatures, ValueExtractor.NODE_FEATURE)
                );
            }
        }
    }

    @Override
    public void updateUserAgent(final BitcoinNode node) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();
        final String userAgent = node.getUserAgent();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return; }

        databaseConnection.executeSql(
            new Query("UPDATE nodes SET user_agent = ? WHERE id = ?")
                .setParameter(userAgent)
                .setParameter(nodeId)
        );
    }

    @Override
    public Boolean updateBlockInventory(final BitcoinNode node, final List<Sha256Hash> blockHashes) throws DatabaseException {
        if (blockHashes.isEmpty()) { return false; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return false; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO node_blocks_inventory (node_id, hash) VALUES (?, ?)");
        for (final Sha256Hash blockHash : blockHashes) {
            batchedInsertQuery.setParameter(nodeId);
            batchedInsertQuery.setParameter(blockHash);
        }

        databaseConnection.executeSql(batchedInsertQuery);
        return (databaseConnection.getRowsAffectedCount() > 0);
    }

    @Override
    public void updateTransactionInventory(final BitcoinNode node, final List<Sha256Hash> transactionHashes) throws DatabaseException {
        if (transactionHashes.isEmpty()) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO node_transactions_inventory (node_id, hash) VALUES (?, ?)");
        for (final Sha256Hash transactionHash : transactionHashes) {
            batchedInsertQuery.setParameter(nodeId);
            batchedInsertQuery.setParameter(transactionHash);
        }

        databaseConnection.executeSql(batchedInsertQuery);
    }

    @Override
    public List<NodeId> filterNodesViaTransactionInventory(final List<NodeId> nodeIds, final Sha256Hash transactionHash, final FilterType filterType) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT node_id FROM node_transactions_inventory WHERE hash = ? AND node_id IN (?)")
                .setParameter(transactionHash)
                .setInClauseParameters(nodeIds, ValueExtractor.IDENTIFIER)
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

    @Override
    public List<NodeId> filterNodesViaBlockInventory(final List<NodeId> nodeIds, final Sha256Hash blockHash, final FilterType filterType) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT node_id FROM node_blocks_inventory WHERE hash = ? AND node_id IN (?)")
                .setParameter(blockHash)
                .setInClauseParameters(nodeIds, ValueExtractor.IDENTIFIER)
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

    @Override
    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id INNER JOIN node_features ON nodes.id = node_features.node_id WHERE nodes.last_handshake_timestamp IS NOT NULL AND hosts.is_banned = 0 AND node_features.feature IN (?) ORDER BY nodes.last_handshake_timestamp DESC, nodes.connection_count DESC LIMIT " + maxCount)
                .setInClauseParameters(requiredFeatures, ValueExtractor.NODE_FEATURE)
        );

        final MutableList<BitcoinNodeIpAddress> nodeIpAddresses = new MutableList<BitcoinNodeIpAddress>(rows.size());

        for (final Row row : rows) {
            final Long nodeId = row.getLong("id");
            final String ipString = row.getString("host");
            final Integer port  = row.getInteger("port");

            final Ip ip = Ip.fromString(ipString);
            if (ip == null) { continue; }

            final BitcoinNodeIpAddress nodeIpAddress = new BitcoinNodeIpAddress();
            nodeIpAddress.setIp(ip);
            nodeIpAddress.setPort(port);

            final NodeFeatures nodeFeatures = _inflateNodeFeatures(nodeId);
            nodeIpAddress.setNodeFeatures(nodeFeatures);

            nodeIpAddresses.add(nodeIpAddress);
        }

        return nodeIpAddresses;
    }

    @Override
    public List<BitcoinNodeIpAddress> findNodes(final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.is_banned = 0 WHERE nodes.last_handshake_timestamp IS NOT NULL ORDER BY nodes.last_handshake_timestamp DESC, nodes.connection_count DESC LIMIT " + maxCount)
        );

        final MutableList<BitcoinNodeIpAddress> nodeIpAddresses = new MutableList<BitcoinNodeIpAddress>(rows.size());

        for (final Row row : rows) {
            final Long nodeId = row.getLong("id");
            final String ipString = row.getString("host");
            final Integer port  = row.getInteger("port");

            final Ip ip = Ip.fromString(ipString);
            if (ip == null) { continue; }

            final BitcoinNodeIpAddress nodeIpAddress = new BitcoinNodeIpAddress();
            nodeIpAddress.setIp(ip);
            nodeIpAddress.setPort(port);

            final NodeFeatures nodeFeatures = _inflateNodeFeatures(nodeId);
            nodeIpAddress.setNodeFeatures(nodeFeatures);

            nodeIpAddresses.add(nodeIpAddress);
        }

        return nodeIpAddresses;
    }

    @Override
    public Integer getFailedConnectionCountForIp(final Ip ip) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT SUM(nodes.connection_count) AS failed_connection_count FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.last_handshake_timestamp IS NULL GROUP BY hosts.id")
                .setParameter(ip)
        );
        if (rows.isEmpty()) { return 0; }

        final Row row = rows.get(0);
        return row.getInteger("failed_connection_count");
    }

    @Override
    public Integer getFailedConnectionCountForIp(final Ip ip, final Long sinceTimestamp) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT SUM(nodes.connection_count) AS failed_connection_count FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.last_handshake_timestamp IS NULL AND nodes.last_seen_timestamp >= ? GROUP BY hosts.id")
                .setParameter(ip)
                .setParameter(sinceTimestamp)
        );
        if (rows.isEmpty()) { return 0; }

        final Row row = rows.get(0);
        return row.getInteger("failed_connection_count");
    }

    /**
     * Marks all nodes at the Node's ip as banned/unbanned--regardless of their port.
     */
    @Override
    public void setIsBanned(final Ip ip, final Boolean isBanned) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        if (isBanned) {
            final Long now = _systemTime.getCurrentTimeInSeconds();
            databaseConnection.executeSql(
                new Query("UPDATE hosts SET is_banned = 1, banned_timestamp = ? WHERE host = ?")
                    .setParameter(now)
                    .setParameter(ip)
            );
        }
        else {
            // Prevent the node from immediately being re-banned...
            databaseConnection.executeSql(
                new Query("DELETE node_features FROM node_features INNER JOIN nodes ON nodes.id = node_features.node_id INNER JOIN hosts ON hosts.id = nodes.host_id WHERE nodes.last_handshake_timestamp IS NULL AND host = ?")
                    .setParameter(ip)
            );
            databaseConnection.executeSql(
                new Query("DELETE nodes FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE nodes.last_handshake_timestamp IS NULL AND host = ?")
                    .setParameter(ip)
            );

            // Mark the node as not banned...
            databaseConnection.executeSql(
                new Query("UPDATE hosts SET is_banned = 0, banned_timestamp = NULL WHERE host = ?")
                    .setParameter(ip)
            );
        }
    }

    @Override
    public Boolean isBanned(final Ip ip) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT is_banned FROM hosts WHERE host = ?")
                .setParameter(ip)
        );

        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final Boolean isBanned = row.getBoolean("is_banned");

        return isBanned;
    }

    @Override
    public Boolean isBanned(final Ip ip, final Long sinceTimestamp) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT is_banned FROM hosts WHERE host = ? AND banned_timestamp >= ?")
                .setParameter(ip)
                .setParameter(sinceTimestamp)
        );

        if (rows.isEmpty()) { return false; }

        final Row row = rows.get(0);
        final Boolean isBanned = row.getBoolean("is_banned");

        return isBanned;
    }
}

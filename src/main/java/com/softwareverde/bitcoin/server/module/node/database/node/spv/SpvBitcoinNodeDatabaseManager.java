package com.softwareverde.bitcoin.server.module.node.database.node.spv;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.database.util.DatabaseUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.type.time.SystemTime;

public class SpvBitcoinNodeDatabaseManager implements BitcoinNodeDatabaseManager {

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

    public SpvBitcoinNodeDatabaseManager(final DatabaseManager databaseConnection) {
        _databaseManager = databaseConnection;
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
            Logger.warn("Unable to store node: " + node.getConnectionString());
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
                final String inClause = DatabaseUtil.createInClause(disabledFeatures);

                databaseConnection.executeSql(
                    new Query("DELETE FROM node_features WHERE node_id = ? AND feature IN (" + inClause + ")")
                        .setParameter(nodeId)
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
    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final Integer maxCount) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final String inClause = DatabaseUtil.createInClause(requiredFeatures);

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id INNER JOIN node_features ON nodes.id = node_features.node_id WHERE nodes.last_handshake_timestamp IS NOT NULL AND hosts.is_banned = 0 AND node_features.feature IN (" + inClause + ") ORDER BY nodes.last_handshake_timestamp DESC, nodes.connection_count DESC LIMIT " + maxCount)
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

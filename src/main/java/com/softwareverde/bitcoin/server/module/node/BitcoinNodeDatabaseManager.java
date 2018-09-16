package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.ip.IpInflater;
import com.softwareverde.util.type.time.SystemTime;

public class BitcoinNodeDatabaseManager {
    public static final Object MUTEX = new Object();

    protected final MysqlDatabaseConnection _databaseConnection;
    protected final SystemTime _systemTime = new SystemTime();

    protected String _createInClause(final List<?> list) {
        final StringBuilder disabledFeaturesInClauseBuilder = new StringBuilder();
        for (final Object item : list) {
            disabledFeaturesInClauseBuilder.append("'");
            disabledFeaturesInClauseBuilder.append(item.toString());
            disabledFeaturesInClauseBuilder.append("',");
        }
        return disabledFeaturesInClauseBuilder.substring(0, disabledFeaturesInClauseBuilder.length() - 1); // Remove the last comma..
    }

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

    public BitcoinNodeDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public void storeNode(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

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
                    new Query("INSERT INTO nodes (host_id, port, first_seen_timestamp, last_seen_timestamp) VALUES (?, ?, ?, ?)")
                        .setParameter(hostId)
                        .setParameter(port)
                        .setParameter(now)
                        .setParameter(now)
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

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.port = ?")
                .setParameter(host)
                .setParameter(port)
        );

        if (rows.isEmpty()) { return; }

        final Row row = rows.get(0);
        final Long nodeId = row.getLong("id");

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
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT nodes.id FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.host = ? AND nodes.port = ?")
                    .setParameter(host)
                    .setParameter(port)
            );

            if (rows.isEmpty()) { return; }

            final Row row = rows.get(0);
            final Long nodeId = row.getLong("id");

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
                final String inClause = _createInClause(disabledFeatures);

                _databaseConnection.executeSql(
                    new Query("DELETE FROM node_features WHERE node_id = ? AND feature IN (" + inClause + ")")
                        .setParameter(nodeId)
                );
            }
        }
    }

    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final Integer maxCount) throws DatabaseException {
        final String inClause = _createInClause(requiredFeatures);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id INNER JOIN node_features ON nodes.id = node_features.node_id WHERE hosts.is_banned = 0 AND node_features.feature IN (" + inClause + ") ORDER BY nodes.last_handshake_timestamp DESC LIMIT " + maxCount)
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
            new Query("SELECT nodes.id, hosts.host, nodes.port FROM nodes INNER JOIN hosts ON hosts.id = nodes.host_id WHERE hosts.is_banned = 0 ORDER BY nodes.last_handshake_timestamp DESC LIMIT " + maxCount)
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
        _databaseConnection.executeSql(
            new Query("UPDATE hosts SET is_banned = ? WHERE host = ?")
                .setParameter((isBanned ? 1 : 0))
                .setParameter(host)
        );
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

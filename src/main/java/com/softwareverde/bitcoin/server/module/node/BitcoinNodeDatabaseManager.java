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

        TransactionUtil.startTransaction(_databaseConnection);
        try {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id FROM nodes WHERE host = ? AND port = ?")
                    .setParameter(host)
                    .setParameter(port)
            );

            if (rows.isEmpty()) {
                _databaseConnection.executeSql(
                    new Query("INSERT INTO nodes (host, port, timestamp) VALUES (?, ?, ?)")
                        .setParameter(host)
                        .setParameter(port)
                        .setParameter(_systemTime.getCurrentTimeInSeconds())
                );
            }
            TransactionUtil.commitTransaction(_databaseConnection);
        }
        catch (final Exception exception) {
            TransactionUtil.rollbackTransaction(_databaseConnection);
            throw exception;
        }
    }

    public void updateLastHandshake(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        TransactionUtil.startTransaction(_databaseConnection);
        try {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id FROM nodes WHERE host = ? AND port = ?")
                    .setParameter(host)
                    .setParameter(port)
            );

            if (rows.isEmpty()) {
                TransactionUtil.rollbackTransaction(_databaseConnection);
                return;
            }

            final Row row = rows.get(0);
            final Long nodeId = row.getLong("id");

            _databaseConnection.executeSql(
                new Query("UPDATE nodes SET last_handshake_timestamp = ? WHERE id = ?")
                    .setParameter(_systemTime.getCurrentTimeInSeconds())
                    .setParameter(nodeId)
            );
        }
        catch (final Exception exception) {
            TransactionUtil.rollbackTransaction(_databaseConnection);
            throw exception;
        }
    }

    public void updateNodeFeatures(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        final BitcoinNodeIpAddress nodeIpAddress = node.getLocalNodeIpAddress();

        if (nodeIpAddress != null) {
            TransactionUtil.startTransaction(_databaseConnection);
            try {
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id FROM nodes WHERE host = ? AND port = ?")
                        .setParameter(host)
                        .setParameter(port)
                );

                if (rows.isEmpty()) {
                    TransactionUtil.rollbackTransaction(_databaseConnection);
                    return;
                }

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

                TransactionUtil.commitTransaction(_databaseConnection);
            }
            catch (final Exception exception) {
                TransactionUtil.rollbackTransaction(_databaseConnection);
                throw exception;
            }
        }
    }

    public List<BitcoinNodeIpAddress> findNodes(final List<NodeFeatures.Feature> requiredFeatures, final Integer maxCount) throws DatabaseException {
        final String inClause = _createInClause(requiredFeatures);

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT nodes.id, nodes.host, nodes.port FROM nodes INNER JOIN node_features ON nodes.id = node_features.node_id WHERE node_features.feature IN (" + inClause + ") ORDER BY nodes.last_handshake_timestamp DESC LIMIT " + maxCount)
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
            new Query("SELECT id, host, port FROM nodes ORDER BY last_handshake_timestamp DESC LIMIT " + maxCount)
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
}

package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.server.message.type.node.address.BitcoinNodeIpAddress;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.util.type.time.SystemTime;

public class BitcoinNodeDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final SystemTime _systemTime = new SystemTime();

    public BitcoinNodeDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public void storeNode(final BitcoinNode node) throws DatabaseException {
        final String host = node.getHost();
        final Integer port = node.getPort();

        TransactionUtil.startTransaction(_databaseConnection);
        try {
            final java.util.List<Row> rows = _databaseConnection.query(
                new Query("SELECT id FROM nodes WHERE ip = ? AND port = ?")
                    .setParameter(host)
                    .setParameter(port)
            );

            if (rows.isEmpty()) {
                _databaseConnection.executeSql(
                    new Query("INSERT INTO nodes (ip, port, timestamp) VALUES (?, ?, ?)")
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
                new Query("SELECT id FROM nodes WHERE ip = ? AND port = ?")
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
                    new Query("SELECT id FROM nodes WHERE ip = ? AND port = ?")
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
                            new Query("INSERT INTO node_features (node_id, feature) VALUES (?, ?)")
                                .setParameter(nodeId)
                                .setParameter(feature)
                        );
                    }
                    else {
                        disabledFeatures.add(feature);
                    }
                }

                if (! disabledFeatures.isEmpty()) {
                    final StringBuilder disabledFeaturesInClauseBuilder = new StringBuilder();
                    for (final NodeFeatures.Feature disabledFeature : disabledFeatures) {
                        disabledFeaturesInClauseBuilder.append("'");
                        disabledFeaturesInClauseBuilder.append(disabledFeature.toString());
                        disabledFeaturesInClauseBuilder.append("',");
                    }
                    final String inClause = disabledFeaturesInClauseBuilder.substring(0, disabledFeaturesInClauseBuilder.length() - 1); // Remove the last comma..

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
}

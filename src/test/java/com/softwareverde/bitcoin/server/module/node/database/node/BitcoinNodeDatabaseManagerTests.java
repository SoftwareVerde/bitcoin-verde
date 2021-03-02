package com.softwareverde.bitcoin.server.module.node.database.node;

import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.message.type.version.synchronize.BitcoinSynchronizeVersionMessageInflater;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BitcoinNodeDatabaseManagerTests extends IntegrationTest {
    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    public void _storeAndAssertNodeFeatures(final DatabaseManagerFactory databaseManagerFactory) throws Exception {
        // Setup
        final CachedThreadPool mainThreadPool = new CachedThreadPool(0, 1000L);
        mainThreadPool.start();

        final BitcoinNode bitcoinNode = new BitcoinNode("127.0.0.1", 8333, mainThreadPool, new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                return nodeFeatures;
            }
        }) {{
            _synchronizeVersionMessage = (new BitcoinSynchronizeVersionMessageInflater(_masterInflater)).fromBytes(HexUtil.hexStringToByteArray("E3E1F3E876657273696F6E00000000006B0000006ACB57267F110100B50100000000000035F9AB5F00000000000000000000000000000000000000000000FFFF43954527F9F3000000000000000000000000000000000000FFFF00000000000000A8D9B1778B1A14152F426974636F696E2056657264653A312E342E302F5B160A0001"));
        }};

        // Action
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            nodeDatabaseManager.storeNode(bitcoinNode);
            nodeDatabaseManager.updateLastHandshake(bitcoinNode);
            nodeDatabaseManager.updateNodeFeatures(bitcoinNode);
            nodeDatabaseManager.updateUserAgent(bitcoinNode);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }

        final NodeFeatures storedFeatures = new NodeFeatures();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            for (final Row row : databaseConnection.query(new Query("SELECT * FROM node_features"))) {
                final NodeFeatures.Feature nodeFeature = NodeFeatures.Feature.fromString(row.getString("feature"));
                if (nodeFeature != null) {
                    storedFeatures.enableFeature(nodeFeature);
                }
            }
        }

        // Assert
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.BITCOIN_CASH_ENABLED));
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED));
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.BLOCKCHAIN_INDEX_ENABLED));
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.BLOOM_CONNECTIONS_ENABLED));
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.SLP_INDEX_ENABLED));
        Assert.assertTrue(storedFeatures.isFeatureEnabled(NodeFeatures.Feature.XTHIN_PROTOCOL_ENABLED));

        mainThreadPool.stop();
    }

    @Test
    public void should_store_node_features_full_node() throws Exception {
        _storeAndAssertNodeFeatures(_fullNodeDatabaseManagerFactory);
    }

    @Test
    public void should_store_node_features_spv_node() throws Exception {
        _storeAndAssertNodeFeatures(_spvDatabaseManagerFactory);
    }
}

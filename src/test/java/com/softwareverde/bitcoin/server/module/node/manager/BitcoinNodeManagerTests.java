package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.node.feature.LocalNodeFeatures;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.manager.banfilter.BanFilterCore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.BitcoinNodeFactory;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.concurrent.threadpool.CachedThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPoolFactory;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BitcoinNodeManagerTests extends IntegrationTest {
    @Before @Override
    public void before() throws Exception {
        super.before();
    }

    @After @Override
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_ban_node_after_multiple_failed_inbound_connections() throws Exception {
        // Setup
        final CachedThreadPool threadPool = new CachedThreadPool(32, 1L);
        threadPool.start();

        final ThreadPoolFactory nodeThreadPoolFactory = new ThreadPoolFactory() {
            @Override
            public ThreadPool newThreadPool() {
                return threadPool;
            }
        };

        final LocalNodeFeatures localNodeFeatures = new LocalNodeFeatures() {
            @Override
            public NodeFeatures getNodeFeatures() {
                final NodeFeatures nodeFeatures = new NodeFeatures();
                nodeFeatures.enableFeature(NodeFeatures.Feature.BITCOIN_CASH_ENABLED);
                nodeFeatures.enableFeature(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                return nodeFeatures;
            }
        };

        final NodeInitializer nodeInitializer;
        { // Initialize NodeInitializer...
            final NodeInitializer.Context nodeInitializerContext = new NodeInitializer.Context();
            nodeInitializerContext.synchronizationStatus = _synchronizationStatus;
            nodeInitializerContext.threadPoolFactory = nodeThreadPoolFactory;
            nodeInitializerContext.localNodeFeatures = localNodeFeatures;
            nodeInitializerContext.binaryPacketFormat = BitcoinProtocolMessage.BINARY_PACKET_FORMAT;
            nodeInitializer = new NodeInitializer(nodeInitializerContext);
        }

        final long banDurationInSeconds = 3L;

        final BanFilterCore banFilter = new BanFilterCore(_fullNodeDatabaseManagerFactory);
        banFilter.setBanDuration(banDurationInSeconds);

        final BitcoinNodeManager.Context bitcoinNodeContext = new BitcoinNodeManager.Context();
        bitcoinNodeContext.maxNodeCount = 1;
        bitcoinNodeContext.databaseManagerFactory = _fullNodeDatabaseManagerFactory;
        bitcoinNodeContext.nodeFactory = new BitcoinNodeFactory(BitcoinProtocolMessage.BINARY_PACKET_FORMAT, nodeThreadPoolFactory, localNodeFeatures);;
        bitcoinNodeContext.networkTime = new MutableNetworkTime();
        bitcoinNodeContext.nodeInitializer = nodeInitializer;
        bitcoinNodeContext.banFilter = banFilter;
        bitcoinNodeContext.memoryPoolEnquirer = null;
        bitcoinNodeContext.synchronizationStatusHandler = _synchronizationStatus;
        bitcoinNodeContext.threadPool = threadPool;
        bitcoinNodeContext.systemTime = new SystemTime();

        final BitcoinNodeManager bitcoinNodeManager = new BitcoinNodeManager(bitcoinNodeContext);

        final String host = "127.0.0.1";
        final Ip ip = Ip.fromString(host);

        final MutableList<BitcoinNode> bitcoinNodes = new MutableList<BitcoinNode>();

        // Action
        // Spam the NodeManager with 10 connections that never handshake.
        for (int i = 0; i < 10; ++i) {
            final BitcoinNode bitcoinNode = new BitcoinNode(host, i, _threadPool, localNodeFeatures);
            bitcoinNodeManager.addNode(bitcoinNode);
            bitcoinNodes.add(bitcoinNode);
        }

        // Disconnect the nodes to prevent needing to waiting for connection timeout.
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            bitcoinNode.disconnect();
        }

        Thread.sleep((banDurationInSeconds * 1000) / 2); // Wait for async callbacks to execute.

        // Assert the BitcoinNodes are banned.
        Assert.assertTrue(banFilter.isIpBanned(ip));

        // Wait for the ban to expire.
        Thread.sleep(banDurationInSeconds * 1000L);

        // Assert the BitcoinNodes are no longer banned.
        Assert.assertFalse(banFilter.isIpBanned(ip));

        // Spam the NodeManager with 10 new failing connections.
        bitcoinNodes.clear();
        for (int i = 0; i < 10; ++i) {
            final BitcoinNode bitcoinNode = new BitcoinNode(host, i, _threadPool, localNodeFeatures);
            bitcoinNodeManager.addNode(bitcoinNode);
            bitcoinNodes.add(bitcoinNode);
        }

        // Disconnect the nodes to prevent needing to waiting for connection timeout.
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            bitcoinNode.disconnect();
        }

        Thread.sleep(500L); // Allow for any callbacks to executed.

        // Assert the BitcoinNodes are re-banned.
        Assert.assertTrue(banFilter.isIpBanned(ip));

        threadPool.stop();
    }
}

package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.fake.FakeBitcoinNode;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PendingBlockDatabaseManagerTests extends IntegrationTest {

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    protected void _insertFakePendingBlock(final Sha256Hash blockHash, final Long blockHeight) throws DatabaseException {
        final SystemTime systemTime = new SystemTime();

        try (final DatabaseConnection databaseConnection = _database.newConnection()) {
            databaseConnection.executeSql(
                new Query("INSERT INTO pending_blocks (hash, timestamp, priority) VALUES (?, ?, ?)")
                    .setParameter(blockHash)
                    .setParameter(systemTime.getCurrentTimeInSeconds())
                    .setParameter(blockHeight)
            );
        }
    }

    protected Map<NodeId, BitcoinNode> _insertFakePeers(final Integer peerCount) throws DatabaseException {
        final HashMap<NodeId, BitcoinNode> nodes = new HashMap<NodeId, BitcoinNode>(peerCount);
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            for (int i = 0; i < peerCount; i++) {
                final BitcoinNode node = new FakeBitcoinNode("192.168.1." + i, 8333, null, null);
                nodeDatabaseManager.storeNode(node);

                final NodeId nodeId = nodeDatabaseManager.getNodeId(node);
                nodes.put(nodeId, node);
            }
        }
        return nodes;
    }

    protected void _insertFakePeerInventory(final Sha256Hash blockHash, final BitcoinNode node) throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            nodeDatabaseManager.updateBlockInventory(node, new ImmutableList<Sha256Hash>(blockHash));
        }
    }

    @Test
    public void should_return_priority_incomplete_blocks() throws DatabaseException {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            synchronized (BlockHeaderDatabaseManager.MUTEX) { // Store the Genesis Block...
                final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                blockDatabaseManager.storeBlock(genesisBlock);
            }

            // Create fake peers...
            final Map<NodeId, BitcoinNode> nodes = _insertFakePeers(6);

            final HashMap<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>(1024);
            { // Store 1024 pending blocks...
                for (int i = 0; i < 1024; ++i) {
                    final Long blockHeight = (i + 1L);
                    final Sha256Hash blockHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.integerToBytes(blockHeight)));

                    blockHeights.put(blockHash, blockHeight);
                    _insertFakePendingBlock(blockHash, blockHeight);

                    // Ensure all peers have the block as available inventory...
                    for (final NodeId nodeId : nodes.keySet()) {
                        final BitcoinNode node = nodes.get(nodeId);
                        _insertFakePeerInventory(blockHash, node);
                    }
                }
            }

            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            { // Should return pendingBlocks when available...
                final List<NodeId> connectedNodeIds = new MutableList<NodeId>(nodes.keySet());

                // Action
                final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(connectedNodeIds, 1024);

                // Assert
                Assert.assertEquals(1024, downloadPlan.size());
            }

            { // Should return no pendingBlocks when no nodes connected...
                final List<NodeId> connectedNodeIds = new MutableList<NodeId>(0);

                // Action
                final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(connectedNodeIds, 1024);

                // Assert
                Assert.assertEquals(0, downloadPlan.size());
            }

            { // Should return correct peer for available pendingBlocks...
                final MutableList<NodeId> connectedNodeIds = new MutableList<NodeId>();
                final NodeId connectedNodeId = nodes.keySet().iterator().next();
                connectedNodeIds.add(connectedNodeId);

                // Action
                final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(connectedNodeIds, 16);

                // Assert
                Assert.assertEquals(16, downloadPlan.size());

                for (final PendingBlockId pendingBlockId : downloadPlan.keySet()) {
                    final NodeId nodeId = downloadPlan.get(pendingBlockId);
                    Assert.assertEquals(connectedNodeId, nodeId);
                }
            }
        }
    }

    @Test
    public void should_not_return_priority_incomplete_blocks_for_missing_inventory() throws DatabaseException {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockInflater blockInflater = _masterInflater.getBlockInflater();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            synchronized (BlockHeaderDatabaseManager.MUTEX) { // Store the Genesis Block...
                final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                blockDatabaseManager.storeBlock(genesisBlock);
            }

            // Create fake peers...
            final Map<NodeId, BitcoinNode> nodes = _insertFakePeers(2);

            final NodeId evenBlockHeightNodeId;
            final NodeId oddBlockHeightNodeId;
            {
                final Iterator<NodeId> iterator = nodes.keySet().iterator();
                evenBlockHeightNodeId = iterator.next();
                oddBlockHeightNodeId = iterator.next();
            }

            final HashMap<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>(1024);
            { // Store 1024 pending blocks, having even blockHeights assigned to the first node, and odd blockHeights assigned to the second...
                for (int i = 0; i < 1024; ++i) {
                    final Long blockHeight = (i + 1L);
                    final Sha256Hash blockHash = Sha256Hash.wrap(HashUtil.sha256(ByteUtil.integerToBytes(blockHeight)));

                    blockHeights.put(blockHash, blockHeight);
                    _insertFakePendingBlock(blockHash, blockHeight);

                    // Ensure all peers have the block as available inventory...
                    int nodeIndex = 0;
                    for (final NodeId nodeId : nodes.keySet()) {
                        final BitcoinNode node = nodes.get(nodeId);

                        final boolean blockHeightIsEven = ((blockHeight % 2) == 0);
                        if (blockHeightIsEven) {
                            if (Util.areEqual(nodeId, evenBlockHeightNodeId)) {
                                _insertFakePeerInventory(blockHash, node);
                            }
                        }
                        else {
                            if (Util.areEqual(nodeId, oddBlockHeightNodeId)) {
                                _insertFakePeerInventory(blockHash, node);
                            }
                        }

                        nodeIndex += 1;
                    }
                }
            }

            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            { // Should return pendingBlocks when available...
                final List<NodeId> connectedNodeIds = new MutableList<NodeId>(nodes.keySet());

                // Action
                final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(connectedNodeIds, 1024);

                // Assert
                Assert.assertEquals(1024, downloadPlan.size());

                for (final PendingBlockId pendingBlockId : downloadPlan.keySet()) {
                    final NodeId nodeId = downloadPlan.get(pendingBlockId);
                    final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                    final Long blockHeight = blockHeights.get(blockHash);

                    final boolean blockHeightIsEven = ((blockHeight % 2) == 0);
                    if (blockHeightIsEven) {
                        Assert.assertEquals(evenBlockHeightNodeId, nodeId);
                    }
                    else {
                        Assert.assertEquals(oddBlockHeightNodeId, nodeId);
                    }
                }
            }
        }
    }
}

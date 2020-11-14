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
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
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

    protected void _insertFakePeerInventory(final BitcoinNode node, final Long blockHeight, final Sha256Hash blockHash) throws DatabaseException {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            nodeDatabaseManager.updateBlockInventory(node, blockHeight, blockHash);
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
                        _insertFakePeerInventory(node, blockHeight, blockHash);
                    }
                }
            }

            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            { // Should return pendingBlocks when available...
                final List<NodeId> connectedNodeIds = new MutableList<NodeId>(nodes.keySet());

                // Action
                final List<PendingBlockId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(1024);

                // Assert
                Assert.assertEquals(1024, downloadPlan.getCount());
            }
        }
    }
}

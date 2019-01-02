package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.type.time.SystemTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

public class PendingBlockDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
    }

    protected void _insertFakeBlock(final Sha256Hash blockHash, final Long blockHeight) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            databaseConnection.executeSql(
                new Query("INSERT INTO blocks (hash, block_height, merkle_root, timestamp, difficulty, nonce, chain_work) VALUES (?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(blockHash)
                    .setParameter(blockHeight)
                    .setParameter(MerkleRoot.EMPTY_HASH)
                    .setParameter(0L)
                    .setParameter(Difficulty.BASE_DIFFICULTY.encode())
                    .setParameter(0L)
                    .setParameter(Sha256Hash.EMPTY_HASH)
            );
        }
    }

    protected void _insertFakePendingBlock(final Sha256Hash blockHash, final Long blockHeight) throws DatabaseException {
        final SystemTime systemTime = new SystemTime();

        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            databaseConnection.executeSql(
                new Query("INSERT INTO pending_blocks (hash, timestamp, priority) VALUES (?, ?, ?)")
                    .setParameter(blockHash)
                    .setParameter(systemTime.getCurrentTimeInSeconds())
                    .setParameter(blockHeight)
            );
        }
    }

    @Test
    public void should_return_priority_incomplete_blocks() throws DatabaseException {
        // Setup
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final HashMap<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>();
            final Long[] skippedBlockHeights = new Long[]{ };
            final HashSet<Long> skippedBlockHeightSet = new HashSet<Long>(Arrays.asList(skippedBlockHeights));
            for (int i = 0; i < 1024; ++i) {
                final Long blockHeight = (i + 1L);
                final Sha256Hash blockHash = MutableSha256Hash.wrap(BitcoinUtil.sha256(ByteUtil.integerToBytes(blockHeight)));

                blockHeights.put(blockHash, blockHeight);

                _insertFakePendingBlock(blockHash, blockHeight);

                if (! skippedBlockHeightSet.contains(blockHeight)) {
                    _insertFakeBlock(blockHash, blockHeight);
                }
            }

            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

            // Action
            final List<Tuple<Sha256Hash, Sha256Hash>> downloadPlan = pendingBlockDatabaseManager.selectPriorityPendingBlocksWithUnknownNodeInventory(new MutableList<NodeId>(0));

            // Assert
            for (final Tuple<Sha256Hash, Sha256Hash> tuple : downloadPlan) {
                System.out.println(tuple.first + " (" + blockHeights.get(tuple.first) + ") -> " + tuple.second + " (" + blockHeights.get(tuple.second) + ")");
            }

            Assert.assertEquals(Long.valueOf(1L), blockHeights.get(downloadPlan.get(0).first));
            Assert.assertEquals(Long.valueOf(500L), blockHeights.get(downloadPlan.get(0).second));

            Assert.assertEquals(1, downloadPlan.getSize());
        }
    }

    @Test
    public void should_return_priority_incomplete_blocks_separated_by_block_height() throws DatabaseException {
        // Setup
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final HashMap<Sha256Hash, Long> blockHeights = new HashMap<Sha256Hash, Long>();
            final Long[] skippedBlockHeights = new Long[]{ 2L, 50L, 55L, 56L, 60L };
            final HashSet<Long> skippedBlockHeightSet = new HashSet<Long>(Arrays.asList(skippedBlockHeights));
            for (int i = 0; i < 1024; ++i) {
                final Long blockHeight = (i + 1L);
                final Sha256Hash blockHash = MutableSha256Hash.wrap(BitcoinUtil.sha256(ByteUtil.integerToBytes(blockHeight)));

                blockHeights.put(blockHash, blockHeight);

                _insertFakePendingBlock(blockHash, blockHeight);

                if (! skippedBlockHeightSet.contains(blockHeight)) {
                    _insertFakeBlock(blockHash, blockHeight);
                }
            }

            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

            // Action
            final List<Tuple<Sha256Hash, Sha256Hash>> downloadPlan = pendingBlockDatabaseManager.selectPriorityPendingBlocksWithUnknownNodeInventory(new MutableList<NodeId>(0));

            // Assert
            for (final Tuple<Sha256Hash, Sha256Hash> tuple : downloadPlan) {
                System.out.println(tuple.first + " (" + blockHeights.get(tuple.first) + ") -> " + tuple.second + " (" + blockHeights.get(tuple.second) + ")");
            }

            Assert.assertEquals(Long.valueOf(1L), blockHeights.get(downloadPlan.get(0).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(0).second));

            Assert.assertEquals(Long.valueOf(2L), blockHeights.get(downloadPlan.get(1).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(1).second));

            Assert.assertEquals(Long.valueOf(3L), blockHeights.get(downloadPlan.get(2).first));
            Assert.assertEquals(Long.valueOf(49L), blockHeights.get(downloadPlan.get(2).second));

            Assert.assertEquals(Long.valueOf(50L), blockHeights.get(downloadPlan.get(3).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(3).second));

            Assert.assertEquals(Long.valueOf(51L), blockHeights.get(downloadPlan.get(4).first));
            Assert.assertEquals(Long.valueOf(54L), blockHeights.get(downloadPlan.get(4).second));

            Assert.assertEquals(Long.valueOf(55L), blockHeights.get(downloadPlan.get(5).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(5).second));

            Assert.assertEquals(Long.valueOf(56L), blockHeights.get(downloadPlan.get(6).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(6).second));

            Assert.assertEquals(Long.valueOf(57L), blockHeights.get(downloadPlan.get(7).first));
            Assert.assertEquals(Long.valueOf(59L), blockHeights.get(downloadPlan.get(7).second));

            Assert.assertEquals(Long.valueOf(60L), blockHeights.get(downloadPlan.get(8).first));
            Assert.assertEquals(null, blockHeights.get(downloadPlan.get(8).second));

            Assert.assertEquals(Long.valueOf(61L), blockHeights.get(downloadPlan.get(9).first));
            Assert.assertEquals(Long.valueOf(500L), blockHeights.get(downloadPlan.get(9).second));

            Assert.assertEquals(10, downloadPlan.getSize());
        }
    }

    @Test
    public void should_return_single_priority_incomplete_block() throws DatabaseException {
        // Setup
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {

            final Sha256Hash blockHash = MutableSha256Hash.wrap(BitcoinUtil.sha256(ByteUtil.integerToBytes(124)));

            _insertFakePendingBlock(blockHash, 124L);

            final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);

            // Action
            final List<Tuple<Sha256Hash, Sha256Hash>> downloadPlan = pendingBlockDatabaseManager.selectPriorityPendingBlocksWithUnknownNodeInventory(new MutableList<NodeId>(0));

            // Assert
            Assert.assertEquals(blockHash, downloadPlan.get(0).first);
            Assert.assertNull(downloadPlan.get(0).second);

            Assert.assertEquals(1, downloadPlan.getSize());
        }
    }
}

package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.*;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockRelationship;
import com.softwareverde.bitcoin.server.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.Util;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

// NOTE: These tests were back-ported from DocChain...
public class BlockchainDatabaseManagerTests2 extends IntegrationTest {

    @Before
    public void setup() throws Exception {
        _resetDatabase();

        final BlockInflater blockInflater = new BlockInflater();
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            if (! Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, genesisBlock.getHash())) {
                throw new RuntimeException("Error inflating Genesis Block.");
            }

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.insertBlock(genesisBlock);
            }
        }
    }

    private Sha256Hash _insertTestBlocks(final Sha256Hash startingHash, final int blockCount) throws DatabaseException {
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

            final Random random = new Random();
            Sha256Hash hash = ((startingHash == null) ? BlockHeader.GENESIS_BLOCK_HASH : startingHash);

            final BlockHasher blockHasher = new BlockHasher();

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            for (int i=0; i<blockCount; i++) {
                final byte[] nonceBytes = new byte[4];
                random.nextBytes(nonceBytes);


                final MutableBlock block = new MutableBlock();
                block.setPreviousBlockHash(hash);
                block.setVersion(1L);
                block.setTimestamp((long) i+100);
                block.setNonce(ByteUtil.bytesToLong(nonceBytes));
                block.setDifficulty(Difficulty.BASE_DIFFICULTY);

                block.addTransaction(genesisBlock.getCoinbaseTransaction());

                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.insertBlock(block);
                }

                hash = blockHasher.calculateBlockHash(block);
            }
            // return final hash
            return hash;
        }
    }

    private Sha256Hash _insertTestBlocks(final int blockCount) throws DatabaseException {
        return _insertTestBlocks(null, blockCount);
    }

    @Test
    public void should_return_timestamp_of_14th_ancestor_relative_to_current_block_with_16_blocks_on_one_chain() throws DatabaseException {
        // Setup
        final Sha256Hash headHash = _insertTestBlocks(16);

        // Action
        long timestamp = -1;
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId headHashId = blockHeaderDatabaseManager.getBlockHeaderId(headHash);
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(headHashId, 15);
            final BlockHeader ancestorBlockHeader = blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            timestamp = ancestorBlockHeader.getTimestamp();
        }

        // Test
        Assert.assertEquals(timestamp, 100); // (100 + 0) (since the 16th ancestor will be the first block and the block timestamp is in milliseconds)
    }

    @Test
    public void should_return_timestamp_of_current_block_for_0th_ancestor_with_16_blocks_on_one_chain() throws DatabaseException {
        // Setup
        final Sha256Hash headHash = _insertTestBlocks(16);

        // Action
        long timestamp = -1;
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId headHashId = blockHeaderDatabaseManager.getBlockHeaderId(headHash);
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(headHashId, 0);
            final BlockHeader ancestorBlockHeader = blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            timestamp = ancestorBlockHeader.getTimestamp();
        }

        // Test
        Assert.assertEquals(timestamp, 115); // (100 + 15) (since the 0th ancestor will be the 16th/final block and the block timestamp is in milliseconds)
    }

    @Test
    public void should_return_timestamp_of_100th_ancestor_with_200_blocks_on_one_chain() throws DatabaseException {
        // Setup
        final Sha256Hash headHash = _insertTestBlocks(200);

        // Action
        long timestamp = -1;
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockId headHashId = blockHeaderDatabaseManager.getBlockHeaderId(headHash);
            final BlockId ancestorBlockId = blockHeaderDatabaseManager.getAncestorBlockId(headHashId, 100);
            final BlockHeader ancestorBlockHeader = blockHeaderDatabaseManager.getBlockHeader(ancestorBlockId);
            timestamp = ancestorBlockHeader.getTimestamp();
        }

        // Test
        Assert.assertEquals(timestamp, 199); // (100 + 99) (since the 100th ancestor will be the 99th block and the block timestamp is in milliseconds)
    }

    @Test
    public void should_create_fork() throws DatabaseException {
        // Setup
        final Sha256Hash forkStartHash = _insertTestBlocks(10);
        final Sha256Hash fork1Hash = _insertTestBlocks(forkStartHash, 4);
        final Sha256Hash fork2Hash = _insertTestBlocks(forkStartHash, 3);

        // Action
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId fork1Id = blockHeaderDatabaseManager.getBlockHeaderId(fork1Hash);
            final BlockchainSegmentId fork1SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork1Id);
            final BlockId fork2Id = blockHeaderDatabaseManager.getBlockHeaderId(fork2Hash);
            final BlockchainSegmentId fork2SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork2Id);

            final BlockId forkStartId = blockHeaderDatabaseManager.getBlockHeaderId(forkStartHash);
            final BlockchainSegmentId forkStartSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(forkStartId);
//            final BlockchainSegment forkStartBlockchainSegment = blockchainDatabaseManager.getBlockchainSegment(forkStartSegmentId);
//            final BlockchainSegment fork1BlockchainSegment = blockchainDatabaseManager.getBlockchainSegment(fork1SegmentId);
//            final BlockchainSegment fork2BlockchainSegment = blockchainDatabaseManager.getBlockchainSegment(fork2SegmentId);

            // Test
            Assert.assertNotEquals(fork1SegmentId, fork2SegmentId);
//            Assert.assertEquals(10, forkStartBlockchainSegment.getBlockHeight().longValue());
//            Assert.assertEquals(4, fork1BlockchainSegment.getBlockHeight().longValue());
//            Assert.assertEquals(3, fork2BlockchainSegment.getBlockHeight().longValue());

        }
    }

    @Test
    public void should_correctly_report_connected_and_unconnected_blockchain_segments() throws DatabaseException {
        // Setup
        final Sha256Hash forkStartHash1 = _insertTestBlocks(10);
        final Sha256Hash fork1Hash = _insertTestBlocks(forkStartHash1, 10);
        final Sha256Hash fork2Hash = _insertTestBlocks(forkStartHash1, 10);
        final Sha256Hash forkStartHash2 = _insertTestBlocks(fork2Hash, 10);
        final Sha256Hash fork3Hash = _insertTestBlocks(forkStartHash2, 10);
        final Sha256Hash fork4Hash = _insertTestBlocks(forkStartHash2, 10);

        // Action
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);

            final BlockId forkStartId1 = blockHeaderDatabaseManager.getBlockHeaderId(forkStartHash1);
            final BlockchainSegmentId forkStart1SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(forkStartId1);
            final BlockId fork1Id = blockHeaderDatabaseManager.getBlockHeaderId(fork1Hash);
            final BlockchainSegmentId fork1SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork1Id);
            final BlockId fork2Id = blockHeaderDatabaseManager.getBlockHeaderId(fork2Hash);
            final BlockchainSegmentId fork2SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork2Id);
            final BlockId forkStartId2 = blockHeaderDatabaseManager.getBlockHeaderId(forkStartHash2);
            final BlockchainSegmentId forkStart2SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(forkStartId2);
            final BlockId fork3Id = blockHeaderDatabaseManager.getBlockHeaderId(fork3Hash);
            final BlockchainSegmentId fork3SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork3Id);
            final BlockId fork4Id = blockHeaderDatabaseManager.getBlockHeaderId(fork4Hash);
            final BlockchainSegmentId fork4SegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(fork4Id);

            // Test
            // all are connected to fork start 1
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart1SegmentId, fork1SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart1SegmentId, fork2SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart1SegmentId, fork3SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart1SegmentId, fork4SegmentId, BlockRelationship.ANY));

            // fork 1 and fork 2 are not connected to each other
            Assert.assertFalse(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork1SegmentId, fork2SegmentId, BlockRelationship.ANY));

            // fork 3 and fork 4 are connected to fork start 2 and fork 2
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart2SegmentId, fork3SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(forkStart2SegmentId, fork4SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork2SegmentId, fork3SegmentId, BlockRelationship.ANY));
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork2SegmentId, fork4SegmentId, BlockRelationship.ANY));

            // fork 3 and fork 4 are not connected to each other, nor fork 1
            Assert.assertFalse(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork3SegmentId, fork4SegmentId, BlockRelationship.ANY));
            Assert.assertFalse(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork1SegmentId, fork3SegmentId, BlockRelationship.ANY));
            Assert.assertFalse(blockchainDatabaseManager.areBlockchainSegmentsConnected(fork1SegmentId, fork4SegmentId, BlockRelationship.ANY));
        }
    }
}

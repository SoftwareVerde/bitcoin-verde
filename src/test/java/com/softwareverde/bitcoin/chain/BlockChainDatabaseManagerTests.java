package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockChainDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() throws Exception {
        _resetDatabase();
    }

    @Test
    public void should_initialize_chain_on_genesis_block() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0
            0   - Genesis Block ("genesisBlock")

                [0]
                 |

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0)

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        Assert.assertTrue(genesisBlock.isValid());

        // Action
        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);
        final List<BlockChainId> blockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(0L, row.getLong("block_height").longValue());
        Assert.assertEquals(1L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());

        Assert.assertEquals(1, blockChainIds.getSize());
        Assert.assertEquals(1L, blockChainIds.get(0).longValue());
    }

    @Test
    public void should_grow_chain_after_genesis_block() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 1'
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            1'  - Forking Block (sharing Genesis Block as its parent) ("contentiousBlock")

                 [1]
                  |
                 [0]
                  |

            The established chains should be:
                [0,1]   - Chain #1 (Block Height: 1)

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());

        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        // Action
        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);
        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block1Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(1L, row.getLong("block_height").longValue());
        Assert.assertEquals(2L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());

        Assert.assertEquals(1, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
    }

    @Test
    public void should_continue_to_grow_chain_after_genesis_block() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 1'
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            1'  - Forking Block (sharing Genesis Block as its parent) ("contentiousBlock")

                 [2]
                  |
                 [1]
                  |
                 [0]
                  |

            The established chains should be:
                [0,2]   - Chain #1 (Block Height: 2)

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());

        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final BlockId block2Id = blockDatabaseManager.storeBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);
        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block2BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2Id);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block2Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(2L, row.getLong("block_height").longValue());
        Assert.assertEquals(3L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(block2Id).longValue());

        Assert.assertEquals(1, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2BlockChainIds.getSize());
        Assert.assertEquals(1L, block2BlockChainIds.get(0).longValue());
    }

    @Test
    public void should_create_additional_chains_if_next_block_is_a_fork() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 1'
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            1'  - Forking Block (sharing Genesis Block as its parent) ("block1Prime")

            [1]  [1']
             |    |
             +---[0]
                  |

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockChain")
                [0,1]   - Chain #2 (Block Height: 1) ("refactoredBlockChain")
                [0,1']  - Chain #3 (Block Height: 1) ("newBlockChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block1Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);
        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block1PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1PrimeId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments ORDER BY id ASC"));
        Assert.assertEquals(3, rows.size());

        { // Chain #1 (baseBlockChain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockChain)
            final Row row = rows.get(1);
            Assert.assertEquals(block1Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block1PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block1PrimeId).longValue());

        Assert.assertEquals(2, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, genesisBlockBlockChainIds.get(1).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block1PrimeBlockChainIds.get(0).longValue());
    }

    @Test
    public void should_create_additional_chains_when_next_block_is_a_fork_of_a_previous_block() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 2 -> 1'
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            2   - Actual Bitcoin Block #2 ("block2")
            1'  - Forking Block (sharing Genesis Block as its parent) ("block1Prime")

            [2]
             |
            [1]  [1']
             |    |
             +---[0]
                  |

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockChain")
                [0,2]   - Chain #2 (Block Height: 2) ("refactoredBlockChain")
                [0,1']  - Chain #3 (Block Height: 1) ("newBlockChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block1Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.storeBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        // Action
        final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);
        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block2BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2Id);
        final List<BlockChainId> block1PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1PrimeId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block1PrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments ORDER BY id ASC"));
        Assert.assertEquals(3, rows.size());

        { // Chain #1 (baseBlockChain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockChain)
            final Row row = rows.get(1);
            Assert.assertEquals(block2Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block1PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block2Id).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block1PrimeId).longValue());

        Assert.assertEquals(2, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, genesisBlockBlockChainIds.get(1).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2BlockChainIds.getSize());
        Assert.assertEquals(1L, block2BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block1PrimeBlockChainIds.get(0).longValue());
    }

    @Test
    public void should_create_additional_2nd_chain_when_yet_another_block_is_a_fork_of_a_previous_block() throws Exception {
        // Setup

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 2 -> 3 -> 1' -> 2' -> 1''
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            2   - Actual Bitcoin Block #2 ("block2")
            3   - Actual Bitcoin Block #3 ("block3")
            1'  - First Forking Block (sharing Genesis Block as its parent) ("block1Prime")
            2'  - First Child of First Forking Block ("block2Prime")
            1'' - Second Forking Block (sharing Genesis Block as its parent) ("block1DoublePrime")

            [3]
             |
            [2]  [2']
             |    |
            [1]  [1'] [1'']
             |    |    |
             +---[0]---+
                  |

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockChain")
                [0,3]   - Chain #2 (Block Height: 3) ("refactoredBlockChain")
                [0,2']  - Chain #3 (Block Height: 2) ("newBlockChain")
                [0,1''] - Chain #4 (Block Height: 1) ("newestBlockChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_2));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block1Prime.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.storeBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.storeBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final BlockId block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        // Action
        final BlockId block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);
        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block2BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2Id);
        final List<BlockChainId> block3BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block3Id);
        final List<BlockChainId> block1PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1PrimeId);
        final List<BlockChainId> block2PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2PrimeId);
        final List<BlockChainId> block1DoublePrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1DoublePrimeId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block1PrimeId.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block1DoublePrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments ORDER BY id ASC"));
        Assert.assertEquals(4, rows.size());

        { // Chain #1 (baseBlockChain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockChain)
            final Row row = rows.get(1);
            Assert.assertEquals(block3Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(3L, row.getLong("block_height").longValue());
            Assert.assertEquals(3L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block2PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #4 (newestBlockChain)
            final Row row = rows.get(3);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        // Chain 1
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId).longValue());

        Assert.assertEquals(3, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, genesisBlockBlockChainIds.get(1).longValue());
        Assert.assertEquals(3L, genesisBlockBlockChainIds.get(2).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2BlockChainIds.getSize());
        Assert.assertEquals(1L, block2BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block3BlockChainIds.getSize());
        Assert.assertEquals(1L, block3BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block1PrimeBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block2PrimeBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1DoublePrimeBlockChainIds.getSize());
        Assert.assertEquals(3L, block1DoublePrimeBlockChainIds.get(0).longValue());
    }

    @Test
    public void should_resume_growing_main_chain_if_it_used_to_be_an_alt_chain() throws Exception {
        // Setup

        // NOTE: This is mostly a duplicate of should_create_additional_2nd_chain_when_yet_another_block_is_a_fork_of_a_previous_block;
        //  the only difference is the order in which the blocks are received.

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 1' -> 2' -> 1'' -> 2 -> 3
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            2   - Actual Bitcoin Block #2 ("block2")
            3   - Actual Bitcoin Block #3 ("block3")
            1'  - First Forking Block (sharing Genesis Block as its parent) ("block1Prime")
            2'  - First Child of First Forking Block ("block2Prime")
            1'' - Second Forking Block (sharing Genesis Block as its parent) ("block1DoublePrime")

                                                                                                           [3]
                                                                                                            |
                                                        [2']           [2']           [2]  [2']            [2]  [2']
                                                         |              |              |    |               |    |
                   [1]       [1]       [1']     [1]     [1']      [1]  [1'] [1'']     [1]  [1'] [1'']      [1]  [1'] [1'']
                    |         |         |        |       |         |    |    |         |    |    |          |    |    |
        [0]        [0]        +---[0]---+        +--[0]--+         +---[0]---+         +---[0]---+          +---[0]---+
         |          |              |                 |                  |                   |                    |
              ->         ->                  ->               ->                  ->                   ->
    --------------------------------------------------------------------------------------------------------------------

            [3]
             |
            [2]  [2']
             |    |
            [1]  [1'] [1'']
             |    |    |
             +---[0]---+
                  |

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockChain")
                [0,3]   - Chain #2 (Block Height: 3) ("refactoredBlockChain")
                [0,2']  - Chain #3 (Block Height: 2) ("newBlockChain")
                [0,1''] - Chain #4 (Block Height: 1) ("newestBlockChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_2));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block1Prime.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        // Action
        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final BlockId block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final BlockId block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        final BlockId block2Id = blockDatabaseManager.storeBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.storeBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block2BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2Id);
        final List<BlockChainId> block3BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block3Id);
        final List<BlockChainId> block1PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1PrimeId);
        final List<BlockChainId> block2PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2PrimeId);
        final List<BlockChainId> block1DoublePrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1DoublePrimeId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());
        Assert.assertEquals(4L, block2PrimeId.longValue());
        Assert.assertEquals(5L, block1DoublePrimeId.longValue());
        Assert.assertEquals(6L, block2Id.longValue());
        Assert.assertEquals(7L, block3Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments ORDER BY id ASC"));
        Assert.assertEquals(4, rows.size());

        { // Chain #1 (baseBlockChain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockChain)
            final Row row = rows.get(1);
            Assert.assertEquals(block3Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(3L, row.getLong("block_height").longValue());
            Assert.assertEquals(3L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block2PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #4 (newestBlockChain)
            final Row row = rows.get(3);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        // Chain 1
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId).longValue());

        Assert.assertEquals(3, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, genesisBlockBlockChainIds.get(1).longValue());
        Assert.assertEquals(3L, genesisBlockBlockChainIds.get(2).longValue());
        Assert.assertEquals(1, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2BlockChainIds.getSize());
        Assert.assertEquals(1L, block2BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block3BlockChainIds.getSize());
        Assert.assertEquals(1L, block3BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block1PrimeBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block2PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block2PrimeBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block1DoublePrimeBlockChainIds.getSize());
        Assert.assertEquals(3L, block1DoublePrimeBlockChainIds.get(0).longValue());
    }

    @Test
    public void should_maintain_chains_and_descendant_forks_when_an_old_block_is_forked() throws Exception {
        // Setup

        // NOTE: This is mostly a duplicate of should_create_additional_2nd_chain_when_yet_another_block_is_a_fork_of_a_previous_block;
        //  the only difference is the order in which the blocks are received.

        /* The scenario's ordering that blocks are received in is: 0 -> 1 -> 2 -> 3 -> 4 -> 2' -> 3' -> 1''
            0   - Genesis Block ("genesisBlock")
            1   - Actual Bitcoin Block #1 ("block1")
            2   - Actual Bitcoin Block #2 ("block2")
            3   - Actual Bitcoin Block #3 ("block3")
            4   - Actual Bitcoin Block #4 ("block4")
            2'  - First Forking Block (sharing block1 as its parent) ("block2Prime")
            3'  - First Child of First Forking Block ("block3Prime")
            1'' - Second Forking Block (sharing Genesis Block as its parent) ("block1DoublePrime")

       [4]                     [4]
        |                       |
       [3]   [3']              [3]   [3']
        |     |                 |     |
       [2]   [2']              [2]   [2']
        |     |                 |     |
       [1]----+                [1]----+    [1'']
        |                       |           |
       [0]                     [0]----------+
        |                       |
                      ->
    ---------------------------------------------

            The established chains during setup should originally be:
                [0,1]   - Chain #1 (Block Height: 1) ("baseBlockChain")
                [1,4]   - Chain #2 (Block Height: 4) ("refactoredBlockChain")
                [1,3']  - Chain #3 (Block Height: 3) ("originalForkedChain")

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockChain")
                [1,4]   - Chain #2 (Block Height: 4) ("refactoredBlockChain")
                [1,3']  - Chain #3 (Block Height: 3) ("originalForkedChain")
                [0,1]   - Chain #4 (Block Height: 1) ("refactoredBaseChain")
                [0,1''] - Chain #5 (Block Height: 1) ("mostRecentlyForkedChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block4 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
        final Block block3Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_3));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block4.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block3Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Original Chain 1
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());

        // Original Chain 2
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());
        Assert.assertEquals(block3.getHash(), block4.getPreviousBlockHash());

        // Original Chain 3
        Assert.assertEquals(block1.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertEquals(block2Prime.getHash(), block3Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block2.getHash(), block2Prime.getHash());
        Assert.assertNotEquals(block3.getHash(), block3Prime.getHash());

        // New Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        // Establish Original Blocks/Chains...
        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.storeBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.storeBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final BlockId block4Id = blockDatabaseManager.storeBlock(block4);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block4);

        final BlockId block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final BlockId block3PrimeId = blockDatabaseManager.storeBlock(block3Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3Prime);

        // Action
        final BlockId block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        final List<BlockChainId> genesisBlockBlockChainIds = blockChainDatabaseManager.getBlockChainIds(genesisBlockId);
        final List<BlockChainId> block1BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1Id);
        final List<BlockChainId> block2BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2Id);
        final List<BlockChainId> block3BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block3Id);
        final List<BlockChainId> block4BlockChainIds = blockChainDatabaseManager.getBlockChainIds(block4Id);
        final List<BlockChainId> block2PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block2PrimeId);
        final List<BlockChainId> block3PrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block3PrimeId);
        final List<BlockChainId> block1DoublePrimeBlockChainIds = blockChainDatabaseManager.getBlockChainIds(block1DoublePrimeId);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block4Id.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block3PrimeId.longValue());
        Assert.assertEquals(8L, block1DoublePrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chain_segments ORDER BY id ASC"));
        Assert.assertEquals(5, rows.size());

        { // Chain #1 (baseBlockChain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockChain)
            final Row row = rows.get(1);
            Assert.assertEquals(block4Id, row.getLong("head_block_id"));
            Assert.assertEquals(block1Id, row.getLong("tail_block_id"));
            Assert.assertEquals(4L, row.getLong("block_height").longValue());
            Assert.assertEquals(3L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (originalForkedChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block3PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(block1Id, row.getLong("tail_block_id"));
            Assert.assertEquals(3L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #4 (refactoredBaseChain)
            final Row row = rows.get(3);
            Assert.assertEquals(block1Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #5 (mostRecentlyForkedChain)
            final Row row = rows.get(4);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        // Chain 1
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block3Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block4Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block2PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block3PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainSegmentId(block1Id).longValue());

        // Chain 5
        Assert.assertEquals(5L, blockDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId).longValue());

        Assert.assertEquals(3, genesisBlockBlockChainIds.getSize());
        Assert.assertEquals(1L, genesisBlockBlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, genesisBlockBlockChainIds.get(1).longValue());
        Assert.assertEquals(3L, genesisBlockBlockChainIds.get(2).longValue());

        Assert.assertEquals(2, block1BlockChainIds.getSize());
        Assert.assertEquals(1L, block1BlockChainIds.get(0).longValue());
        Assert.assertEquals(2L, block1BlockChainIds.get(1).longValue());

        Assert.assertEquals(1, block2BlockChainIds.getSize());
        Assert.assertEquals(1L, block2BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block3BlockChainIds.getSize());
        Assert.assertEquals(1L, block3BlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block4BlockChainIds.getSize());
        Assert.assertEquals(1L, block4BlockChainIds.get(0).longValue());

        Assert.assertEquals(1, block2PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block2PrimeBlockChainIds.get(0).longValue());
        Assert.assertEquals(1, block3PrimeBlockChainIds.getSize());
        Assert.assertEquals(2L, block3PrimeBlockChainIds.get(0).longValue());

        Assert.assertEquals(1, block1DoublePrimeBlockChainIds.getSize());
        Assert.assertEquals(3L, block1DoublePrimeBlockChainIds.get(0).longValue());
    }
}

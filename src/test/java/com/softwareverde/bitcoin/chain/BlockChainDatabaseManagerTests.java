package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockChainDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
        _resetDatabase();
        _resetCache();
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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        Assert.assertTrue(genesisBlock.isValid());

        // Action
        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        // Action
        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final BlockId block2Id = blockDatabaseManager.insertBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block1Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block1Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.insertBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        // Action
        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));
        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_2));
        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block1Prime.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.insertBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.insertBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final BlockId block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        // Action
        final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));
        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_2));
        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block1Prime.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        // Action
        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final BlockId block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        final BlockId block2Id = blockDatabaseManager.insertBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.insertBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

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

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block4 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
        final Block block3Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_3));
        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());
        Assert.assertTrue(block3.isValid());
        Assert.assertTrue(block4.isValid());
        Assert.assertTrue(block2Prime.isValid());
        Assert.assertTrue(block3Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());

        // Original Chain 1
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());

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
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1DoublePrime.getHash());

        // Establish Original Blocks/Chains...
        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1Id = blockDatabaseManager.insertBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final BlockId block2Id = blockDatabaseManager.insertBlock(block2);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final BlockId block3Id = blockDatabaseManager.insertBlock(block3);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final BlockId block4Id = blockDatabaseManager.insertBlock(block4);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block4);

        final BlockId block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final BlockId block3PrimeId = blockDatabaseManager.insertBlock(block3Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block3Prime);

        // Action
        final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

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
    }

    @Test
    public void should_link_correct_transaction_outputs_when_duplicate_transaction_found_across_forks_0() throws Exception {
        // Setup

        /*
            This test's purpose is to assert that transactions that spend outputs whose hash match transactions
            that are included in multiple blocks are paired to the correct fork.

            For instance, transactionOne could be included in both blockOne and blockTwo, where blockOne and blockTwo belong
            to separate forks.  When a new transaction, newTransaction, spends the output included in transactionOne,
            there are two possible matches when assigning the input's previous_transaction_output_id.  The correct match
            is the output that belongs to the transaction that was included the same blockChain as newTransaction.
         */

        /* The scenario's ordering that blocks are received in is: 0 -> 1' -> 1'' -> 2'
            0   - Genesis Block ("genesisBlock")
            1'' - Fake Bitcoin Block #1 ("block1DoublePrime")
            1'  - Forking Block (sharing Genesis Block as its parent) ("block1Prime")
            2'  - Block #2 Prime that spends a Tx that exists in both 1'' and 1'. ("block2Prime")

           block2Prime spends a transactionOutput found in both block1DoublePrime and block1Prime.

                 [2']
                  |
            [1''][1']
             |    |
             +---[0]
                  |

            The block2 transaction should be linked to the transactionOutput on block1Prime, not block1DoublePrime.

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D61900000000004617A0E5CD278219D09DF557717952EE95EA7045CCC17FAA34952D11B9D40ED25956FC5AFFFF001D0CD8FA580101000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E300EEA944FDA020000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC00000000"));
        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D61900000000001ADC819E4F74B03FF2F5190AB235361CDCC09E6B37E59918857965CC2F5F5B515956FC5AFFFF001D706CA7DC0101000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E300EEA944F0C010000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC00000000"));
        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000737D4C7036345CF70FC4939FFC994A75F4EDE84F3E206F9EF83BEA000000000091D4741CA6F8AC523E21D5E95E492A05A55C94E4419116B986EB5DD7020440349980FC5AFFFF001D846E62900201000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E307388291D1B000000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC0000000001000000014617A0E5CD278219D09DF557717952EE95EA7045CCC17FAA34952D11B9D40ED20000000000FFFFFFFF0100111024010000001976A9140010C980A8F5B8E072AE8175376788AB56E5858688AC00000000")); // NOTE: The transaction in this block does not properly unlock the transactionOutput...

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1Prime.isValid());
        Assert.assertTrue(block1DoublePrime.isValid());
        Assert.assertTrue(block2Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1DoublePrime.getPreviousBlockHash());
        Assert.assertNotEquals(block1Prime.getHash(), block1DoublePrime.getHash());

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        // Action
        final BlockId block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1PrimeId.longValue());
        Assert.assertEquals(3L, block1DoublePrimeId.longValue());
        Assert.assertEquals(4L, block2PrimeId.longValue());

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
            Assert.assertEquals(block2PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockChain)
            final Row row = rows.get(2);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block1PrimeId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainSegmentId(block2PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId).longValue());

        { // Validate that the Transaction Input spent in block2Prime is linked to block1Prime and not block1DoublePrime...
            final Transaction transaction = block2Prime.getTransactions().get(1);
            Assert.assertNotEquals(new ImmutableSha256Hash(), transaction.getTransactionInputs().get(0).getPreviousOutputTransactionHash()); // Assert this transactionInput is not a coinbase transaction...
            final Sha256Hash transactionHash = transaction.getHash();
            final Long transactionId = databaseConnection.query(new Query("SELECT id FROM transactions WHERE hash = ?").setParameter(transactionHash)).get(0).getLong("id");
            final Long previousTransactionOutputId = databaseConnection.query(new Query("SELECT previous_transaction_output_id FROM transaction_inputs WHERE transaction_id = ?").setParameter(transactionId)).get(0).getLong("previous_transaction_output_id");
            final Long blockIdContainingSpentTransaction = databaseConnection.query(new Query("SELECT transactions.block_id FROM transactions INNER JOIN transaction_outputs ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?").setParameter(previousTransactionOutputId)).get(0).getLong("block_id");
            final String blockHashContainingSpentTransaction = databaseConnection.query(new Query("SELECT hash FROM blocks WHERE id = ?").setParameter(blockIdContainingSpentTransaction)).get(0).getString("hash");

            Assert.assertEquals(block1Prime.getHash().toString(), blockHashContainingSpentTransaction);
        }
    }

    @Test
    public void should_link_correct_transaction_outputs_when_duplicate_transaction_found_across_forks_1() throws Exception {
        // Setup

        /* The scenario's the same as the above test, except the ordering that blocks are received in is: 0 -> 1'' -> 1' -> 2'

                 [2']
                  |
            [1''][1']
             |    |
             +---[0]
                  |

            The block2 transaction should (still) be linked to the transactionOutput on block1Prime, not block1DoublePrime.

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D61900000000004617A0E5CD278219D09DF557717952EE95EA7045CCC17FAA34952D11B9D40ED25956FC5AFFFF001D0CD8FA580101000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E300EEA944FDA020000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC00000000"));
        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D61900000000001ADC819E4F74B03FF2F5190AB235361CDCC09E6B37E59918857965CC2F5F5B515956FC5AFFFF001D706CA7DC0101000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E300EEA944F0C010000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC00000000"));
        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000737D4C7036345CF70FC4939FFC994A75F4EDE84F3E206F9EF83BEA000000000091D4741CA6F8AC523E21D5E95E492A05A55C94E4419116B986EB5DD7020440349980FC5AFFFF001D846E62900201000000010000000000000000000000000000000000000000000000000000000000000000000000001C16152F56657264652D426974636F696E3A302E307388291D1B000000FFFFFFFF0100F2052A010000001976A91404A0EB1B8E56E5EE4692C9AAB921376A3B58435588AC0000000001000000014617A0E5CD278219D09DF557717952EE95EA7045CCC17FAA34952D11B9D40ED20000000000FFFFFFFF0100111024010000001976A9140010C980A8F5B8E072AE8175376788AB56E5858688AC00000000")); // NOTE: The transaction in this block does not properly unlock the transactionOutput...

        final BlockId genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockId block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        final BlockId block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        // Action
        final BlockId block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        // Assert
        { // Validate that the Transaction Input spent in block2Prime is linked to block1Prime and not block1DoublePrime...
            final Transaction transaction = block2Prime.getTransactions().get(1);
            Assert.assertNotEquals(new ImmutableSha256Hash(), transaction.getTransactionInputs().get(0).getPreviousOutputTransactionHash()); // Assert this transactionInput is not a coinbase transaction...
            final Sha256Hash transactionHash = transaction.getHash();
            final Long transactionId = databaseConnection.query(new Query("SELECT id FROM transactions WHERE hash = ?").setParameter(transactionHash)).get(0).getLong("id");
            final Long previousTransactionOutputId = databaseConnection.query(new Query("SELECT previous_transaction_output_id FROM transaction_inputs WHERE transaction_id = ?").setParameter(transactionId)).get(0).getLong("previous_transaction_output_id");
            final Long blockIdContainingSpentTransaction = databaseConnection.query(new Query("SELECT transactions.block_id FROM transactions INNER JOIN transaction_outputs ON (transaction_outputs.transaction_id = transactions.id) WHERE transaction_outputs.id = ?").setParameter(previousTransactionOutputId)).get(0).getLong("block_id");
            final String blockHashContainingSpentTransaction = databaseConnection.query(new Query("SELECT hash FROM blocks WHERE id = ?").setParameter(blockIdContainingSpentTransaction)).get(0).getString("hash");

            Assert.assertEquals(block1Prime.getHash().toString(), blockHashContainingSpentTransaction);
        }
    }

    @Test
    public void should_not_increment_chain_block_height_when_processing_a_previously_processed_block() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));

        blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

        Assert.assertEquals(0, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(1, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockCount().intValue());

        blockDatabaseManager.storeBlock(block1);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        Assert.assertEquals(1, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(2, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockCount().intValue());

        // Action
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Assert
        Assert.assertEquals(1, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(2, blockChainDatabaseManager.getBlockChainSegment(blockChainSegmentId).getBlockCount().intValue());
    }

    @Test
    public void should_not_link_transaction_when_transaction_output_is_only_found_on_separate_fork() throws Exception {
        // TODO
    }
}

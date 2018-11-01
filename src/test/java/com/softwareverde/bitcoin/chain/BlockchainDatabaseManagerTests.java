package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.block.*;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.transaction.MutableTransaction;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.coinbase.MutableCoinbaseTransaction;
import com.softwareverde.bitcoin.transaction.input.MutableTransactionInput;
import com.softwareverde.bitcoin.transaction.input.TransactionInput;
import com.softwareverde.bitcoin.transaction.locktime.LockTime;
import com.softwareverde.bitcoin.transaction.locktime.SequenceNumber;
import com.softwareverde.bitcoin.transaction.output.MutableTransactionOutput;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.ScriptBuilder;
import com.softwareverde.bitcoin.transaction.script.runner.ScriptRunner;
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.key.PrivateKey;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainDatabaseManagerTests extends IntegrationTest {

    @Before
    public void setup() {
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
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        Assert.assertTrue(genesisBlock.isValid());

        // Action
        final BlockId genesisBlockId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(0L, row.getLong("block_height").longValue());
        Assert.assertEquals(1L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
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
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());

        final BlockId genesisBlockId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
        }

        // Action
        final BlockId block1Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block1Id = blockDatabaseManager.insertBlock(block1);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block1Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(1L, row.getLong("block_height").longValue());
        Assert.assertEquals(2L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
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
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block2.isValid());

        final BlockId genesisBlockId;
        final BlockId block1Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
        }

        // Action
        final BlockId block2Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block2Id = blockDatabaseManager.insertBlock(block2);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block2Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(2L, row.getLong("block_height").longValue());
        Assert.assertEquals(3L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
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
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockchain")
                [0,1]   - Chain #2 (Block Height: 1) ("refactoredBlockchain")
                [0,1']  - Chain #3 (Block Height: 1) ("newBlockchain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain1.BLOCK_1));

        Assert.assertTrue(genesisBlock.isValid());
        Assert.assertTrue(block1.isValid());
        Assert.assertTrue(block1Prime.isValid());

        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertNotEquals(block1.getHash(), block1Prime.getHash());

        final BlockId genesisBlockId;
        final BlockId block1Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
        }

        // Action
        final BlockId block1PrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments ORDER BY id ASC"));
        Assert.assertEquals(3, rows.size());

        { // Chain #1 (baseBlockchain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockchain)
            final Row row = rows.get(1);
            Assert.assertEquals(block1Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockchain)
            final Row row = rows.get(2);
            Assert.assertEquals(block1PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
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
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockchain")
                [0,2]   - Chain #2 (Block Height: 2) ("refactoredBlockchain")
                [0,1']  - Chain #3 (Block Height: 1) ("newBlockchain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

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

        final BlockId genesisBlockId;
        final BlockId block1Id;
        final BlockId block2Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
            block2Id = blockDatabaseManager.insertBlock(block2);
        }

        // Action
        final BlockId block1PrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block1PrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments ORDER BY id ASC"));
        Assert.assertEquals(3, rows.size());

        { // Chain #1 (baseBlockchain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockchain)
            final Row row = rows.get(1);
            Assert.assertEquals(block2Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockchain)
            final Row row = rows.get(2);
            Assert.assertEquals(block1PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
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
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockchain")
                [0,3]   - Chain #2 (Block Height: 3) ("refactoredBlockchain")
                [0,2']  - Chain #3 (Block Height: 2) ("newBlockchain")
                [0,1''] - Chain #4 (Block Height: 1) ("newestBlockchain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

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

        final BlockId genesisBlockId;
        final BlockId block1Id;
        final BlockId block2Id;
        final BlockId block3Id;
        final BlockId block1PrimeId;
        final BlockId block2PrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
            block2Id = blockDatabaseManager.insertBlock(block2);
            block3Id = blockDatabaseManager.insertBlock(block3);
            block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
            block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
        }

        // Action
        final BlockId block1DoublePrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block1PrimeId.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block1DoublePrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments ORDER BY id ASC"));
        Assert.assertEquals(4, rows.size());

        { // Chain #1 (baseBlockchain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockchain)
            final Row row = rows.get(1);
            Assert.assertEquals(block3Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(3L, row.getLong("block_height").longValue());
            Assert.assertEquals(3L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockchain)
            final Row row = rows.get(2);
            Assert.assertEquals(block2PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #4 (newestBlockchain)
            final Row row = rows.get(3);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        // Chain 1
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1DoublePrimeId).longValue());
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
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockchain")
                [0,3]   - Chain #2 (Block Height: 3) ("refactoredBlockchain")
                [0,2']  - Chain #3 (Block Height: 2) ("newBlockchain")
                [0,1''] - Chain #4 (Block Height: 1) ("newestBlockchain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

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
        final BlockId genesisBlockId;
        final BlockId block1Id;
        final BlockId block1PrimeId;
        final BlockId block2PrimeId;
        final BlockId block1DoublePrimeId;
        final BlockId block2Id;
        final BlockId block3Id;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
            block1PrimeId = blockDatabaseManager.insertBlock(block1Prime);
            block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
            block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
            block2Id = blockDatabaseManager.insertBlock(block2);
            block3Id = blockDatabaseManager.insertBlock(block3);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());
        Assert.assertEquals(4L, block2PrimeId.longValue());
        Assert.assertEquals(5L, block1DoublePrimeId.longValue());
        Assert.assertEquals(6L, block2Id.longValue());
        Assert.assertEquals(7L, block3Id.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments ORDER BY id ASC"));
        Assert.assertEquals(4, rows.size());

        { // Chain #1 (baseBlockchain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockchain)
            final Row row = rows.get(1);
            Assert.assertEquals(block3Id, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(3L, row.getLong("block_height").longValue());
            Assert.assertEquals(3L, row.getLong("block_count").longValue());
        }

        { // Chain #3 (newBlockchain)
            final Row row = rows.get(2);
            Assert.assertEquals(block2PrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(2L, row.getLong("block_height").longValue());
            Assert.assertEquals(2L, row.getLong("block_count").longValue());
        }

        { // Chain #4 (newestBlockchain)
            final Row row = rows.get(3);
            Assert.assertEquals(block1DoublePrimeId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(1L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        // Chain 1
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1DoublePrimeId).longValue());
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
                [0,1]   - Chain #1 (Block Height: 1) ("baseBlockchain")
                [1,4]   - Chain #2 (Block Height: 4) ("refactoredBlockchain")
                [1,3']  - Chain #3 (Block Height: 3) ("originalForkedChain")

            The established chains should be:
                [0,0]   - Chain #1 (Block Height: 0) ("baseBlockchain")
                [1,4]   - Chain #2 (Block Height: 4) ("refactoredBlockchain")
                [1,3']  - Chain #3 (Block Height: 3) ("originalForkedChain")
                [0,1]   - Chain #4 (Block Height: 1) ("refactoredBaseChain")
                [0,1''] - Chain #5 (Block Height: 1) ("mostRecentlyForkedChain")

         */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);

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
        final BlockId genesisBlockId;
        final BlockId block1Id;
        final BlockId block2Id;
        final BlockId block3Id;
        final BlockId block4Id;
        final BlockId block2PrimeId;
        final BlockId block3PrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            genesisBlockId = blockDatabaseManager.insertBlock(genesisBlock);
            block1Id = blockDatabaseManager.insertBlock(block1);
            block2Id = blockDatabaseManager.insertBlock(block2);
            block3Id = blockDatabaseManager.insertBlock(block3);
            block4Id = blockDatabaseManager.insertBlock(block4);
            block2PrimeId = blockDatabaseManager.insertBlock(block2Prime);
            block3PrimeId = blockDatabaseManager.insertBlock(block3Prime);
        }

        // Action
        final BlockId block1DoublePrimeId;
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            block1DoublePrimeId = blockDatabaseManager.insertBlock(block1DoublePrime);
        }

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block4Id.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block3PrimeId.longValue());
        Assert.assertEquals(8L, block1DoublePrimeId.longValue());

        final java.util.List<Row> rows = databaseConnection.query(new Query("SELECT * FROM blockchain_segments ORDER BY id ASC"));
        Assert.assertEquals(5, rows.size());

        { // Chain #1 (baseBlockchain)
            final Row row = rows.get(0);
            Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
            Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
            Assert.assertEquals(0L, row.getLong("block_height").longValue());
            Assert.assertEquals(1L, row.getLong("block_count").longValue());
        }

        { // Chain #2 (refactoredBlockchain)
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
        Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block3Id).longValue());
        Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block4Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2PrimeId).longValue());
        Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block3PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());

        // Chain 5
        Assert.assertEquals(5L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1DoublePrimeId).longValue());
    }

    @Test
    public void should_not_increment_chain_block_height_when_processing_a_previously_processed_block() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            blockDatabaseManager.storeBlock(genesisBlock);
        }

        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        Assert.assertEquals(0, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(1, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockCount().intValue());

        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            blockDatabaseManager.storeBlock(block1);
        }

        Assert.assertEquals(1, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(2, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockCount().intValue());

        // Action
        blockchainDatabaseManager.updateBlockchainsForNewBlock(block1);

        // Assert
        Assert.assertEquals(1, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockHeight().intValue());
        Assert.assertEquals(2, blockchainDatabaseManager.getBlockchainSegment(blockchainSegmentId).getBlockCount().intValue());
    }
}

class Void {
    protected TransactionInput _createCoinbaseTransactionInput() {
        final MutableTransactionInput mutableTransactionInput = new MutableTransactionInput();
        mutableTransactionInput.setPreviousOutputTransactionHash(Sha256Hash.EMPTY_HASH);
        mutableTransactionInput.setPreviousOutputIndex(0);
        mutableTransactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
        mutableTransactionInput.setUnlockingScript((new ScriptBuilder()).pushString("Mined via Bitcoin-Verde.").buildUnlockingScript());
        return mutableTransactionInput;
    }

    protected MutableTransactionOutput _createTransactionOutput(final Address payToAddress) {
        final MutableTransactionOutput transactionOutput = new MutableTransactionOutput();
        transactionOutput.setAmount(50L * Transaction.SATOSHIS_PER_BITCOIN);
        transactionOutput.setIndex(0);
        transactionOutput.setLockingScript(ScriptBuilder.payToAddress(payToAddress));
        return transactionOutput;
    }

    public void Void() throws Exception {
        final AddressInflater addressInflater = new AddressInflater();
        final BlockInflater blockInflater = new BlockInflater();

        final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));
        final Block customBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D539CC56649FFFF001D4A0CDDD801010000000100000000000000000000000000000000000000000000000000000000000000000000000020184D696E65642076696120426974636F696E2D56657264652E06313134353332FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));

        final Integer tenMinutesInSeconds = (60 * 10);

        final MutableBlock mutableBlock = new MutableBlock();
        mutableBlock.setPreviousBlockHash(block5.getHash());
        mutableBlock.setDifficulty(block5.getDifficulty());
        mutableBlock.setTimestamp(block5.getTimestamp() + (tenMinutesInSeconds));
        mutableBlock.setVersion(block5.getVersion());

        final PrivateKey privateKey = PrivateKey.parseFromHexString("9F40477DAB2F6822360E6C690F8278DB73E536156A402BBBE798A85DCBE1A8AC");
        final Address payToAddress = addressInflater.fromPrivateKey(privateKey);

        {
            final MutableCoinbaseTransaction coinbaseTransaction = new MutableCoinbaseTransaction();
            coinbaseTransaction.setVersion(Transaction.VERSION);
            coinbaseTransaction.setLockTime(LockTime.MIN_TIMESTAMP);
            coinbaseTransaction.addTransactionInput(_createCoinbaseTransactionInput());
            coinbaseTransaction.addTransactionOutput(_createTransactionOutput(payToAddress));
            mutableBlock.addTransaction(coinbaseTransaction);
        }

        {
            final MutableTransaction transaction = new MutableTransaction();
            transaction.setVersion(Transaction.VERSION);
            transaction.setLockTime(LockTime.MIN_TIMESTAMP);
            final TransactionOutput outputBeingSpent;
            {
                final Transaction transactionToSpend = customBlock6.getCoinbaseTransaction();
                outputBeingSpent = transactionToSpend.getTransactionOutputs().get(0);

                final MutableTransactionInput transactionInput = new MutableTransactionInput();
                transactionInput.setPreviousOutputTransactionHash(transactionToSpend.getHash());
                transactionInput.setPreviousOutputIndex(0);
                transactionInput.setSequenceNumber(SequenceNumber.MAX_SEQUENCE_NUMBER);
                transactionInput.setUnlockingScript(UnlockingScript.EMPTY_SCRIPT);
                transaction.addTransactionInput(transactionInput);
            }
            transaction.addTransactionOutput(_createTransactionOutput(addressInflater.fromPrivateKey(privateKey)));

            final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), Long.MAX_VALUE);
            signatureContext.setShouldSignInputScript(0, true, outputBeingSpent);
            final TransactionSigner transactionSigner = new TransactionSigner();
            final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

            final TransactionInput transactionInput = signedTransaction.getTransactionInputs().get(0);
            final MutableContext context = new MutableContext();
            context.setCurrentScript(null);
            context.setTransactionInputIndex(0);
            context.setTransactionInput(transactionInput);
            context.setTransaction(signedTransaction);
            context.setBlockHeight(6L);
            context.setTransactionOutputBeingSpent(outputBeingSpent);
            context.setCurrentScriptLastCodeSeparatorIndex(0);
            final ScriptRunner scriptRunner = new ScriptRunner();
            final Boolean outputIsUnlocked = scriptRunner.runScript(outputBeingSpent.getLockingScript(), transactionInput.getUnlockingScript(), context);
            Assert.assertTrue(outputIsUnlocked);

            mutableBlock.addTransaction(signedTransaction);
        }

        final BlockDeflater blockDeflater = new BlockDeflater();

        System.out.println(privateKey);
        System.out.println(payToAddress.toBase58CheckEncoded());

        final Miner miner = new Miner(4, 0);
        miner.setShouldMutateTimestamp(true);
        final Block minedBlock = miner.mineBlock(mutableBlock);
        System.out.println(minedBlock.getHash());
        System.out.println(blockDeflater.toBytes(minedBlock));
    }
}
package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.address.Address;
import com.softwareverde.bitcoin.address.AddressInflater;
import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.miner.Miner;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
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
import com.softwareverde.bitcoin.transaction.script.runner.context.MutableTransactionContext;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.HashType;
import com.softwareverde.bitcoin.transaction.script.signature.hashtype.Mode;
import com.softwareverde.bitcoin.transaction.script.unlocking.UnlockingScript;
import com.softwareverde.bitcoin.transaction.signer.SignatureContext;
import com.softwareverde.bitcoin.transaction.signer.TransactionSigner;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PrivateKey;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockchainDatabaseManagerTests extends IntegrationTest {

    protected void assertBlockSegmentBlockCount(final BlockchainSegmentId blockchainSegmentId, final Integer expectedValue, final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT COUNT(*) AS count FROM blocks WHERE blockchain_segment_id = ?")
                .setParameter(blockchainSegmentId)
        );
        final Row row = rows.get(0);
        final Integer blockCount = row.getInteger("count");

        Assert.assertEquals(expectedValue, blockCount);
    }

    protected void assertBlockSegmentBlockHeight(final BlockchainSegmentId blockchainSegmentId, final Integer expectedValue, final DatabaseConnection databaseConnection) throws DatabaseException {
        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT MAX(block_height) AS block_height FROM blocks WHERE blockchain_segment_id = ?")
                .setParameter(blockchainSegmentId)
        );
        final Row row = rows.get(0);
        final Integer blockHeight = row.getInteger("block_height");

        Assert.assertEquals(expectedValue, blockHeight);
    }

    @Override @Before
    public void before() throws Exception {
        super.before();

        _nonce = 0L;
    }

    @Override @After
    public void after() throws Exception {
        super.after();
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

            assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
            assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);

            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
        }
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

            assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);

            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
        }
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
        }
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #2 (refactoredBlockchain)
                final Row row = rows.get(1);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #3 (newBlockchain)
                final Row row = rows.get(2);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
            Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
            Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
        }
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #2 (refactoredBlockchain)
                final Row row = rows.get(1);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
            }

            { // Chain #3 (newBlockchain)
                final Row row = rows.get(2);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            Assert.assertEquals(1L, blockHeaderDatabaseManager.getBlockchainSegmentId(genesisBlockId).longValue());
            Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1Id).longValue());
            Assert.assertEquals(2L, blockHeaderDatabaseManager.getBlockchainSegmentId(block2Id).longValue());
            Assert.assertEquals(3L, blockHeaderDatabaseManager.getBlockchainSegmentId(block1PrimeId).longValue());
        }
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #2 (refactoredBlockchain)
                final Row row = rows.get(1);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
            }

            { // Chain #3 (newBlockchain)
                final Row row = rows.get(2);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
            }

            { // Chain #4 (newestBlockchain)
                final Row row = rows.get(3);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #2 (refactoredBlockchain)
                final Row row = rows.get(1);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
            }

            { // Chain #3 (newBlockchain)
                final Row row = rows.get(2);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
            }

            { // Chain #4 (newestBlockchain)
                final Row row = rows.get(3);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
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

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

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

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 0, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #2 (refactoredBlockchain)
                final Row row = rows.get(1);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 4, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
            }

            { // Chain #3 (originalForkedChain)
                final Row row = rows.get(2);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 3, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 2, databaseConnection);
            }

            { // Chain #4 (refactoredBaseChain)
                final Row row = rows.get(3);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
            }

            { // Chain #5 (mostRecentlyForkedChain)
                final Row row = rows.get(4);

                assertBlockSegmentBlockHeight(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
                assertBlockSegmentBlockCount(BlockchainSegmentId.wrap(row.getLong("id")), 1, databaseConnection);
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
    }

    protected Long _nonce = 0L;
    protected Sha256Hash _insertBlock(final Sha256Hash parentBlockHash) throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            final MutableBlockHeader mutableBlockHeader = new MutableBlockHeader(genesisBlock);
            mutableBlockHeader.setNonce(_nonce++);
            mutableBlockHeader.setPreviousBlockHash(parentBlockHash);

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.insertBlockHeader(mutableBlockHeader);
            }

            return mutableBlockHeader.getHash();
        }
    }

    protected void _assertBlockchainSegments(final Long expectedBlockchainSegmentId, final Sha256Hash[] blockHashes) throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            for (final Sha256Hash blockHash : blockHashes) {
                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                Assert.assertEquals(BlockchainSegmentId.wrap(expectedBlockchainSegmentId), blockchainSegmentId);
            }
        }
    }

    protected void _assertIsParent(final Sha256Hash parentHash, final Sha256Hash childHash) throws Exception {
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

            final BlockId childId = blockHeaderDatabaseManager.getBlockHeaderId(childHash);
            final BlockId parentId = blockHeaderDatabaseManager.getBlockHeaderId(parentHash);
            final BlockchainSegmentId childSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(childId);
            final BlockchainSegmentId parentSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(parentId);

            final Long childBlockHeight = blockHeaderDatabaseManager.getBlockHeight(childId);
            final Long parentBlockHeight = blockHeaderDatabaseManager.getBlockHeight(parentId);

            Assert.assertTrue(parentBlockHeight < childBlockHeight);
            Assert.assertTrue(blockchainDatabaseManager.areBlockchainSegmentsConnected(parentSegmentId, childSegmentId, BlockRelationship.ANCESTOR));
        }
    }

    @Test
    public void should_handle_chain_restructure() throws Exception {
        /*


                         [4]       [4']                          [4]       [4']
                          |         |                             |         |
                          +----+----+                             +----+----+
                               |                                       |
                  [3']        [3]                         [3']        [3]
                   |           |                           |           |
                   +----[2]----+                           +----[2]----+    [2']
                         |                                       |           |
                        [1]                                      +----[1]----+
                         |                                             |
                        [0]                                           [0]
                         |                                             |
                                             ->

         */

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockInflater blockInflater = new BlockInflater();

            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.insertBlock(genesisBlock);
            }

            final Sha256Hash blockHash0 = genesisBlock.getHash();
            final Sha256Hash blockHash1 = _insertBlock(blockHash0);
            final Sha256Hash blockHash2 = _insertBlock(blockHash1);
            final Sha256Hash blockHash3 = _insertBlock(blockHash2);
            final Sha256Hash blockHash4 = _insertBlock(blockHash3);

            _assertBlockchainSegments(1L, new Sha256Hash[] { blockHash0, blockHash1, blockHash2, blockHash3, blockHash4 });

            final Sha256Hash blockHash3p = _insertBlock(blockHash2);

            _assertIsParent(blockHash0, blockHash1);
            _assertIsParent(blockHash1, blockHash2);
            _assertIsParent(blockHash2, blockHash3);
            _assertIsParent(blockHash3, blockHash4);
            _assertIsParent(blockHash2, blockHash3p);

            final Sha256Hash blockHash4p = _insertBlock(blockHash3);

            _assertIsParent(blockHash0, blockHash1);
            _assertIsParent(blockHash1, blockHash2);
            _assertIsParent(blockHash2, blockHash3);
            _assertIsParent(blockHash2, blockHash3p);
            _assertIsParent(blockHash3, blockHash4);
            _assertIsParent(blockHash3, blockHash4p);

            final Sha256Hash blockHash2p = _insertBlock(blockHash1);

            _assertIsParent(blockHash0, blockHash1);
            _assertIsParent(blockHash1, blockHash2p);
            _assertIsParent(blockHash2, blockHash3);
            _assertIsParent(blockHash2, blockHash3p);
            _assertIsParent(blockHash3, blockHash4);
            _assertIsParent(blockHash3, blockHash4p);
        }
    }

    @Test
    public void should_handle_chain_restructure2() throws Exception {
        /*





                                                      [7']  [7]
                                                       |     |
                   [6]                                [6']  [6]       [6'']
                    |                                  |     |         |
                   [5]       [5']                      +----[5]       [5']
                    |         |                              |         |
                   [4]       [4']                           [4]       [4']
                    |         |                              |         |
                    +----+----+                              +----+----+
                         |                                        |
                  [3']  [3]                                [3']  [3]
                   |     |                                  |     |
                   +----[2]   [2']                          +----[2]   [2']
                         |     |                                  |     |
                        [1]----+                                 [1]----+
                         |                                        |
                        [0]                                      [0]
                         |                                        |
                                             ->

         */

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockInflater blockInflater = new BlockInflater();

            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockDatabaseManager.insertBlock(genesisBlock);
            }

            final Sha256Hash blockHash0 = genesisBlock.getHash();
            final Sha256Hash blockHash1 = _insertBlock(blockHash0);
            final Sha256Hash blockHash2 = _insertBlock(blockHash1);
            final Sha256Hash blockHash3 = _insertBlock(blockHash2);
            final Sha256Hash blockHash4 = _insertBlock(blockHash3);
            final Sha256Hash blockHash5 = _insertBlock(blockHash4);

            final Sha256Hash blockHash3p = _insertBlock(blockHash2);
            final Sha256Hash blockHash4p = _insertBlock(blockHash3);
            final Sha256Hash blockHash5p = _insertBlock(blockHash4p);
            final Sha256Hash blockHash2p = _insertBlock(blockHash1);

            final Sha256Hash blockHash6 = _insertBlock(blockHash5);

            _assertIsParent(blockHash0, blockHash1);
            _assertIsParent(blockHash1, blockHash2);
            _assertIsParent(blockHash2, blockHash3);
            _assertIsParent(blockHash2, blockHash3p);
            _assertIsParent(blockHash3, blockHash4);
            _assertIsParent(blockHash3, blockHash4p);
            _assertIsParent(blockHash4, blockHash5);
            _assertIsParent(blockHash4p, blockHash5p);
            _assertIsParent(blockHash1, blockHash2p);

            final Sha256Hash blockHash7 = _insertBlock(blockHash6);
            final Sha256Hash blockHash6p = _insertBlock(blockHash5);
            final Sha256Hash blockHash7p = _insertBlock(blockHash6p);
            final Sha256Hash blockHash6pp = _insertBlock(blockHash5p);

            _assertIsParent(blockHash0, blockHash1);
            _assertIsParent(blockHash1, blockHash2);
            _assertIsParent(blockHash2, blockHash3);
            _assertIsParent(blockHash2, blockHash3p);
            _assertIsParent(blockHash3, blockHash4);
            _assertIsParent(blockHash3, blockHash4p);
            _assertIsParent(blockHash4, blockHash5);
            _assertIsParent(blockHash4p, blockHash5p);
            _assertIsParent(blockHash1, blockHash2p);
            _assertIsParent(blockHash5, blockHash6);
            _assertIsParent(blockHash6, blockHash7);
            _assertIsParent(blockHash5, blockHash6p);
            _assertIsParent(blockHash6p, blockHash7p);
            _assertIsParent(blockHash5p, blockHash6pp);

            // // NOTE: All BlockchainSegments start_height should be greater than its parents end_height...
            //        final java.util.List<Row> rows = databaseConnection.query(new Query("select blockchain_segment_id as id, COALESCE(parent_blockchain_segment_id, 0) as parent_id, count(*) as block_count, min(block_height) as start_height, max(block_height) as end_height from blocks inner join blockchain_segments on blockchain_segments.id = blocks.blockchain_segment_id group by blocks.blockchain_segment_id"));
            //        for (final Row row : rows) {
            //            for (final String key : row.getColumnNames()) {
            //                System.out.print(key + ": " + row.getString(key) + " ");
            //            }
            //            System.out.println();
            //        }
        }
    }

    @Test
    public void should_return_original_head_block_during_contention() throws Exception {
        final BlockInflater blockInflater = _masterInflater.getBlockInflater();

        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            // MainChain Blocks
            final Block genesisBlock = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.MainChain.GENESIS_BLOCK)); // 000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F

            // ForkChain2 Blocks
            final Block forkChain2Block01 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.ForkChain2.BLOCK_1)); // 0000000001BE52D653305F7D80ED373837E61CC26AE586AFD343A3C2E64E64A2
            final Block forkChain2Block02 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.ForkChain2.BLOCK_2)); // 00000000314E669144E0781C432EB33F2079834D406E46393291E94199F433EE
            final Block forkChain2Block03 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.ForkChain2.BLOCK_3)); // 00000000EC006D368F4610AAEA50986B4E71450C81E8A2E1D947A2BF93F0BCB7

            // ForkChain4 Blocks
            final Block forkChain4Block03 = blockInflater.fromBytes(ByteArray.fromHexString(BlockData.ForkChain4.BLOCK_3)); // 00000000C77EFC229BD4EF49BBC08C17AB26B7AC242C10B0105179EFA1A2D0D6

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                // Action/Assert
                blockDatabaseManager.storeBlock(genesisBlock);
                Assert.assertEquals(BlockchainSegmentId.wrap(1L), blockchainDatabaseManager.getHeadBlockchainSegmentId());
                Assert.assertEquals(BlockId.wrap(1L), blockHeaderDatabaseManager.getHeadBlockHeaderId());
                Assert.assertEquals(BlockId.wrap(1L), blockDatabaseManager.getHeadBlockId());

                blockDatabaseManager.storeBlock(forkChain2Block01);
                Assert.assertEquals(BlockchainSegmentId.wrap(1L), blockchainDatabaseManager.getHeadBlockchainSegmentId());
                Assert.assertEquals(BlockId.wrap(2L), blockHeaderDatabaseManager.getHeadBlockHeaderId());
                Assert.assertEquals(BlockId.wrap(2L), blockDatabaseManager.getHeadBlockId());

                blockDatabaseManager.storeBlock(forkChain2Block02);
                Assert.assertEquals(BlockchainSegmentId.wrap(1L), blockchainDatabaseManager.getHeadBlockchainSegmentId());
                Assert.assertEquals(BlockId.wrap(3L), blockHeaderDatabaseManager.getHeadBlockHeaderId());
                Assert.assertEquals(BlockId.wrap(3L), blockDatabaseManager.getHeadBlockId());

                final BlockId blockId = blockDatabaseManager.storeBlock(forkChain4Block03);
                Assert.assertEquals(BlockchainSegmentId.wrap(1L), blockchainDatabaseManager.getHeadBlockchainSegmentId());
                Assert.assertEquals(BlockId.wrap(4L), blockHeaderDatabaseManager.getHeadBlockHeaderId());
                Assert.assertEquals(BlockId.wrap(4L), blockDatabaseManager.getHeadBlockId());

                blockDatabaseManager.storeBlock(forkChain2Block03);
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                Assert.assertEquals(blockchainSegmentId, blockchainDatabaseManager.getHeadBlockchainSegmentId());
                Assert.assertEquals(blockId, blockHeaderDatabaseManager.getHeadBlockHeaderId());
                Assert.assertEquals(blockId, blockDatabaseManager.getHeadBlockId());
            }
        }
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
        final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

        final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));
        final Block customBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D539CC56649FFFF001D4A0CDDD801010000000100000000000000000000000000000000000000000000000000000000000000000000000020184D696E65642076696120426974636F696E2D56657264652E06313134353332FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));

        final int tenMinutesInSeconds = (60 * 10);

        final MutableBlock mutableBlock = new MutableBlock();
        mutableBlock.setPreviousBlockHash(block5.getHash());
        mutableBlock.setDifficulty(block5.getDifficulty());
        mutableBlock.setTimestamp(block5.getTimestamp() + (tenMinutesInSeconds));
        mutableBlock.setVersion(block5.getVersion());

        final PrivateKey privateKey = PrivateKey.fromHexString("9F40477DAB2F6822360E6C690F8278DB73E536156A402BBBE798A85DCBE1A8AC");
        final Address payToAddress = addressInflater.fromPrivateKey(privateKey, false);

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
            transaction.addTransactionOutput(_createTransactionOutput(addressInflater.fromPrivateKey(privateKey, false)));

            final SignatureContext signatureContext = new SignatureContext(transaction, new HashType(Mode.SIGNATURE_HASH_ALL, true, false), Long.MAX_VALUE, upgradeSchedule);
            signatureContext.setShouldSignInputScript(0, true, outputBeingSpent);
            final TransactionSigner transactionSigner = new TransactionSigner();
            final Transaction signedTransaction = transactionSigner.signTransaction(signatureContext, privateKey);

            final TransactionInput transactionInput = signedTransaction.getTransactionInputs().get(0);
            final MutableTransactionContext context = new MutableTransactionContext(upgradeSchedule);
            context.setCurrentScript(null);
            context.setTransactionInputIndex(0);
            context.setTransactionInput(transactionInput);
            context.setTransaction(signedTransaction);
            context.setBlockHeight(6L);
            context.setTransactionOutputBeingSpent(outputBeingSpent);
            context.setCurrentScriptLastCodeSeparatorIndex(0);
            final ScriptRunner scriptRunner = new ScriptRunner(upgradeSchedule);
            final Boolean outputIsUnlocked = scriptRunner.runScript(outputBeingSpent.getLockingScript(), transactionInput.getUnlockingScript(), context).isValid;
            Assert.assertTrue(outputIsUnlocked);

            mutableBlock.addTransaction(signedTransaction);
        }

        final BlockDeflater blockDeflater = new BlockDeflater();

        System.out.println(privateKey);
        System.out.println(payToAddress.toBase58CheckEncoded());

        final Miner miner = new Miner(4, 0, null);
        miner.setShouldMutateTimestamp(true);
        final Block minedBlock = miner.mineBlock(mutableBlock);
        System.out.println(minedBlock.getHash());
        System.out.println(blockDeflater.toBytes(minedBlock));
    }
}

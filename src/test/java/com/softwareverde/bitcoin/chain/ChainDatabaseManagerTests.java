package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ChainDatabaseManagerTests extends IntegrationTest {
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));

        // Action
        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(genesisBlockId, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(0L, row.getLong("block_height").longValue());
        Assert.assertEquals(1L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));

        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        // Action
        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block1Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(1L, row.getLong("block_height").longValue());
        Assert.assertEquals(2L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9BB0BC6649FFFF001D08D2BD610101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010BFFFFFFFF0100F2052A010000004341047211A824F55B505228E4C3D5194C1FCFAA15A456ABDF37F9B9D97A4040AFC073DEE6C89064984F03385237D92167C13E236446B417AB79A0FCAE412AE3316B77AC00000000"));

        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final Long block2Id = blockDatabaseManager.storeBlock(block2);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains"));
        Assert.assertEquals(1, rows.size());

        final Row row = rows.get(0);
        Assert.assertEquals(block2Id, row.getLong("head_block_id"));
        Assert.assertEquals(genesisBlockId, row.getLong("tail_block_id"));
        Assert.assertEquals(2L, row.getLong("block_height").longValue());
        Assert.assertEquals(3L, row.getLong("block_count").longValue());

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(block2Id).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0EFAA8975AFFFF001DB0E02D5E00"));

        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());

        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        // Action
        final Long block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains ORDER BY id ASC"));
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

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block1PrimeId).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9BB0BC6649FFFF001D08D2BD610101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010BFFFFFFFF0100F2052A010000004341047211A824F55B505228E4C3D5194C1FCFAA15A456ABDF37F9B9D97A4040AFC073DEE6C89064984F03385237D92167C13E236446B417AB79A0FCAE412AE3316B77AC00000000"));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0EFAA8975AFFFF001DB0E02D5E00"));

        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());

        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final Long block2Id = blockDatabaseManager.storeBlock(block2);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2);

        // Action
        final Long block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block1PrimeId.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains ORDER BY id ASC"));
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

        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block2Id).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block1PrimeId).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9BB0BC6649FFFF001D08D2BD610101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010BFFFFFFFF0100F2052A010000004341047211A824F55B505228E4C3D5194C1FCFAA15A456ABDF37F9B9D97A4040AFC073DEE6C89064984F03385237D92167C13E236446B417AB79A0FCAE412AE3316B77AC00000000"));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("01000000BDDD99CCFDA39DA1B108CE1A5D70038D0A967BACB68B6B63065F626A0000000044F672226090D85DB9A9F2FBFE5F0F9609B387AF7BE5B7FBB7A1767C831C9E995DBE6649FFFF001D05E0ED6D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010EFFFFFFFF0100F2052A0100000043410494B9D3E76C5B1629ECF97FFF95D7A4BBDAC87CC26099ADA28066C6FF1EB9191223CD897194A08D0C2726C5747F1DB49E8CF90E75DC3E3550AE9B30086F3CD5AAAC00000000"));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF942E29C5AFFFF001DD2A4166100"));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000008F228834D9A4CB753E4D0502CFD66FAAF46492E7D9218BB6952C80FF00000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF98BE49C5AFFFF001DA1C07C1800"));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E7E25985AFFFF001D0D59E03500"));

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());

        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final Long block2Id = blockDatabaseManager.storeBlock(block2);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final Long block3Id = blockDatabaseManager.storeBlock(block3);
        chainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final Long block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final Long block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        // Action
        final Long block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block1PrimeId.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block1DoublePrimeId.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains ORDER BY id ASC"));
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
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainIdForBlockId(block1DoublePrimeId).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9BB0BC6649FFFF001D08D2BD610101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010BFFFFFFFF0100F2052A010000004341047211A824F55B505228E4C3D5194C1FCFAA15A456ABDF37F9B9D97A4040AFC073DEE6C89064984F03385237D92167C13E236446B417AB79A0FCAE412AE3316B77AC00000000"));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("01000000BDDD99CCFDA39DA1B108CE1A5D70038D0A967BACB68B6B63065F626A0000000044F672226090D85DB9A9F2FBFE5F0F9609B387AF7BE5B7FBB7A1767C831C9E995DBE6649FFFF001D05E0ED6D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010EFFFFFFFF0100F2052A0100000043410494B9D3E76C5B1629ECF97FFF95D7A4BBDAC87CC26099ADA28066C6FF1EB9191223CD897194A08D0C2726C5747F1DB49E8CF90E75DC3E3550AE9B30086F3CD5AAAC00000000"));
        final Block block1Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF942E29C5AFFFF001DD2A4166100"));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000008F228834D9A4CB753E4D0502CFD66FAAF46492E7D9218BB6952C80FF00000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF98BE49C5AFFFF001DA1C07C1800"));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E7E25985AFFFF001D0D59E03500"));

        // Chain 2
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());

        // Chain 3
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1Prime.getPreviousBlockHash());
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());

        // Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());

        // Action
        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final Long block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);

        final Long block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final Long block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        final Long block2Id = blockDatabaseManager.storeBlock(block2);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final Long block3Id = blockDatabaseManager.storeBlock(block3);
        chainDatabaseManager.updateBlockChainsForNewBlock(block3);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block1PrimeId.longValue());
        Assert.assertEquals(4L, block2PrimeId.longValue());
        Assert.assertEquals(5L, block1DoublePrimeId.longValue());
        Assert.assertEquals(6L, block2Id.longValue());
        Assert.assertEquals(7L, block3Id.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains ORDER BY id ASC"));
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
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block3Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block1PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block2PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainIdForBlockId(block1DoublePrimeId).longValue());
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
        final ChainDatabaseManager chainDatabaseManager = new ChainDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("0100000000000000000000000000000000000000000000000000000000000000000000003BA3EDFD7A7B12B27AC72C3E67768F617FC81BC3888A51323A9FB8AA4B1E5E4A29AB5F49FFFF001D1DAC2B7C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF4D04FFFF001D0104455468652054696D65732030332F4A616E2F32303039204368616E63656C6C6F72206F6E206272696E6B206F66207365636F6E64206261696C6F757420666F722062616E6B73FFFFFFFF0100F2052A01000000434104678AFDB0FE5548271967F1A67130B7105CD6A828E03909A67962E0EA1F61DEB649F6BC3F4CEF38C4F35504E51EC112DE5C384DF7BA0B8D578A4C702B6BF11D5FAC00000000"));
        final Block block1 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E61BC6649FFFF001D01E362990101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D0104FFFFFFFF0100F2052A0100000043410496B538E853519C726A2C91E61EC11600AE1390813A627C66FB8BE7947BE63C52DA7589379515D4E0A604F8141781E62294721166BF621E73A82CBF2342C858EEAC00000000"));
        final Block block2 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000D5FDCC541E25DE1C7A5ADDEDF24858B8BB665C9F36EF744EE42C316022C90F9BB0BC6649FFFF001D08D2BD610101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010BFFFFFFFF0100F2052A010000004341047211A824F55B505228E4C3D5194C1FCFAA15A456ABDF37F9B9D97A4040AFC073DEE6C89064984F03385237D92167C13E236446B417AB79A0FCAE412AE3316B77AC00000000"));
        final Block block3 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("01000000BDDD99CCFDA39DA1B108CE1A5D70038D0A967BACB68B6B63065F626A0000000044F672226090D85DB9A9F2FBFE5F0F9609B387AF7BE5B7FBB7A1767C831C9E995DBE6649FFFF001D05E0ED6D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D010EFFFFFFFF0100F2052A0100000043410494B9D3E76C5B1629ECF97FFF95D7A4BBDAC87CC26099ADA28066C6FF1EB9191223CD897194A08D0C2726C5747F1DB49E8CF90E75DC3E3550AE9B30086F3CD5AAAC00000000"));
        final Block block4 = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004944469562AE1C2C74D9A535E00B6F3E40FFBAD4F2FDA3895501B582000000007A06EA98CD40BA2E3288262B28638CEC5337C1456AAF5EEDC8E9E5A20F062BDF8CC16649FFFF001D2BFEE0A90101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0704FFFF001D011AFFFFFFFF0100F2052A01000000434104184F32B212815C6E522E66686324030FF7E5BF08EFB21F8B00614FB7690E19131DD31304C54F37BAA40DB231C918106BB9FD43373E37AE31A0BEFC6ECAEFB867AC00000000"));
        final Block block2Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF9E5E59C5AFFFF001DA4A938F900")); // Another Option: 010000004860EB18BF1B1620E37E9490FC8A427514416FD75159AB86688E9A8300000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF93BE69C5AFFFF001DFBD9E14300
        final Block block3Prime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000005206D15DEBBBA129DA8F902437753E9718B5532B85028E034488FD9800000000E2F61C3F71D1DEFD3FA999DFA36953755C690689799962B48BEBD836974E8CF9B3E99C5AFFFF001D8C284AA500"));
        final Block block1DoublePrime = blockInflater.fromBytes(BitcoinUtil.hexStringToByteArray("010000006FE28C0AB6F1B372C1A6A246AE63F74F931E8365E15A089C68D6190000000000982051FD1E4BA744BBBE680E1FEE14677BA1A3C3540BF7B1CDB606E857233E0E7E25985AFFFF001D0D59E03500"));

        // Original Chain 1
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1.getPreviousBlockHash());

        // Original Chain 2
        Assert.assertEquals(block1.getHash(), block2.getPreviousBlockHash());
        Assert.assertEquals(block2.getHash(), block3.getPreviousBlockHash());
        Assert.assertEquals(block3.getHash(), block4.getPreviousBlockHash());

        // Original Chain 3
        Assert.assertEquals(block1.getHash(), block2Prime.getPreviousBlockHash());
        Assert.assertEquals(block2Prime.getHash(), block3Prime.getPreviousBlockHash());

        // New Chain 4
        Assert.assertEquals(Block.GENESIS_BLOCK_HEADER_HASH, block1DoublePrime.getPreviousBlockHash());

        // Establish Original Blocks/Chains...
        final Long genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        chainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Long block1Id = blockDatabaseManager.storeBlock(block1);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1);

        final Long block2Id = blockDatabaseManager.storeBlock(block2);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2);

        final Long block3Id = blockDatabaseManager.storeBlock(block3);
        chainDatabaseManager.updateBlockChainsForNewBlock(block3);

        final Long block4Id = blockDatabaseManager.storeBlock(block4);
        chainDatabaseManager.updateBlockChainsForNewBlock(block4);

        final Long block2PrimeId = blockDatabaseManager.storeBlock(block2Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);

        final Long block3PrimeId = blockDatabaseManager.storeBlock(block3Prime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block3Prime);

        // Action
        final Long block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
        chainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);

        // Assert
        Assert.assertEquals(1L, genesisBlockId.longValue());
        Assert.assertEquals(2L, block1Id.longValue());
        Assert.assertEquals(3L, block2Id.longValue());
        Assert.assertEquals(4L, block3Id.longValue());
        Assert.assertEquals(5L, block4Id.longValue());
        Assert.assertEquals(6L, block2PrimeId.longValue());
        Assert.assertEquals(7L, block3PrimeId.longValue());
        Assert.assertEquals(8L, block1DoublePrimeId.longValue());

        final List<Row> rows = databaseConnection.query(new Query("SELECT * FROM block_chains ORDER BY id ASC"));
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
        Assert.assertEquals(1L, blockDatabaseManager.getBlockChainIdForBlockId(genesisBlockId).longValue());

        // Chain 2
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block2Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block3Id).longValue());
        Assert.assertEquals(2L, blockDatabaseManager.getBlockChainIdForBlockId(block4Id).longValue());

        // Chain 3
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block2PrimeId).longValue());
        Assert.assertEquals(3L, blockDatabaseManager.getBlockChainIdForBlockId(block3PrimeId).longValue());

        // Chain 4
        Assert.assertEquals(4L, blockDatabaseManager.getBlockChainIdForBlockId(block1Id).longValue());

        // Chain 5
        Assert.assertEquals(5L, blockDatabaseManager.getBlockChainIdForBlockId(block1DoublePrimeId).longValue());
    }
}

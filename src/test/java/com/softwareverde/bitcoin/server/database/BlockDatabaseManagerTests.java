package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.MutableBlock;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.bitcoin.test.TransactionTestUtil;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.IoUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockDatabaseManagerTests extends IntegrationTest {

    public static class ScenarioData {
        public final BlockId A;
        public final BlockId B;
        public final BlockId C;
        public final BlockId D;
        public final BlockId E;
        public final BlockId C2;
        public final BlockId E2;

        public ScenarioData(final BlockId[] blocks) {
            this.A  = blocks[0];
            this.B  = blocks[1];
            this.C  = blocks[2];
            this.C2 = blocks[3];
            this.D  = blocks[4];
            this.E  = blocks[5];
            this.E2 = blocks[6];
        }
    }

    /**
     * Creates the following scenario...
     *
     *     E         E'             Height: 4
     *     |         |
     *  #4 +----D----+ #5           Height: 3
     *          |
     *          C         C''       Height: 2
     *          |         |
     *       #2 +----B----+ #3      Height: 1
     *               |
     *               A #1           Height: 0
     *
     */
    protected ScenarioData _setupScenario(final MysqlDatabaseConnection databaseConnection) throws Exception {
        final BlockInflater blockInflater = new BlockInflater();

        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final Block block_A = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block_B = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block_C = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block_D = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block_E = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
        final Block block_C2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));

        final Block block_E2; // NOTE: Has an invalid hash, but shouldn't matter...
        {
            final MutableBlock mutableBlock = new MutableBlock(block_E);
            mutableBlock.setNonce(mutableBlock.getNonce() + 1);
            block_E2 = mutableBlock;
        }

        { //  Sanity check for the appropriate chain structure...
            Assert.assertEquals(block_A.getHash(), block_B.getPreviousBlockHash());
            Assert.assertEquals(block_B.getHash(), block_C.getPreviousBlockHash());
            Assert.assertEquals(block_C.getHash(), block_D.getPreviousBlockHash());
            Assert.assertEquals(block_D.getHash(), block_E.getPreviousBlockHash());

            Assert.assertEquals(block_B.getHash(), block_C2.getPreviousBlockHash());
            Assert.assertEquals(block_D.getHash(), block_E2.getPreviousBlockHash());
        }

        final BlockId[] blockIds = new BlockId[7];

        { // Store blocks...
            int i = 0;
            final Block[] blocks = new Block[]{ block_A, block_B, block_C, block_C2, block_D, block_E, block_E2 };
            for (final Block block : blocks) {
                blockIds[i] = blockDatabaseManager.insertBlock(block);
                i += 1;
            }
        }

        { // Sanity check for the expected chainSegmentIds...
            Assert.assertEquals(1, blockDatabaseManager.getBlockChainSegmentId(blockIds[0]).longValue()); // Block A
            Assert.assertEquals(1, blockDatabaseManager.getBlockChainSegmentId(blockIds[1]).longValue()); // Block B
            Assert.assertEquals(2, blockDatabaseManager.getBlockChainSegmentId(blockIds[2]).longValue()); // Block C
            Assert.assertEquals(2, blockDatabaseManager.getBlockChainSegmentId(blockIds[4]).longValue()); // Block D
            Assert.assertEquals(3, blockDatabaseManager.getBlockChainSegmentId(blockIds[3]).longValue()); // Block C''
            Assert.assertEquals(4, blockDatabaseManager.getBlockChainSegmentId(blockIds[5]).longValue()); // Block E
            Assert.assertEquals(5, blockDatabaseManager.getBlockChainSegmentId(blockIds[6]).longValue()); // Block E'
        }

        return new ScenarioData(blockIds);
    }

    @Before
    public void setup() {
        _resetDatabase();
        _resetCache();
    }

    @Test
    public void should_inflate_stored_transaction() throws Exception {
        try (final MysqlDatabaseConnection databaseConnection = _database.newConnection()) {

            // Setup
            final BlockInflater blockInflater = new BlockInflater();
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            { // Store blocks that contain this blocks spent outputs...
                // Block Hash: 00000000689051C09FF2CD091CC4C22C10B965EB8DB3AD5F032621CC36626175
                final Block prerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000F5790162A682DDD5086265D254F7F59023D35D07DF7C95DC9779942D00000000193028D8B78007269D52B2A1068E32EDD21D0772C2C157954F7174761B78A51A30CE6E49FFFF001D3A2E34480201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027C05FFFFFFFF0100F2052A01000000434104B43BB206B71F34E2FAB9359B156FF683BED889021A06C315722A7C936B9743AD88A8882DC13EECAFCDAD4F082D2D0CC54AA177204F79DC7305F1F4857B7B8802AC00000000010000000177B5E6E78F8552129D07A73801B1A5F6830EC040D218755B46340B4CF6D21FD7000000004A49304602210083EC8BD391269F00F3D714A54F4DBD6B8004B3E9C91F3078FF4FCA42DA456F4D0221008DFE1450870A717F59A494B77B36B7884381233555F8439DAC4EA969977DD3F401FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F00000000434104F36C67039006EC4ED2C885D7AB0763FEB5DEB9633CF63841474712E4CF0459356750185FC9D962D0F4A1E08E1A84F0C9A9F826AD067675403C19D752530492DCAC00000000"));
                for (final Transaction transaction : prerequisiteBlock.getTransactions()) {
                    TransactionTestUtil.createRequiredTransactionInputs(null, transaction, databaseConnection);
                }
                blockDatabaseManager.insertBlock(prerequisiteBlock);
            }

            final BlockId blockId;
            { // Store the block and its transactions...
                // Block Hash: 000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("0100000075616236CC2126035FADB38DEB65B9102CC2C41C09CDF29FC051906800000000FE7D5E12EF0FF901F6050211249919B1C0653771832B3A80C66CEA42847F0AE1D4D26E49FFFF001D00F0A4410401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D029105FFFFFFFF0100F2052A010000004341046D8709A041D34357697DFCB30A9D05900A6294078012BF3BB09C6F9B525F1D16D5503D7905DB1ADA9501446EA00728668FC5719AA80BE2FDFC8A858A4DBDD4FBAC00000000010000000255605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D28350000000049483045022100AA46504BAA86DF8A33B1192B1B9367B4D729DC41E389F2C04F3E5C7F0559AAE702205E82253A54BF5C4F65B7428551554B2045167D6D206DFE6A2E198127D3F7DF1501FFFFFFFF55605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D2835010000004847304402202329484C35FA9D6BB32A55A70C0982F606CE0E3634B69006138683BCD12CBB6602200C28FEB1E2555C3210F1DDDB299738B4FF8BBE9667B68CB8764B5AC17B7ADF0001FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC0000000001000000025F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028000000004847304402205D6058484157235B06028C30736C15613A28BDB768EE628094CA8B0030D4D6EB0220328789C9A2EC27DDAEC0AD5EF58EFDED42E6EA17C2E1CE838F3D6913F5E95DB601FFFFFFFF5F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028010000004A493046022100C45AF050D3CEA806CEDD0AB22520C53EBE63B987B8954146CDCA42487B84BDD6022100B9B027716A6B59E640DA50A864D6DD8A0EF24C76CE62391FA3EABAF4D2886D2D01FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC000000000100000002E2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B0000000048473044022016E7A727A061EA2254A6C358376AAA617AC537EB836C77D646EBDA4C748AAC8B0220192CE28BF9F2C06A6467E6531E27648D2B3E2E2BAE85159C9242939840295BA501FFFFFFFFE2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B010000004A493046022100B7A1A755588D4190118936E15CD217D133B0E4A53C3C15924010D5648D8925C9022100AAEF031874DB2114F2D869AC2DE4AE53908FBFEA5B2B1862E181626BB9005C9F01FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00000000"));
                blockId = blockDatabaseManager.insertBlock(block);
            }

            // Action
            final Block inflatedBlock = blockDatabaseManager.getBlock(blockId);

            // Assert
            Assert.assertTrue(inflatedBlock.isValid());
            Assert.assertEquals("000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6", inflatedBlock.getHash().toString());
        }
    }

    @Test
    public void should_detect_connected_block_when_parent() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.C;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(4L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANCESTOR);

        // Assert
        Assert.assertTrue(isBlockConnected);
    }

    @Test
    public void should_detect_not_connected_block() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.C2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(4L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);

        // Assert
        Assert.assertFalse(isBlockConnected);
    }

    @Test
    public void should_detect_connected_block_for_child() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.E;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(2L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.DESCENDANT);

        // Assert
        Assert.assertTrue(isBlockConnected);
    }

    @Test
    public void should_detect_connected_block_for_child_2() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.E;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(1L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.DESCENDANT);

        // Assert
        Assert.assertTrue(isBlockConnected);
    }

    @Test
    public void should_detect_connected_block_for_child_3() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.E2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(1L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.DESCENDANT);

        // Assert
        Assert.assertTrue(isBlockConnected);
    }

    @Test
    public void should_detect_connected_block_for_child_4() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.C2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(1L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.DESCENDANT);

        // Assert
        Assert.assertTrue(isBlockConnected);
    }

    @Test
    public void should_detect_not_connected_block_2() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.C2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(5L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);

        // Assert
        Assert.assertFalse(isBlockConnected);
    }

    @Test
    public void should_detect_not_connected_block_3() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.E2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(3L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);

        // Assert
        Assert.assertFalse(isBlockConnected);
    }

    @Test
    public void should_detect_not_connected_block_4() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.E;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(3L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);

        // Assert
        Assert.assertFalse(isBlockConnected);
    }

    @Test
    public void should_detect_not_connected_block_5() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final ScenarioData scenarioData = _setupScenario(databaseConnection);

        final BlockId blockId = scenarioData.C2;
        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(4L);

        // Action
        final Boolean isBlockConnected = blockDatabaseManager.isBlockConnectedToChain(blockId, blockChainSegmentId, BlockRelationship.ANY);

        // Assert
        Assert.assertFalse(isBlockConnected);
    }

    @Test
    public void should_inflate_block_00000000B0C5A240B2A61D2E75692224EFD4CBECDF6EAF4CC2CF477CA7C270E7() throws Exception {
        // Setup
        final BlockInflater blockInflater = new BlockInflater();
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        { // Store genesis block...
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            blockDatabaseManager.insertBlock(block);
        }

        final BlockChainSegmentId blockChainSegmentId = BlockChainSegmentId.wrap(1L);

        final String blockData = IoUtil.getResource("/blocks/00000000B0C5A240B2A61D2E75692224EFD4CBECDF6EAF4CC2CF477CA7C270E7");
        final byte[] blockBytes = HexUtil.hexStringToByteArray(blockData);
        final Block block = blockInflater.fromBytes(blockBytes);
        for (final Transaction transaction : block.getTransactions()) {
            TransactionTestUtil.createRequiredTransactionInputs(blockChainSegmentId, transaction, databaseConnection);
        }

        // Hack the genesis block so that its hash looks like the tested-block's previousBlockHash...
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                .setParameter(block.getPreviousBlockHash())
                .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
        );

        final BlockId blockId = blockDatabaseManager.insertBlock(block);

        // Action
        final Block inflatedBlock = blockDatabaseManager.getBlock(blockId);
        Logger.log(inflatedBlock.getTransactions().get(1).getHash());

        // Assert
        Assert.assertEquals("00000000B0C5A240B2A61D2E75692224EFD4CBECDF6EAF4CC2CF477CA7C270E7", inflatedBlock.getHash().toString());
    }

    @Test
    public void should_store_block_if_input_exist_on_prior_block_chain_segment_after_fork() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));
        final Block block4 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_4));
        final Block block5 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_5));

        final Block customBlock6 = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000FC33F596F822A0A1951FFDBF2A897B095636AD871707BF5D3162729B00000000E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D539CC56649FFFF001D4A0CDDD801010000000100000000000000000000000000000000000000000000000000000000000000000000000020184D696E65642076696120426974636F696E2D56657264652E06313134353332FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));
        final Block fakeBlock7;
        {
            final MutableBlock mutableBlock = new MutableBlock(customBlock6);
            mutableBlock.setPreviousBlockHash(customBlock6.getHash());
            mutableBlock.setTimestamp(mutableBlock.getTimestamp() + 600);
            mutableBlock.clearTransactions();
            mutableBlock.addTransaction(block5.getCoinbaseTransaction());
            fakeBlock7 = mutableBlock;
        }

        // This block is valid to store (although the proof of work is invalid). It contains a transaction that spends
        //  an input that is a part of the same chain, but a different blockChainSegment...
        final Block fakeBlock7Prime;
        {
            final TransactionInflater transactionInflater = new TransactionInflater();
            final Transaction transactionSpendingBlock6Coinbase = transactionInflater.fromBytes(HexUtil.hexStringToByteArray("0100000001E04DAA8565BEFFCEF1949AC5582B7DF359A10A2138409503A1B8B8D3C7355D53000000008A47304402205AF3B0D44912D11484ACAFE9409B76D1682A6BDF86EAC17056B8AD6F9FEDF47502205D844BFC1F93F9C5B4BD7D45C426CE56B6BA7E4EFAB5433189ACBAF5403C61F70141046C1D8C923D8ADFCEA711BE28A9BF7E2981632AAC789AEF95D7402B9225784AD93661700AB5474EFFDD7D5BEA6100904D3F1B3BE2017E2A18971DD8904B522020FFFFFFFF0100F2052A010000001976A914F1A626E143DCC5E75E8E6BE3F2CE1CF3108FB53D88AC00000000"));

            final MutableBlock mutableBlock = new MutableBlock(fakeBlock7);
            mutableBlock.setPreviousBlockHash(customBlock6.getHash());
            mutableBlock.setTimestamp(mutableBlock.getTimestamp() + 600);
            mutableBlock.clearTransactions();
            mutableBlock.addTransaction(block5.getCoinbaseTransaction());
            mutableBlock.addTransaction(transactionSpendingBlock6Coinbase);
            fakeBlock7Prime = mutableBlock;
        }

        for (final Block block : new Block[]{ genesisBlock, block1, block2, block3, block4, block5, customBlock6, fakeBlock7 }) {
            blockDatabaseManager.storeBlock(block);
        }

        // Action
        final BlockId blockId = blockDatabaseManager.storeBlock(fakeBlock7Prime);

        // Assert
        Assert.assertNotNull(blockId);
    }

    @Test
    public void should_find_block_at_height_across_block_chain_segments() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final Block block1 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1));
        final Block block2 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_2));
        final Block block3 = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_3));

        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_2));
        final Block block3Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain3.BLOCK_3));

        for (final Block block : new Block[]{ genesisBlock, block1, block2, block3, block2Prime, block3Prime }) {
            blockDatabaseManager.storeBlock(block);
        }

        final BlockChainSegmentId blockChainSegmentId1 = BlockChainSegmentId.wrap(1L); // Blocks 0 and 1...
        final BlockChainSegmentId blockChainSegmentId2 = BlockChainSegmentId.wrap(2L); // Blocks 2 and 3...
        final BlockChainSegmentId blockChainSegmentId3 = BlockChainSegmentId.wrap(3L); // Blocks 2' and 3'...

        final BlockId expectedBlockId1 = blockDatabaseManager.getBlockIdFromHash(block3.getHash());
        final BlockId expectedBlockId2 = blockDatabaseManager.getBlockIdFromHash(block3Prime.getHash());

        // Action
        final BlockId blockId1 = blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId2, 3L);
        final BlockId blockId2 = blockDatabaseManager.getBlockIdAtHeight(blockChainSegmentId3, 3L);

        // Assert
        Assert.assertEquals(expectedBlockId1, blockId1);
        Assert.assertEquals(expectedBlockId2, blockId2);
    }
}

package com.softwareverde.bitcoin.block.validator;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BlockValidatorTests extends IntegrationTest {
    @Before
    public void setup() throws Exception {
        _resetDatabase();
    }

    @Test
    public void should_validate_block_that_contains_transaction_that_spends_its_own_outputs() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockValidator blockValidator = new BlockValidator(_database);

        { // Store the blocks and transactions included within the block-under-test so that it should appear valid...
            // Block Hash: 	00000000689051C09FF2CD091CC4C22C10B965EB8DB3AD5F032621CC36626175
            final Block prerequisiteBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000F5790162A682DDD5086265D254F7F59023D35D07DF7C95DC9779942D00000000193028D8B78007269D52B2A1068E32EDD21D0772C2C157954F7174761B78A51A30CE6E49FFFF001D3A2E34480201000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D027C05FFFFFFFF0100F2052A01000000434104B43BB206B71F34E2FAB9359B156FF683BED889021A06C315722A7C936B9743AD88A8882DC13EECAFCDAD4F082D2D0CC54AA177204F79DC7305F1F4857B7B8802AC00000000010000000177B5E6E78F8552129D07A73801B1A5F6830EC040D218755B46340B4CF6D21FD7000000004A49304602210083EC8BD391269F00F3D714A54F4DBD6B8004B3E9C91F3078FF4FCA42DA456F4D0221008DFE1450870A717F59A494B77B36B7884381233555F8439DAC4EA969977DD3F401FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F00000000434104F36C67039006EC4ED2C885D7AB0763FEB5DEB9633CF63841474712E4CF0459356750185FC9D962D0F4A1E08E1A84F0C9A9F826AD067675403C19D752530492DCAC00000000"));
            final BlockId prerequisiteBlockId = blockDatabaseManager.storeBlock(prerequisiteBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(prerequisiteBlock);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(prerequisiteBlockId);
            final Boolean prerequisiteBlockIsValid = blockValidator.validateBlock(blockChainSegmentId, prerequisiteBlock);
            Assert.assertFalse(prerequisiteBlockIsValid); // Should fail because its dependent transactions are not inserted.  Asserting for clarity and sanity.
        }

        // Block Hash: 000000005A4DED781E667E06CEEFAFB71410B511FE0D5ADC3E5A27ECBEC34AE6
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray("0100000075616236CC2126035FADB38DEB65B9102CC2C41C09CDF29FC051906800000000FE7D5E12EF0FF901F6050211249919B1C0653771832B3A80C66CEA42847F0AE1D4D26E49FFFF001D00F0A4410401000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0804FFFF001D029105FFFFFFFF0100F2052A010000004341046D8709A041D34357697DFCB30A9D05900A6294078012BF3BB09C6F9B525F1D16D5503D7905DB1ADA9501446EA00728668FC5719AA80BE2FDFC8A858A4DBDD4FBAC00000000010000000255605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D28350000000049483045022100AA46504BAA86DF8A33B1192B1B9367B4D729DC41E389F2C04F3E5C7F0559AAE702205E82253A54BF5C4F65B7428551554B2045167D6D206DFE6A2E198127D3F7DF1501FFFFFFFF55605DC6F5C3DC148B6DA58442B0B2CD422BE385EAB2EBEA4119EE9C268D2835010000004847304402202329484C35FA9D6BB32A55A70C0982F606CE0E3634B69006138683BCD12CBB6602200C28FEB1E2555C3210F1DDDB299738B4FF8BBE9667B68CB8764B5AC17B7ADF0001FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC0000000001000000025F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028000000004847304402205D6058484157235B06028C30736C15613A28BDB768EE628094CA8B0030D4D6EB0220328789C9A2EC27DDAEC0AD5EF58EFDED42E6EA17C2E1CE838F3D6913F5E95DB601FFFFFFFF5F9A06D3ACDCEB56BE1BFEAA3E8A25E62D182FA24FEFE899D1C17F1DAD4C2028010000004A493046022100C45AF050D3CEA806CEDD0AB22520C53EBE63B987B8954146CDCA42487B84BDD6022100B9B027716A6B59E640DA50A864D6DD8A0EF24C76CE62391FA3EABAF4D2886D2D01FFFFFFFF0200E1F505000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC000000000100000002E2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B0000000048473044022016E7A727A061EA2254A6C358376AAA617AC537EB836C77D646EBDA4C748AAC8B0220192CE28BF9F2C06A6467E6531E27648D2B3E2E2BAE85159C9242939840295BA501FFFFFFFFE2274E5FEA1BF29D963914BD301AA63B64DAAF8A3E88F119B5046CA5738A0F6B010000004A493046022100B7A1A755588D4190118936E15CD217D133B0E4A53C3C15924010D5648D8925C9022100AAEF031874DB2114F2D869AC2DE4AE53908FBFEA5B2B1862E181626BB9005C9F01FFFFFFFF0200E1F505000000004341044A656F065871A353F216CA26CEF8DDE2F03E8C16202D2E8AD769F02032CB86A5EB5E56842E92E19141D60A01928F8DD2C875A390F67C1F6C94CFC617C0EA45AFAC00180D8F000000004341046A0765B5865641CE08DD39690AADE26DFBF5511430CA428A3089261361CEF170E3929A68AEE3D8D4848B0C5111B0A37B82B86AD559FD2A745B44D8E8D9DFDC0CAC00000000"));
        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    @Test
    public void should_validate_transactions_that_spend_its_coinbase() throws Exception {
        // NOTE: Spending the coinbase transaction within 100 blocks of its mining is invalid.

        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockInflater blockInflater = new BlockInflater();
        final BlockValidator blockValidator = new BlockValidator(_database);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
        final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);

        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain4.BLOCK_1));
        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
        final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);

        // Assert
        Assert.assertTrue(blockIsValid);
    }

    @Test
    public void block_should_be_invalid_if_its_input_only_exists_withing_a_different_chain() throws Exception {
        // Setup

        /*
            // Given the following forking situation...

            [2']                    // [2'] attempts to spend {UTXO-A}, [2'] does NOT include {UTXO-A}.
             |
            [1']  [1'']             // [1''] includes {UTXO-A}, [1'] does NOT include {UTXO-A}.
             |     |
            [0]----+
             |

            //
        */

        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final BlockInflater blockInflater = new BlockInflater();
        final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

        final Block block1Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain2.BLOCK_1));
        Assert.assertEquals(genesisBlock.getHash(), block1Prime.getPreviousBlockHash());

        final Block block2Prime = blockInflater.fromBytes(HexUtil.hexStringToByteArray("01000000A0725312AADE7F12764C47F9A8B54C4924EE12940C697782AE31F52300000000A66A6AAC381ACE8514F527B6324F72F8ADFBC9B853350675E5309B18E2256E9BA165A45AFFFF001D580D16B802010000000100000000000000000000000000000000000000000000000000000000000000000000000019184D696E65642076696120426974636F696E2D56657264652EFFFFFFFF0100F2052A010000001976A914B8E012A1EC221C31F69AA2895129C02C90AAE2C588AC0000000001000000011D1E1A5147E3170CB7B8DF8B234794CC61B798144B532EEE9C3A62CF9F5A4EBF000000008B483045022100CD4556EFF98614498B736E7894446F82B92638B40512B95C3C64FA06682F68A702200C02B594C919046E6D1EC951745D51778575BDBDB46DCB9793AAC3D0714AAAD10141044B1F57CD308E8AE8AADA38AA8183A348E1F12C107898760A11324E1B0288C49DE1AB5E1DA16F649B7375046E165FD2CF143F08989F1092A7ED6FED183107B236FFFFFFFF0100F2052A010000001976A91476B5CB4E5D485BDD6B6DD7A2AAAEC6F01FFA7DD488AC00000000")); // Spends a transaction within block1DoublePrime...
        Assert.assertEquals(block1Prime.getHash(), block2Prime.getPreviousBlockHash());

        final Block block1DoublePrime = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.ForkChain4.BLOCK_1));
        Assert.assertEquals(genesisBlock.getHash(), block1DoublePrime.getPreviousBlockHash());

        final BlockValidator blockValidator = new BlockValidator(_database);

        {
            final BlockId genesisBlockId = blockDatabaseManager.storeBlock(genesisBlock);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(genesisBlock);
            final BlockChainSegmentId genesisBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(genesisBlockId);
            Assert.assertTrue(blockValidator.validateBlock(genesisBlockChainSegmentId, genesisBlock));
        }

        {
            final BlockId block1PrimeId = blockDatabaseManager.storeBlock(block1Prime);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block1Prime);
            final BlockChainSegmentId block1PrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block1PrimeId);
            Assert.assertTrue(blockValidator.validateBlock(block1PrimeBlockChainSegmentId, block1Prime));
        }

        {
            final BlockId block1DoublePrimeId = blockDatabaseManager.storeBlock(block1DoublePrime);
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block1DoublePrime);
            final BlockChainSegmentId block1DoublePrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block1DoublePrimeId);
            Assert.assertTrue(blockValidator.validateBlock(block1DoublePrimeBlockChainSegmentId, block1DoublePrime));
        }

        final BlockChainSegmentId block2PrimeBlockChainSegmentId;
        {
            final BlockId block2Id = blockDatabaseManager.storeBlock(block2Prime); // Should be an invalid block...
            blockChainDatabaseManager.updateBlockChainsForNewBlock(block2Prime);
            block2PrimeBlockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(block2Id);
        }

        // Action
        final Boolean block2PrimeIsValid = blockValidator.validateBlock(block2PrimeBlockChainSegmentId, block2Prime);

        // Assert
        Assert.assertFalse(block2PrimeIsValid);
    }
}

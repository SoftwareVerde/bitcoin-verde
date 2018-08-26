package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DifficultyCalculatorTests extends IntegrationTest {
    @Before
    public void setup() {
        _resetDatabase();
        _resetCache();
    }

    @Test
    public void should_return_default_difficulty_for_block_0() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection, null);

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);

        final BlockInflater blockInflater = new BlockInflater();
        final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        blockChainDatabaseManager.updateBlockChainsForNewBlock(block);

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, block);

        // Assert
        Assert.assertEquals(Difficulty.BASE_DIFFICULTY, difficulty);
    }

    @Test
    public void should_return_bitcoin_cash_adjusted_difficulty() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader block478565 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002061B45491714B05FB0F6E6673DD7AB13135F38E1D4D37CF0000000000000000009653314C1D73E4630BB485FB25CE7A2583CEC7C3CCFC27A6D24163BE1E9FB19530F4805935470118F17AD2C5"));
        final BlockHeader block478566 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020F01DA03CFF6555A9DF5C2E844225286701014CB339E84E000000000000000000CDF48B8E7AC6BF3A51D1878EE3FF7E6FD0022926DD69CC5CC8D9126E77C4DBA809F58059354701188114E836"));
        final BlockHeader block478567 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FBF11E24BB91021FF26C74CE0AB00E52CE88177CA9CEF7000000000000000000890CF1DC60EDBF0FD4FB667F28AC785849C031D8B24D5E5A0AF56EE2BD8A739BF51081593547011812F32A96"));
        final BlockHeader block478568 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020BAB02C6A78280FD5FB187CED4982D7116237B9AD727304000000000000000000FF5244613AD20FDC39B7EE6F4FBC7016432D2DBF45C2A950C59665B39C3954B5B525815935470118AA790D66"));
        final BlockHeader block478569 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FE52DE7C797671CE6985256F4E0040DC5A660A0C6922D8000000000000000000AEED520E7C1693DE5CFE7531E7D3E73DFF7858B09CB6E1EC29229A75C3DA2B92453E81593547011830A4314D"));
        final BlockHeader block478570 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000207FA5085533AE45DF25A045DC1BFAFEF59E7D022987076D0000000000000000002E4A4054E64C3F5810C23EC0144D9793AAB2D5A7D77D1660EEE24D3D55E8B715B543815935470118DA1C00E0"));
        final BlockHeader block478571 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000200B6620D77A15ECB46A120430E27F3E6306D86D628C9FE200000000000000000073152AF68778A98FD984A158AEB29D28E094E23C3A7DFF02260C345791E52498C3FB8159354701188D5ABED9"));
        final BlockHeader block478572 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DBC3EB18F4D57451085CAC98BC5EA228FCEEC617EF012401000000000000000022606E744A29F9D4A67FF1FCD2F0E31300DDBD145F8F1DB8A68270BFBDE77DD88FFF8159354701186AF175F8"));
        final BlockHeader block478573 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020774F568005A00BF4FFED76B52E3A7E5E5140A5371834A00000000000000000009F5DB27969FECC0EF71503279069B2DF981BA545592A7B425F353B5060E77F3E7E13825935470118DA70378E"));
        final BlockHeader block478574 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002044068FFD5ACE2999D5E326A56CA17DF7ECBB4C89C218A80000000000000000002F0D316B08350F5CD998C6A11762D10ADB9F951B5F79CE2A073F8187C05F561F1B1C8259354701184834C623"));
        final BlockHeader block478575 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000205841AEA6F06F4A0E3560EA076CF1DF217EBDE943A92C16010000000000000000CF8FC3BAD8DAD139A3DD6A30481D87E1F760122573168002CC9EF7A58FC53AD387848259354701188A3B54F7"));
        final BlockHeader block478576 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D22E87EAA7C68D9E8F947BF5DF3CABE9294050180BF4130000000000000000000EAE92D9B46D81A011A79726A802D4EB195A7AF8B70A09B0E115C391968C50D51C8A825935470118CD786D13"));
        // medianBlockTime.addBlock(blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FA4F8E791184C0CEE158961A0AC6F4299898F872F06A410100000000000000003EC6D34403E8B74BFE9711CE053468EFB269D87422B18DB202C3FA6CB7E503754598825902990118BE67E71E"))); // 478577

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FA4F8E791184C0CEE158961A0AC6F4299898F872F06A410100000000000000003EC6D34403E8B74BFE9711CE053468EFB269D87422B18DB202C3FA6CB7E503754598825902990118BE67E71E")); // 478577

        Assert.assertEquals(block478565.getHash(), block478566.getPreviousBlockHash());
        Assert.assertEquals(block478566.getHash(), block478567.getPreviousBlockHash());
        Assert.assertEquals(block478567.getHash(), block478568.getPreviousBlockHash());
        Assert.assertEquals(block478568.getHash(), block478569.getPreviousBlockHash());
        Assert.assertEquals(block478569.getHash(), block478570.getPreviousBlockHash());
        Assert.assertEquals(block478570.getHash(), block478571.getPreviousBlockHash());
        Assert.assertEquals(block478571.getHash(), block478572.getPreviousBlockHash());
        Assert.assertEquals(block478572.getHash(), block478573.getPreviousBlockHash());
        Assert.assertEquals(block478573.getHash(), block478574.getPreviousBlockHash());
        Assert.assertEquals(block478574.getHash(), block478575.getPreviousBlockHash());
        Assert.assertEquals(block478575.getHash(), block478576.getPreviousBlockHash());
        Assert.assertEquals(block478576.getHash(), blockHeader.getPreviousBlockHash());

        Assert.assertNotEquals(block478576.getDifficulty(), blockHeader.getDifficulty());

        final BlockHeader[] blockHeaders = { block478566, block478567, block478568, block478569, block478570, block478571, block478572, block478573, block478574, block478575, block478576 };

        long blockHeight = 478566L;
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (final BlockHeader block : blockHeaders) {
            medianBlockTime.addBlock(block);

            blockDatabaseManager.storeBlockHeader(block);
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                    .setParameter(blockHeight)
                    .setParameter(block.getHash())
            );

            blockHeight += 1L;
        }

        /*
            2017-08-01 17:39:21 *
            2017-08-01 19:38:29 **
            2017-08-01 21:07:01 ***
            2017-08-01 22:51:49 ****
            2017-08-01 23:15:01 *****       (blockTipMinus6)
            2017-08-02 12:20:19 ******      (blockMedianTimePast)
            2017-08-02 12:36:31 *****
            2017-08-02 14:01:34 ****
            2017-08-02 14:38:19 ***
            2017-08-02 22:03:51 **
            2017-08-02 22:27:40 *           (blockTip)

            2017-08-02 23:28:05             (blockHeader)

            If blockTip - blockTipMinusSix is greater than 12 hours, the difficulty emergency difficulty adjustment is activated...
         */

        Assert.assertTrue(medianBlockTime.hasRequiredBlockCount());

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection, medianBlockTime);

        final BlockId blockId = blockDatabaseManager.storeBlockHeader(blockHeader);
        databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(478577L).setParameter(blockHeader.getHash()));

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, blockHeader);

        // Assert
        Assert.assertEquals(ImmutableDifficulty.decode(HexUtil.hexStringToByteArray("18019902")), difficulty);
    }
}

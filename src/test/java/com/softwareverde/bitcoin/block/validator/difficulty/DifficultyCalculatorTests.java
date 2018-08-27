package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.ImmutableDifficulty;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.util.HexUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DifficultyCalculatorTests extends IntegrationTest {
    protected BlockHeader[] _initBlocks(final Long stopBeforeBlockHeight, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader block478550 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000202C76152C65B451734851B81FFBB3E2636DD5A1EE1C74B8000000000000000000A7FCE3FCC48CE39A71FAC02F29AE3842F35F3B6C1F302A88B08050AA2AADFE68C566805935470118490B9728"));
        final BlockHeader block478551 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000205423048C44541A70B4390A9971AEF0C33DFA0E93A619000100000000000000004F98CA438AC6DC923EA36656BED506F612DBD1E1EF4797D8751E7A449FEF1021956780593547011874D788E4"));
        final BlockHeader block478552 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0200002040D70AA6D0A40E73DC12E6A3E1B01E70EF77BD2C7C89460000000000000000009FBDDA67886AE4DD96B9095D1D3C9469E5752B4260A76737C0FDD9186CCC99B709718059354701188E501C06"));
        final BlockHeader block478553 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("02000020A61D05ACAB477F4507A590A70E6FDDDA3B3E83D7FCB91800000000000000000048F0D29BE222FCEB8AE3009B0DCADE95F7FEE667AACFACEA3317BF528E8A8BD5087680593547011872B2E23A"));
        final BlockHeader block478554 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("02000020BF60619CA0B40EA3A1600C52672DA7D234A6856AC7DD2B010000000000000000EC11162C8F8D27373F2E14094C83B41DC180E0F59DE40E883EDE3F95811E36070F7D805935470118EC736A6B"));
        final BlockHeader block478555 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("02000020A0ACCE51728BDA35EC4701564A95828FF754FB9BF92FE500000000000000000027DE42FE908075932AD82551CC461F45879562F5A4A69D63D71871364F92D748527D80593547011885297E11"));
        final BlockHeader block478556 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000201082264C64BFE00B14D0073169508D52EFEADB5B35A4E7000000000000000000F3BA19FCCFD6FA26D1A65F0A2885441A8F50664A4B115AEFE77190F173174D39957D8059354701186E3F5A5E"));
        final BlockHeader block478557 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000206934FCA9A5DD15210AD36FD1898D6C0AC300DBA0AA014800000000000000000000723257646DDDF1D79467B83425B0498D4C3B4EC8CF12FFF20853B67FC3F6B2FC7D8059354701183915517A"));
        final BlockHeader block478558 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("02000020E42980330B7294BEF6527AF576E5CFE2C97D55F9C19BEB0000000000000000004A88016082F466735A0F4BC9E5E42725FBC3D0AC28D4AB9547BF18654F14655B1E7F80593547011816DD5975"));
        final BlockHeader block478559 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020432D350741FBF28F2E1486EABE2C4E143BFE2241AF6518010000000000000000ABAA4BD8A48C1C6BC08EE39B66065E5E9484304CAB8B56D5EED3E40B1AC996C899C480593547011822CA4AE8"));
        final BlockHeader block478560 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020EC5E1A193601F25FF1D94B421DDEAD0DBEFCB99CF91E6500000000000000000082AFC8EF7EB41A4ECAC1FEA46983742E491F804AD662E3745AB9C6C4297D8A0862C980593547011840A772CB"));
        final BlockHeader block478561 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000207B258F3C03DCCA84586E0B6DD46244CA6A8FAF92D85AB100000000000000000058874E50628FDF83AEEA4E8CBC7ADE946E9BA14BCB1D8FFB28C3DAF8ADE84DF65FCA805935470118E2F51003"));
        final BlockHeader block478562 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020158A3E4DC9F434FE95F8306A0B3A2A86735F667488EE13000000000000000000111B85F9D3B969A1F7FF3D50AF08893C500EDFC5623B96DBEAB6DAF16A5164A40ACE805935470118C4F4240A"));
        final BlockHeader block478563 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000204DDEB88BDA32F525179FA04A6A2D3A6A324D70AA826E5C00000000000000000040A045063B551B61D6A1C9DB6D3231E2D7403185BBB2332AE1F66DB24AAC7FA288D8805935470118F15DD76B"));
        final BlockHeader block478564 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020CCEAE5D370393FE043446A14F0B502A9C115561192B37500000000000000000070CB14529E8757C359C2E8B1E987F6EEE6FBC4472EE9AD4A2E5DF6905C19D6D70BED80593547011885AE00D0"));
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

        Assert.assertEquals(block478550.getHash(), block478551.getPreviousBlockHash());
        Assert.assertEquals(block478551.getHash(), block478552.getPreviousBlockHash());
        Assert.assertEquals(block478552.getHash(), block478553.getPreviousBlockHash());
        Assert.assertEquals(block478553.getHash(), block478554.getPreviousBlockHash());
        Assert.assertEquals(block478554.getHash(), block478555.getPreviousBlockHash());
        Assert.assertEquals(block478555.getHash(), block478556.getPreviousBlockHash());
        Assert.assertEquals(block478556.getHash(), block478557.getPreviousBlockHash());
        Assert.assertEquals(block478557.getHash(), block478558.getPreviousBlockHash());
        Assert.assertEquals(block478558.getHash(), block478559.getPreviousBlockHash());
        Assert.assertEquals(block478559.getHash(), block478560.getPreviousBlockHash());
        Assert.assertEquals(block478560.getHash(), block478561.getPreviousBlockHash());
        Assert.assertEquals(block478561.getHash(), block478562.getPreviousBlockHash());
        Assert.assertEquals(block478562.getHash(), block478563.getPreviousBlockHash());
        Assert.assertEquals(block478563.getHash(), block478564.getPreviousBlockHash());
        Assert.assertEquals(block478564.getHash(), block478565.getPreviousBlockHash());
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

        final BlockHeader[] allBlockHeaders = { block478550, block478551, block478552, block478553, block478554, block478555, block478556, block478557, block478558, block478559, block478560, block478561, block478562, block478563, block478564, block478565, block478566, block478567, block478568, block478569, block478570, block478571, block478572, block478573, block478574, block478575, block478576 };

        final BlockHeader[] returnedBlockHeaders = new BlockHeader[Math.min(allBlockHeaders.length, (int) (stopBeforeBlockHeight - 478550L))];
        long blockHeight = 478550L;
        int i = 0;
        for (final BlockHeader blockHeader : allBlockHeaders) {
            if (blockHeight >= stopBeforeBlockHeight) { break; }

            blockDatabaseManager.storeBlockHeader(blockHeader);
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                    .setParameter(blockHeight)
                    .setParameter(blockHeader.getHash())
            );

            returnedBlockHeaders[i] = blockHeader;

            blockHeight += 1L;
            i += 1;
        }

        return returnedBlockHeaders;
    }

    @Before
    public void setup() {
        _resetDatabase();
        _resetCache();
    }

    @Test
    public void should_return_default_difficulty_for_block_0() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection);

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

        final BlockHeader[] blockHeaders = _initBlocks(478577L, databaseConnection);
        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FA4F8E791184C0CEE158961A0AC6F4299898F872F06A410100000000000000003EC6D34403E8B74BFE9711CE053468EFB269D87422B18DB202C3FA6CB7E503754598825902990118BE67E71E")); // 478577

        Assert.assertEquals(blockHeaders[blockHeaders.length - 1].getHash(), blockHeader.getPreviousBlockHash());
        Assert.assertNotEquals(blockHeaders[0].getDifficulty(), blockHeader.getDifficulty());

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

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection);

        final BlockId blockId = blockDatabaseManager.storeBlockHeader(blockHeader);
        databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(478577L).setParameter(blockHeader.getHash()));

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, blockHeader);

        // Assert
        Assert.assertEquals(ImmutableDifficulty.decode(HexUtil.hexStringToByteArray("18019902")), difficulty);
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000000A818C2894CBBECF77DA16CA526E3D59929CE5AFD8F0644() throws Exception {
        // Setup
        final MysqlDatabaseConnection databaseConnection = _database.newConnection();

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader[] blockHeaders = _initBlocks(478573L, databaseConnection);

        final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020774F568005A00BF4FFED76B52E3A7E5E5140A5371834A00000000000000000009F5DB27969FECC0EF71503279069B2DF981BA545592A7B425F353B5060E77F3E7E13825935470118DA70378E"));

        Assert.assertEquals(blockHeaders[blockHeaders.length - 1].getHash(), blockHeader.getPreviousBlockHash());
        Assert.assertEquals(blockHeaders[0].getDifficulty(), blockHeader.getDifficulty());

        final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(databaseConnection);

        final BlockId blockId = blockDatabaseManager.storeBlockHeader(blockHeader);
        databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(478573L).setParameter(blockHeader.getHash()));

        final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockChainSegmentId, blockHeader);

        // Assert
        Assert.assertEquals(ImmutableDifficulty.decode(HexUtil.hexStringToByteArray("18014735")), difficulty);
    }
}

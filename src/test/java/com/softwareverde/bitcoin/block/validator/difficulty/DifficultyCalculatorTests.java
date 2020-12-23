package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.CoreUpgradeSchedule;
import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.lazy.LazyDifficultyCalculatorContext;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.test.BlockData;
import com.softwareverde.bitcoin.test.IntegrationTest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.util.HexUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DifficultyCalculatorTests extends IntegrationTest {
    public static class LazyDifficultyCalculatorContextWithBlockTimeFallback extends LazyDifficultyCalculatorContext {
        public LazyDifficultyCalculatorContextWithBlockTimeFallback(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager, final UpgradeSchedule upgradeSchedule) {
            super(blockchainSegmentId, databaseManager, new DifficultyCalculatorFactory() {
                @Override
                public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
                    return new DifficultyCalculator(context);
                }
            },
            upgradeSchedule);
        }

        @Override
        public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
            // MedianBlockTime is used to determine if Asert DAA has activated.
            //  These tests use the original DAA, so the Context falls back to the Genesis MTP.
            //  It's possible that the MTP becomes needed for other functionality, at which point the test should
            //  store the needed MTPs instead of relying on the fake Genesis MTP.

            try {
                return super.getMedianBlockTime(blockHeight);
            }
            catch (final Exception exception) {
                return MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP);
            }
        }
    };
    protected BlockHeader[] _initBlocks(final Long stopBeforeBlockHeight, final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
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

        blockHeaderDatabaseManager.storeBlockHeader(blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK)));
        blockHeaderDatabaseManager.storeBlockHeader(new MutableBlockHeader(blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.BLOCK_1))) {
            @Override
            public Sha256Hash getHash() {
                return Sha256Hash.fromHexString("000000000000000000B8741CEEA1D56D63E2B3FB1FB851487351B4652C15762C");
            }
            @Override
            public Boolean isValid() { return true; }
        });

        final BlockHeader[] allBlockHeaders = { block478550, block478551, block478552, block478553, block478554, block478555, block478556, block478557, block478558, block478559, block478560, block478561, block478562, block478563, block478564, block478565, block478566, block478567, block478568, block478569, block478570, block478571, block478572, block478573, block478574, block478575, block478576 };

        final BlockHeader[] returnedBlockHeaders = new BlockHeader[Math.min(allBlockHeaders.length, (int) (stopBeforeBlockHeight - 478550L))];
        long blockHeight = 478550L;
        int i = 0;
        for (final BlockHeader blockHeader : allBlockHeaders) {
            if (blockHeight >= stopBeforeBlockHeight) { break; }

            blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
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

    protected BlockHeader[] _initBlocks2(final Long stopBeforeBlockHeight, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

        final BlockHeader block477790 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000201E41513A74A6AFC3E9B6C7DE4C311BC5D82C65F87EF4E0000000000000000000615834D9119AC057CD586B89BB823DF8F80E2FE482661457366AD76CEDDCF8A4CCC57959DC5D0118A2650916"));

        {
            final BlockInflater blockInflater = new BlockInflater();
            final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
            blockDatabaseManager.storeBlock(genesisBlock);

            // Hack the genesis block so that its hash looks like the tested-block's previousBlockHash...
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                    .setParameter(block477790.getHash())
                    .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
            );
        }

        blockHeaderDatabaseManager.storeBlockHeader(block477790);
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                .setParameter(477790L)
                .setParameter(block477790.getHash())
        );

        final BlockHeader block477791 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("1200002062519C1B5E518CFE5AC6F719917C486F71EFF9DD69273F0000000000000000000DB7A2E7D38EB9E457CE42820D90594C5222B872C5911C5E8703210CCF367761D3C77959DC5D0118332D5368"));
        blockHeaderDatabaseManager.storeBlockHeader(block477791);
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                .setParameter(477791L)
                .setParameter(block477791.getHash())
        );

        final BlockHeader block477792 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("02000020B8795AD86442CE6D8289718D7DE413E738759B7173030E0100000000000000002F4BB70CD68CAA575DE605E93A4837EC068EA8BC85A8445BADEE5A9214AEE3289AC8795935470118C4A6D809"));
        blockHeaderDatabaseManager.storeBlockHeader(block477792);
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                .setParameter(477792L)
                .setParameter(block477792.getHash())
        );

        final BlockHeader block477793 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0200002006324720303E3CEFD16BA1368B835B447691308677BA160000000000000000004B7D6BAD05C31A947E5B49F5594FC992D741EC32C951DF4969E6FDBB00FEDCF5A2C9795935470118421225CE"));
        blockHeaderDatabaseManager.storeBlockHeader(block477793);
        databaseConnection.executeSql(
            new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                .setParameter(477793L)
                .setParameter(block477793.getHash())
        );

        {
            final MutableBlockHeader block477794 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("020000206569100B4B52EADCEE0AA9A34001BE33B8AB44DB5C59C200000000000000000042FB937F6398AE22CFE62A2CFB9D96F916B0B7113905669D9D24834601225AA60DCC795935470118B131AF2B"));
            blockHeaderDatabaseManager.storeBlockHeader(block477794);

            // Hack the block so that its hash looks like the block479790's previousBlockHash...
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                    .setParameter(Sha256Hash.fromHexString("0000000000000000045A0372DFB07E71CFEE86C282800660AB73EA66BE3F8545"))
                    .setParameter(Sha256Hash.fromHexString("000000000000000000735904147ECF02E80B5F092B08961ECCD68A6A02EDF409"))
            );
        }

        final BlockHeader block479790 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020F44C014C09C71A616BE35E2171A1BCB689D265B4B2073D0200000000000000008AEAF7998B7657375DE112ED9C6D9309DF800DCD27F842C377BEAF1EF961554F13F29859D7850918F89096B5"));
        final BlockHeader block479791 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002045853FBE66EA73AB60068082C286EECF717EB0DF72035A04000000000000000093EB3B5F106412594E76E692C96BD1A601C2911509E153731D078B0BCD2424CF23F29859D7850918C87B0403"));
        final BlockHeader block479792 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D7E48C420B4CD20782744D597A1F1BC76648A1509EECDE0600000000000000006F349D42644C07A8DAC0023CDCC96BA2313F58EBF507DA3E8F38F9945BF9C9D9F7F29859D7850918D585358E"));
        final BlockHeader block479793 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002035AC9BB9FDD83BA32DB4018AFBE76E0899EB5FCCA1F1F20600000000000000003DF4E7DD81704DA300868EAC0A65E3C84218CB357EFEB2A263F44FE8BDAC4781C8F39859D785091850DEB633"));
        final BlockHeader block479794 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002095D031133EDD53A94F041BC08CB4E0938BD47367563E10080000000000000000BB774B8B090E97F9FBAD085991EADCEAEFD855021FFBDF7A65CA24785330AF6889F59859D78509184A175FB0"));
        final BlockHeader block479795 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000205DE7FFEAA9F96F07EB8243964A346200EABEB8DEC1350A0800000000000000001E20C2AAF83777BEE2EDA2A99D52B5A84B5811E057EDBFA0ED1C4AE3BA87777D9EF69859D7850918545F0838"));
        final BlockHeader block479796 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020BA7358D739AEC93038D6AD493790291564C87CFA76C4D7030000000000000000D3A49DD7B9974B1BBDF2E7F7CA7BDF65E33BB74831091CD21A0D8EBBAD13980449F79859D7850918232C8B16"));
        final BlockHeader block479797 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020C7F8C18D616BA61B9A545382C92822BEBF26639A8273D308000000000000000026C5E08FB0C2670C113E7959CEB02D54CC0A9F975E5CF6EF5ECDC3F6F53FC1915FF79859D785091844E2FBD4"));
        final BlockHeader block479798 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002095F9E1A5A85CEE578F8146115CC579FC7D96F6D76E6CCD0300000000000000005E6EA7E1335ECCE354636F8DAE5354114E35BFEB90B74148C1F94BFA8ECA3151E1F79859D785091899D4618F"));
        final BlockHeader block479799 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002080B4972FE648BF7C762027DC75BFCF46BAC6AE83D2CE9C060000000000000000BEA06B513DF61CF273AF177CAF5B81B3F699B22E2BA12A880AA89B120E60F4FD81F89859D78509187B2360E2"));
        final BlockHeader block479800 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002042A8635D58BB54712EE0FE7512B652773ED0DE54B40284060000000000000000D86ADCABF42D511A83B0883F36A70F20B6D01DDF6D9AFAF7B814E70BCB4927DEBBFA9859D7850918A15D0101"));
        final BlockHeader block479801 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000204961E3999D6966C44F20B61133C953626595B4BD90C92902000000000000000021FBA3BF9FB92B771593A4AF967B6506F75D2D734A17D8A8C41B07EB27ECDAE70BFB9859D7850918DD572136"));
        final BlockHeader block479802 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020478C2EBCAD0C3C6ECD116BF45598F515E1D3A2EDEF96A104000000000000000009CCE815B5C11B37DDD29458C9369DEC020ABC32B85AB16D8BC2251E97054E5674FB9859D78509182740D06D"));
        final BlockHeader block479803 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000206E469D0E0A304C67BCDF5DDFC17AF4393DC9142094457F020000000000000000BDC6E9AEA1C780350D27BFB43BD89088D7C88ABD02FE7B413B9303DE18CEF35ADEFD9859D7850918C09DD688"));
        final BlockHeader block479804 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020AF2DC267662C3EF5914A5F5DBDDAB6B9784A59E3F2270F060000000000000000042056FA2E27FE52F7AC9804AEC0D2F00D1725D1182E05E84A75C5F5C7647C2040019959D7850918DF080F39"));
        final BlockHeader block479805 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002008D66BDEF6970B78C50FBBFF49DD3BAB14D78B48C5624B03000000000000000084E14D6F2DC974AC4143C8D33D0D07A5217950A649037A9B4EC807F713AE57F099039959D785091851477C54"));
        final BlockHeader block479806 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020866378A9D461F92286CCE52A4170A1A6C758B1D84BBD5B090000000000000000000C901326B697602B665381122DBC6A311E417CF647886FF67701CAB3EC677E83049959D7850918A6A851A1"));
        final BlockHeader block479807 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000207378476C93379825B527239E4F7882420C110D21ECD4F80100000000000000000554E4C96C789170A37E9E3D4488F0B2203CF62D8B123BE4911C5FA33812454930059959D7850918B2AADCE2"));

        Assert.assertEquals(block479790.getHash(), block479791.getPreviousBlockHash());
        Assert.assertEquals(block479791.getHash(), block479792.getPreviousBlockHash());
        Assert.assertEquals(block479792.getHash(), block479793.getPreviousBlockHash());
        Assert.assertEquals(block479793.getHash(), block479794.getPreviousBlockHash());
        Assert.assertEquals(block479794.getHash(), block479795.getPreviousBlockHash());
        Assert.assertEquals(block479795.getHash(), block479796.getPreviousBlockHash());
        Assert.assertEquals(block479796.getHash(), block479797.getPreviousBlockHash());
        Assert.assertEquals(block479797.getHash(), block479798.getPreviousBlockHash());
        Assert.assertEquals(block479798.getHash(), block479799.getPreviousBlockHash());
        Assert.assertEquals(block479799.getHash(), block479800.getPreviousBlockHash());
        Assert.assertEquals(block479800.getHash(), block479801.getPreviousBlockHash());
        Assert.assertEquals(block479801.getHash(), block479802.getPreviousBlockHash());
        Assert.assertEquals(block479802.getHash(), block479803.getPreviousBlockHash());
        Assert.assertEquals(block479803.getHash(), block479804.getPreviousBlockHash());
        Assert.assertEquals(block479804.getHash(), block479805.getPreviousBlockHash());
        Assert.assertEquals(block479805.getHash(), block479806.getPreviousBlockHash());
        Assert.assertEquals(block479806.getHash(), block479807.getPreviousBlockHash());

        final BlockHeader[] allBlockHeaders = { block479790, block479791, block479792, block479793, block479794, block479795, block479796, block479797, block479798, block479799, block479800, block479801, block479802, block479803, block479804, block479805, block479806, block479807 };

        final BlockHeader[] returnedBlockHeaders = new BlockHeader[Math.min(allBlockHeaders.length, (int) (stopBeforeBlockHeight - 479790L))];
        long blockHeight = 479790L;
        int i = 0;
        for (final BlockHeader blockHeader : allBlockHeaders) {
            if (blockHeight >= stopBeforeBlockHeight) { break; }

            blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
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

    @Override @Before
    public void before() throws Exception {
        super.before();
    }

    @Override @After
    public void after() throws Exception {
        super.after();
    }

    @Test
    public void should_return_default_difficulty_for_block_0() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(null, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final BlockInflater blockInflater = new BlockInflater();
            final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockDatabaseManager.storeBlock(block);
            }

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(0L);

            // Assert
            Assert.assertEquals(Difficulty.BASE_DIFFICULTY, difficulty);
        }
    }

    @Test
    public void should_return_bitcoin_cash_adjusted_difficulty() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

            final BlockHeader[] blockHeaders;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaders = _initBlocks(478577L, databaseManager);
            }
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

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long blockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }
            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(478577L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18019902")), difficulty);
        }
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000000A818C2894CBBECF77DA16CA526E3D59929CE5AFD8F0644() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

            final BlockHeader[] blockHeaders;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaders = _initBlocks(478573L, databaseManager);
            }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020774F568005A00BF4FFED76B52E3A7E5E5140A5371834A00000000000000000009F5DB27969FECC0EF71503279069B2DF981BA545592A7B425F353B5060E77F3E7E13825935470118DA70378E"));

            Assert.assertEquals(blockHeaders[blockHeaders.length - 1].getHash(), blockHeader.getPreviousBlockHash());
            Assert.assertEquals(blockHeaders[0].getDifficulty(), blockHeader.getDifficulty());

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long blockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }
            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(478573L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18014735")), difficulty);
        }
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000002CF5C8BE76F5EF40196B8D1A63E0FF138F9FB1DF907E315() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

            final BlockHeader[] blockHeaders;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaders = _initBlocks2(479808L, databaseManager);
            }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020C5F04B072B446E02EFE3231190BFE67E1883BDB3D58B5A00000000000000000063AF62061B439138245E2ED248100E0EF7B25E9192FCF2DC550209D0F3F9D715AF069959CC1D101846ADA54C"));

            Assert.assertEquals(blockHeaders[blockHeaders.length - 1].getHash(), blockHeader.getPreviousBlockHash());
            Assert.assertNotEquals(blockHeaders[0].getDifficulty(), blockHeader.getDifficulty());

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long blockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }

            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(479808L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(blockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18101DCC")), difficulty);
        }
    }

    @Test
    public void should_calculate_bitcoin_cash_difficulty_for_block_00000000000000000343E9875012F2062554C8752929892C82A0C0743AC7DCFD() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();

            {
                final BlockInflater blockInflater = new BlockInflater();
                final Block block = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.insertBlock(block);
                }

                // Hack the genesis block so that its hash looks like the tested-block's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(Sha256Hash.fromHexString("00000000000000000435BC5750DAF2D9840D6CC670CC539D67B565B3F175EF40"))
                        .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
                );
            }

            final BlockHeader block503884 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002040EF75F1B365B5679D53CC70C66C0D84D9F2DA5057BC3504000000000000000064BE673E5FFCAE00F3E9543B6A29D9D9BFD99D917815E00F5DACDE20AD7EE759EE83085AF56A0818E6D5820C"));
            // ChainWork: 0000000000000000000000000000000000000000007C9252468D6FC7AA51E743
            final BlockHeader block503885 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002062B205D7E0CA90FBA87B1FC30AC413E7AE2033886DFB760700000000000000003C99B667F880E4FCE0E40EB746FC10F5B20564928B8216AC440A3AB0932E76042684085AF56A08187B499EE6"));
            // ChainWork: 0000000000000000000000000000000000000000007C9270AFF5719E18D2C3AF
            final BlockHeader block503886 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DAEC9B14A439C371137E769989ED1913249040A122403804000000000000000041476811D6EFA21523EBAE504A391A94F980995C96C53E7AE5270FD6520520E86B84085AF56A081824CB2663"));
            // ChainWork: 0000000000000000000000000000000000000000007C928F195D73748753A01B
            final BlockHeader block503887 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D0BC02429848A7F73FF340D51E2275B9481750F61E3A810500000000000000004305BA203D4CA21D4D151AE054A8C490B16A571631CD28B0643B2BF3E61C7B4DD484085AF56A08181669B1F4"));
            // ChainWork: 0000000000000000000000000000000000000000007C92AD82C5754AF5D47C87
            final BlockHeader block503888 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020989C05B9643A1F7F23583BC2523BC9CBFBBE14D2AC46000200000000000000003C66B23AC172D7E59ECFB831230C5B8AFB1FEA79EFADDABC52CCDF1F2129800B0B85085AF56A08184A7FC95A"));
            // ChainWork: 0000000000000000000000000000000000000000007C92CBEC2D7721645558F3

            Assert.assertEquals(block503884.getHash(), block503885.getPreviousBlockHash());
            Assert.assertEquals(block503885.getHash(), block503886.getPreviousBlockHash());
            Assert.assertEquals(block503886.getHash(), block503887.getPreviousBlockHash());
            Assert.assertEquals(block503887.getHash(), block503888.getPreviousBlockHash());

            long blockHeight = 503884L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block503884, block503885, block503886, block503887, block503888 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 503884L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007C9252468D6FC7AA51E743"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            {
                final BlockHeader block = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000203BEDAF10775BF4DAEA20C0CD14DFB1305643750B78ED5A03000000000000000052B2D71F8917516C6A61CF4FF70149B22337138F174971A341B9729C33D4FE8F9385085AF56A08182A6E5437"));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.insertBlockHeader(block);
                }

                // Hack the block so that its hash looks like the next block's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(Sha256Hash.fromHexString("0000000000000000004AB6CD0EC46F050566B7CA9E556CA6825039078E5CC4D3"))
                        .setParameter(Sha256Hash.fromHexString("000000000000000007C927A6A203FB2CCCA31D3AFA56EA54429576925CE07995"))
                );
            }

            final BlockHeader block504028 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D3C45C8E07395082A66C559ECAB76605056FC40ECDB64A000000000000000000DEBD79522F23890A23DDC97CE239F2760BCB23BE4274AA33EE40B99693159E694EF9095ABD1A021896DEFE6B"));
            // ChainWork: 0000000000000000000000000000000000000000007CADC3650A2DDD4BB91FD3
            final BlockHeader block504029 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000202FC1F2AB4B3B63C8217422E5E6385D5A9E28E001FEFB9401000000000000000066A022B0F05F4DF670D4105719004714509C009ED924BE9DA7486AF832B4666E89FB095ABD1A0218ECCA3FAB"));
            // ChainWork: 0000000000000000000000000000000000000000007CAE3D0AB8A8B7D8E0FE4F
            final BlockHeader block504030 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002088B635DDA8E9441A7AB80D084DC34AD28ACA42F6AA3A5F010000000000000000D2E58AD82664E61A249F1ED829FA2D52CCF77D41AF12EDD3230314C99E011DE8CCFC095ABD1A021828F4EA08"));
            // ChainWork: 0000000000000000000000000000000000000000007CAEB6B06723926608DCCB
            final BlockHeader block504031 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DE1EFF50CF57B6903D5FC8C4D72A35B798DA483FD688800000000000000000007EEBC424B078D2FC05CD8B49AC1E628B1A7D69B39B4F0F497AD1F310F1D73DAB60070A5ABD1A0218651C93E1"));
            // ChainWork: 0000000000000000000000000000000000000000007CAF3056159E6CF330BB47

            Assert.assertEquals(block504028.getHash(), block504029.getPreviousBlockHash());
            Assert.assertEquals(block504029.getHash(), block504030.getPreviousBlockHash());
            Assert.assertEquals(block504030.getHash(), block504031.getPreviousBlockHash());

            blockHeight = 504028L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block504028, block504029, block504030, block504031 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 504028L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007CADC3650A2DDD4BB91FD3"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000209CABB6EE1B1A4C3B659D70BE75810BE83D0A0DB665BF1E010000000000000000EE1BE69B11EF6D4B6B15E007628C3AF5563B967F35155FAF0ABAB1D87921BF8E93080A5A2BB40518462F5110"));

            Assert.assertEquals(block504031.getHash(), blockHeader.getPreviousBlockHash());

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long validationBlockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                validationBlockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }

            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(504032L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(validationBlockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("1805B42B")), difficulty);
        }
    }

    @Test
    public void should_calculate_bitcoin_cash_difficulty_for_block_000000000000000002173D0AC7B3A30F2AAC302449778D72386C785D2C370429() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

            final BlockHeader block503884 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002040EF75F1B365B5679D53CC70C66C0D84D9F2DA5057BC3504000000000000000064BE673E5FFCAE00F3E9543B6A29D9D9BFD99D917815E00F5DACDE20AD7EE759EE83085AF56A0818E6D5820C"));
            // ChainWork: 0000000000000000000000000000000000000000007C9252468D6FC7AA51E743
            final BlockHeader block503885 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002062B205D7E0CA90FBA87B1FC30AC413E7AE2033886DFB760700000000000000003C99B667F880E4FCE0E40EB746FC10F5B20564928B8216AC440A3AB0932E76042684085AF56A08187B499EE6"));
            // ChainWork: 0000000000000000000000000000000000000000007C9270AFF5719E18D2C3AF
            final BlockHeader block503886 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DAEC9B14A439C371137E769989ED1913249040A122403804000000000000000041476811D6EFA21523EBAE504A391A94F980995C96C53E7AE5270FD6520520E86B84085AF56A081824CB2663"));
            // ChainWork: 0000000000000000000000000000000000000000007C928F195D73748753A01B
            final BlockHeader block503887 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D0BC02429848A7F73FF340D51E2275B9481750F61E3A810500000000000000004305BA203D4CA21D4D151AE054A8C490B16A571631CD28B0643B2BF3E61C7B4DD484085AF56A08181669B1F4"));
            // ChainWork: 0000000000000000000000000000000000000000007C92AD82C5754AF5D47C87
            final BlockHeader block503888 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020989C05B9643A1F7F23583BC2523BC9CBFBBE14D2AC46000200000000000000003C66B23AC172D7E59ECFB831230C5B8AFB1FEA79EFADDABC52CCDF1F2129800B0B85085AF56A08184A7FC95A"));
            // ChainWork: 0000000000000000000000000000000000000000007C92CBEC2D7721645558F3
            final BlockHeader block503889 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000203BEDAF10775BF4DAEA20C0CD14DFB1305643750B78ED5A03000000000000000052B2D71F8917516C6A61CF4FF70149B22337138F174971A341B9729C33D4FE8F9385085AF56A08182A6E5437"));
            // ChainWork: 0000000000000000000000000000000000000000007C92EA559578F7D2D6355F

            Assert.assertEquals(block503884.getHash(), block503885.getPreviousBlockHash());
            Assert.assertEquals(block503885.getHash(), block503886.getPreviousBlockHash());
            Assert.assertEquals(block503886.getHash(), block503887.getPreviousBlockHash());
            Assert.assertEquals(block503887.getHash(), block503888.getPreviousBlockHash());
            Assert.assertEquals(block503888.getHash(), block503889.getPreviousBlockHash());

            {
                final BlockInflater blockInflater = new BlockInflater();
                final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(genesisBlock);
                }

                // Hack the genesis block so that its hash looks like the tested-block's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(block503884.getPreviousBlockHash())
                        .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
                );
            }


            long blockHeight = 503884L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block503884, block503885, block503886, block503887, block503888, block503889 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 503884L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007C9252468D6FC7AA51E743"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            final BlockHeader block504028 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D3C45C8E07395082A66C559ECAB76605056FC40ECDB64A000000000000000000DEBD79522F23890A23DDC97CE239F2760BCB23BE4274AA33EE40B99693159E694EF9095ABD1A021896DEFE6B"));
            // ChainWork: 0000000000000000000000000000000000000000007CADC3650A2DDD4BB91FD3
            final BlockHeader block504029 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000202FC1F2AB4B3B63C8217422E5E6385D5A9E28E001FEFB9401000000000000000066A022B0F05F4DF670D4105719004714509C009ED924BE9DA7486AF832B4666E89FB095ABD1A0218ECCA3FAB"));
            // ChainWork: 0000000000000000000000000000000000000000007CAE3D0AB8A8B7D8E0FE4F
            final BlockHeader block504030 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002088B635DDA8E9441A7AB80D084DC34AD28ACA42F6AA3A5F010000000000000000D2E58AD82664E61A249F1ED829FA2D52CCF77D41AF12EDD3230314C99E011DE8CCFC095ABD1A021828F4EA08"));
            // ChainWork: 0000000000000000000000000000000000000000007CAEB6B06723926608DCCB
            final BlockHeader block504031 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DE1EFF50CF57B6903D5FC8C4D72A35B798DA483FD688800000000000000000007EEBC424B078D2FC05CD8B49AC1E628B1A7D69B39B4F0F497AD1F310F1D73DAB60070A5ABD1A0218651C93E1"));
            // ChainWork: 0000000000000000000000000000000000000000007CAF3056159E6CF330BB47
            final BlockHeader block504032 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000209CABB6EE1B1A4C3B659D70BE75810BE83D0A0DB665BF1E010000000000000000EE1BE69B11EF6D4B6B15E007628C3AF5563B967F35155FAF0ABAB1D87921BF8E93080A5A2BB40518462F5110"));
            // ChainWork: 0000000000000000000000000000000000000000007CAF5D3801A392AC1E2266

            Assert.assertEquals(block504028.getHash(), block504029.getPreviousBlockHash());
            Assert.assertEquals(block504029.getHash(), block504030.getPreviousBlockHash());
            Assert.assertEquals(block504030.getHash(), block504031.getPreviousBlockHash());
            Assert.assertEquals(block504031.getHash(), block504032.getPreviousBlockHash());

            {
                final BlockHeader block503890 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000209579E05C9276954254EA56FA3A1DA3CC2CFB03A2A627C90700000000000000000041850165F77D5CDBEF74DCA7FB492328BA575B9708202C094D0D19F6EAC5121586085AF56A08186C37472D"));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block503890);
                }

                // Hack the block so that its hash looks like the block503884's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(block504028.getPreviousBlockHash())
                        .setParameter(block503890.getHash())
                );
            }

            blockHeight = 504028L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block504028, block504029, block504030, block504031, block504032 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 504028L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007CADC3650A2DDD4BB91FD3"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020FDDCC73A74C0A0822C89292975C8542506F2125087E9430300000000000000009B7A863829AD082CAF6D81DFFEDC5B964049BB7A0032FE4D363CD62A8E7E36A356090A5ADFC805181AD77B05"));

            Assert.assertEquals(block504032.getHash(), blockHeader.getPreviousBlockHash());

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long validationBlockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                validationBlockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }

            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(504033L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(validationBlockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("1805C8DF")), difficulty);
        }
    }

    @Test
    public void should_calculate_bitcoin_cash_difficulty_for_block_0000000000000000044B32C9A65C643A5B7EA4C38C5A34E6E1203998D3B38392() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

            final BlockHeader block504940 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000207F96BFA443BB1ED083E8091972AE62EE4656B1D34A741A0100000000000000005D96B2D6A485A654581DE98D0650DA22839288172F050BE7A081AD414E6D6D8F0F68125A10160618008E4ADB"));
            final BlockHeader block504941 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002020A6CED7518848CB69807B1F2624F841FFAAEE8049A8BD0200000000000000007D6EFA12553307585EE114A500BC30B1E7643015510DE35BB3A179E31F141F382269125A5A1F06184B2B7352"));
            final BlockHeader block504942 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000208A41A4051838F915CF42935089C7DF12AD43C3A43357DF000000000000000000617E0D1924695F4A7128C5683630DCC68EACB6434B349DFE9BE578CAC6E0058B4B70125ABE320618051C314F"));
            final BlockHeader block504943 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000208D1B5B05073A755C35AE763E8F25050332587576D51C3A040000000000000000CAD3CB19DE59BE7ABE0383C413A13F97DAD9EE2AB7E78D24C4F25AE56258DBAE6273125AB32206185D7A6BEF"));
            final BlockHeader block504944 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020BF669B2EA52778AF6358EA5C056ACD8F770AA989B69DE0000000000000000000D11F3CEA5955AC2DCEEB92644418F887A7F7CBEE8FB371F9169EA297314CB4067777125AFF0606180D7C6143"));
            final BlockHeader block504945 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020B22EB7086713B1034D43F3E34B59F353B040548C0A96B8030000000000000000444B1A283242A592AE0C88C47E1464BA675EF5C5ACCA62D02A46A58DEE9B21B8E279125A971606187B744418"));

            Assert.assertEquals(block504940.getHash(), block504941.getPreviousBlockHash());
            Assert.assertEquals(block504941.getHash(), block504942.getPreviousBlockHash());
            Assert.assertEquals(block504942.getHash(), block504943.getPreviousBlockHash());
            Assert.assertEquals(block504943.getHash(), block504944.getPreviousBlockHash());
            Assert.assertEquals(block504944.getHash(), block504945.getPreviousBlockHash());

            {
                final BlockInflater blockInflater = new BlockInflater();
                final Block genesisBlock = blockInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockDatabaseManager.storeBlock(genesisBlock);
                }

                // Hack the genesis block so that its hash looks like the tested-block's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(block504940.getPreviousBlockHash())
                        .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
                );
            }

            long blockHeight = 504940L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block504940, block504941, block504942, block504943, block504944, block504945 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 504940L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007D3EACCB1F141B6BA962F8"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            final BlockHeader block505085 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000203700A49D88CA61917F366F82879055E38638AC69DE8B240200000000000000006073A21BB91119A3566A7E868294675F72AFB4310829E89BF129816722531240FBC5135AC4CE06189B23C6B7"));
            final BlockHeader block505086 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000207C5E37326104F6BC8C63F5205F2E80C5BE0550F0575FB0060000000000000000F3F80EA9626D20070C60C0BF164FBD5EC19F8EC44E6E2BE745CA487679E5C1D2A6CB135A26B40618D9ED4EAC"));
            final BlockHeader block505087 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020A661F15246670FE26DE06CD6B96D0B6196C7C1E6DDD1350100000000000000000181AB574205822742D399856350124B67CF2A62765C1546CF32EAB21EEF3D9A0ECC135A7BC00618A704BBC7"));
            final BlockHeader block505088 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002078387A4DEEE4C5969EC34284350A848B8BC9663A7A676705000000000000000084CFC4F095B2FFFA55368260BB82E319CA36D8F4CB68A061925CDC7D95EABFC33ACF135A0ABA0618B4DA2FC2"));
            final BlockHeader block505089 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002076DE30BC9F3B3F449668F1E23349FC365073E745F40481050000000000000000B768DED6A1F7EED9A2859FE80855D099AA16492EFE7D81055BEF515948662BF33ACF135AE3AD061885DE69B2"));

            Assert.assertEquals(block505085.getHash(), block505086.getPreviousBlockHash());
            Assert.assertEquals(block505086.getHash(), block505087.getPreviousBlockHash());
            Assert.assertEquals(block505087.getHash(), block505088.getPreviousBlockHash());
            Assert.assertEquals(block505088.getHash(), block505089.getPreviousBlockHash());

            {
                final BlockHeader block504946 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000205EDDCB2C5994B16FD9F0155836404CD3BCDBD1D2D6ABF2010000000000000000A85C491190D7DF041B6D8301B9DC61469A6D3450E95E03B57159F7F8A70914CFFB79125AD927061849AD94C2"));
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(block504946);
                }

                // Hack the block so that its hash looks like the block505085's previousBlockHash...
                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET hash = ? WHERE hash = ?")
                        .setParameter(block505085.getPreviousBlockHash())
                        .setParameter(block504946.getHash())
                );
            }

            blockHeight = 505085L;
            for (final BlockHeader blockHeader : new BlockHeader[] { block505085, block505086, block505087, block505088, block505089 }) {
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                }

                if (blockHeight == 505085L) {
                    databaseConnection.executeSql(
                        new Query("UPDATE blocks SET chain_work = ? WHERE hash = ?")
                            .setParameter(ChainWork.fromHexString("0000000000000000000000000000000000000000007D54E1FD313DB21DF0DE08"))
                            .setParameter(blockHeader.getHash())
                    );
                }

                databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_height = ? WHERE hash = ?")
                        .setParameter(blockHeight)
                        .setParameter(blockHeader.getHash())
                );

                blockHeight += 1L;
            }

            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020D3DB5B631E21D35F11BD907E80714D6CFC49F8E32EDE8A030000000000000000ABEC7E29315F8A7CAB8AD87F65BB7EB178E0FBB1EFCE61766A2C97A31820E3C9E4CF135AB6AA0618A53BC16C"));

            Assert.assertEquals(block505089.getHash(), blockHeader.getPreviousBlockHash());

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            final Long validationBlockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                validationBlockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }

            databaseConnection.executeSql(new Query("UPDATE blocks SET block_height = ? WHERE hash = ?").setParameter(505090L).setParameter(blockHeader.getHash()));

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(validationBlockHeight);

            // Assert
            Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("1806AAB6")), difficulty);
        }
    }

    @Test
    public void should_calculate_difficulty_for_block_000000000000000001D49711B252E3C8DDAEFEC6A668B3104E4C4EE63908A587() throws Exception {
        // Setup
        try (final FullNodeDatabaseManager databaseManager = _fullNodeDatabaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final UpgradeSchedule upgradeSchedule = new CoreUpgradeSchedule();
            final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();

            final Long blockHeight = 547204L;

            // Fake Pre-Block
            final BlockHeader preBlock = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray(BlockData.MainChain.GENESIS_BLOCK));

            // Starting Blocks (547057 through 547059)
            final BlockHeader block547057 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020B5FBB21B15194519A93E89B3C9E4DCDE7A30E3378065540000000000000000002307AE41F518AB7B1ADCEC650A66DFA6D2805C0C583D5D74A9A6E4D8C71B52B4309A945B211902181E80E9F6"));
            final BlockHeader block547058 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002087D4725EF111A4C59AD8F4324A41C034AA089B0268D733010000000000000000CD99207673166CF38726CED59FD0902802935C6C6B67B80E17A976009340DFCC4C9C945B88180218867927A5"));
            final BlockHeader block547059 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020F54AF12DFB380656BF448C8AE3A20151C56A312B9DE9790100000000000000008F9A5ABD96F5D9731CCDEDBC978169378178557031E8F0BD0F3AE87070848EFF499D945B7F2102180C04F854"));

            // Fake Transition Block
            final BlockHeader transitionBlock = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020E5E9CA253DEB603C1BBFA40C3905F598C38737502E61A5010000000000000000C7E8374831FD345C38A008E6EF456DA791C7118AA3AD912A6648E1CE8362C7D3E29F945B852302184B0E186E")); // 547060

            // Ending Blocks (547201 through 547203)
            final BlockHeader block547201 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("00000020DEBBC302DBED222D0FF299D1FDFC2667ED9CB5DCAC3B040100000000000000000DBFBF361ADC14E26962F130A465AF6E4D68746AF94F38F2E783ACC8C094DE651EFA955B982302189EC47A14"));
            final BlockHeader block547202 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("0000002030C831E27B35C498552F087186351E8A55DC1F1D83CF49010000000000000000CD7611E790C1051D5FC2E024EEAFD7A691DD3B92167211BEAFAE161FAC030D972DFC955B0F240218F86BB4D6"));
            final BlockHeader block547203 = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000201F8D5E668DAA58B04FE17C1AA448FA143E64F01D9177030200000000000000001030F6AF6ED57728388DDBC309B0D690E70AA60E9EECE501AF2B50C3A1DE5594D6FD955B231D02181541C84F"));

            // Block To Test...
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(HexUtil.hexStringToByteArray("000000209C40E748F5BADA0670EE1F67EE0E3D9F48CE1D0753538E01000000000000000016C1FA95946D658E7F79DF9420EAF862F320A95630217EDED426114C46C1F5F88B01965B221D02186548ACB8"));

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(preBlock);
            }
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET hash = ?, block_height = ?, chain_work = ? WHERE hash = ?")
                    .setParameter(Sha256Hash.fromHexString("00000000000000000054658037E3307ADEDCE4C9B3893EA9194519151BB2FBB5"))
                    .setParameter(547056L)
                    .setParameter(Sha256Hash.fromHexString("000000000000000000000000000000000000000000C06C4B44874C9B9A130D94"))
                    .setParameter(BlockHeader.GENESIS_BLOCK_HASH)
            );

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(block547057);
                blockHeaderDatabaseManager.storeBlockHeader(block547058);
                blockHeaderDatabaseManager.storeBlockHeader(block547059);

                blockHeaderDatabaseManager.storeBlockHeader(transitionBlock);
            }
            databaseConnection.executeSql(
                new Query("UPDATE blocks SET hash = ?, block_height = ?, chain_work = ? WHERE hash = ?")
                    .setParameter(Sha256Hash.fromHexString("000000000000000001043BACDCB59CED6726FCFDD199F20F2D22EDDB02C3BBDE"))
                    .setParameter(547200L)
                    .setParameter(Sha256Hash.fromHexString("000000000000000000000000000000000000000000C0B356BB448CE8066B2F93"))
                    .setParameter(Sha256Hash.fromHexString("0000000000000000004129906FC0D6496A4194CD97251260FCF7F202A494DB92"))
            );

            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockHeaderDatabaseManager.storeBlockHeader(block547201);
                blockHeaderDatabaseManager.storeBlockHeader(block547202);
                blockHeaderDatabaseManager.storeBlockHeader(block547203);
            }

            final Long validationBlockHeight;
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                final BlockId blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
                validationBlockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
            }

            final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
            final LazyDifficultyCalculatorContext difficultyCalculatorContext = new LazyDifficultyCalculatorContextWithBlockTimeFallback(blockchainSegmentId, databaseManager, upgradeSchedule);
            final DifficultyCalculator difficultyCalculator = new DifficultyCalculator(difficultyCalculatorContext);

            // Action
            final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(validationBlockHeight);

            // Assert
            final Difficulty expectedDifficulty = Difficulty.decode(ByteArray.fromHexString("18021D22"));
            Assert.assertEquals(expectedDifficulty, difficulty);
        }
    }
}

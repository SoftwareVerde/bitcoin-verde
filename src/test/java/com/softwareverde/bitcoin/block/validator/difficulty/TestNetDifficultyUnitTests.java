package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeDifficultyCalculatorContext;
import com.softwareverde.bitcoin.util.IoUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;

public class TestNetDifficultyUnitTests extends UnitTest {
    @Test
    public void should_calculate_difficulty_for_block_000000001AF3B22A7598B10574DEB6B3E2D596F36D62B0A49CB89A1F99AB81EB() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final FakeDifficultyCalculatorContext difficultyCalculatorContext = new FakeDifficultyCalculatorContext();
        final HashMap<Long, BlockHeader> blockHeaders = difficultyCalculatorContext.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = difficultyCalculatorContext.getMedianBlockTimes();

        {
            final Long blockHeight = 2014L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000B9EAB344C59D69D0F67CB5CDF9A426D2D6F0B4BBDC6CF35EF692091D000000001C3ADB49B0A067BCAEB2A7B812F42E5B76E6F6BE723DEBF9CE2459ED7582E152E9BEBF4FFFFF001D071FD25F0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04E9BEBF4F011A172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A01000000232102814088FAA9803F0B0D6B9017E23EC53913F0366C2510472E630478E2E50ABD09AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 2015L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("010000007CDC1D863347ED850B6ACE004073C62A5042521698E524D682D01DE9000000008071C4E60214C16C1F2D5D56E7713782D6FA9F16D9692F49F8FA8428BE51C909E9BEBF4FFFFF001D0B7A9E0D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04E9BEBF4F0132172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321035A9054077A7F18F085BD954D1536A64DF794E0CB1210EAAF7DBBC4AD2AEFD181AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 2016L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000299B05FA50212725C6082936BB1CEDE9164AAA36103325504C744B8600000000F480BD5081532A426C15BF3AA534A0817B21217CE21DACA4EA87AC509637D670EABEBF4FFFFF001D04E6838E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04EABEBF4F010B172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A01000000232103894E257ABCCA0889212F3648867CBAE0C06EA5413A8BC936ED61740D088642EEAC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4030L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("010000008786E1FE747F0545A0630B701F5E10A26C6BDBBDBEC4F34892B17795000000006670DC27B3D686DF3AFBADFD8888CE93276AD94E62881F660BF0255DF8682EE239C0BF4FFFFF001D0ACDFE510101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F0439C0BF4F0104172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321039A68569864A29872632DA58879C4A820029C2C3A5B6869969A3D87C7D6EDF0DDAC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4031L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000B06831147C84E7835E0AAAE59A9E56476896CF09F2493BE64928074B000000006096CD1532E9EDF3E52045A2F5C220D5782630C77A55C0EEF17C5F87B701774939C0BF4FFFFF001D186FBB4E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F0439C0BF4F011C172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321029CC239EB9233C0C6B27EDE841BD4ACDE244068C28F71E831A34FD37B79994604AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final BlockHeader blockHeader;
        {
            final Long blockHeight = 4032L;
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000C7B49F0DAB7CF7D20AC9F654C7E9B2E12921D7F8CC669199FCCF9C2E00000000AD205DC405B584A3FC8076299DFCFA2B52D436B40E7FF75E8B0E1499EDCFB03C3AC0BF4FC0FF3F1C1FD27A3E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F043AC0BF4F0111172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A0100000023210224E7FF9142B9B0945910B16FA3139DC9177FEC58EF6749D50FA5F437830120C3AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final DifficultyCalculator difficultyCalculator = new TestNetDifficultyCalculator(difficultyCalculatorContext);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(4032L);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
        Assert.assertTrue(difficulty.isSatisfiedBy(blockHeader.getHash()));
    }

    @Test
    public void should_calculate_difficulty_for_block_00000000DB623A1752143F2F805C4527573570D9B4CA0A3CFE371E703AC429AA() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final FakeDifficultyCalculatorContext difficultyCalculatorContext = new FakeDifficultyCalculatorContext();
        final HashMap<Long, BlockHeader> blockHeaders = difficultyCalculatorContext.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = difficultyCalculatorContext.getMedianBlockTimes();

        {
            final Long blockHeight = 2015L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("010000007CDC1D863347ED850B6ACE004073C62A5042521698E524D682D01DE9000000008071C4E60214C16C1F2D5D56E7713782D6FA9F16D9692F49F8FA8428BE51C909E9BEBF4FFFFF001D0B7A9E0D0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04E9BEBF4F0132172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321035A9054077A7F18F085BD954D1536A64DF794E0CB1210EAAF7DBBC4AD2AEFD181AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 2016L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000299B05FA50212725C6082936BB1CEDE9164AAA36103325504C744B8600000000F480BD5081532A426C15BF3AA534A0817B21217CE21DACA4EA87AC509637D670EABEBF4FFFFF001D04E6838E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04EABEBF4F010B172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A01000000232103894E257ABCCA0889212F3648867CBAE0C06EA5413A8BC936ED61740D088642EEAC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 2017L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000E93D9D5DA00E7310A69ACA2D4916FCA75CC22BCC7F9FD795FD57D78900000000B9A9B7A12CA9F771461BB059E8408CDD87E18D131C7B59FF0D6FB9E06408DE55EABEBF4FFFFF001D0A7648660101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F04EABEBF4F012C172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A0100000023210219BAC775470961A1998FAE7113FA221BDA3B73A0A3BF1295E6EDEFF257740EFFAC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4031L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000B06831147C84E7835E0AAAE59A9E56476896CF09F2493BE64928074B000000006096CD1532E9EDF3E52045A2F5C220D5782630C77A55C0EEF17C5F87B701774939C0BF4FFFFF001D186FBB4E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F0439C0BF4F011C172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321029CC239EB9233C0C6B27EDE841BD4ACDE244068C28F71E831A34FD37B79994604AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4032L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000C7B49F0DAB7CF7D20AC9F654C7E9B2E12921D7F8CC669199FCCF9C2E00000000AD205DC405B584A3FC8076299DFCFA2B52D436B40E7FF75E8B0E1499EDCFB03C3AC0BF4FC0FF3F1C1FD27A3E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F043AC0BF4F0111172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A0100000023210224E7FF9142B9B0945910B16FA3139DC9177FEC58EF6749D50FA5F437830120C3AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final BlockHeader blockHeader;
        {
            final Long blockHeight = 4033L;
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/00000000DB623A1752143F2F805C4527573570D9B4CA0A3CFE371E703AC429AA")));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final DifficultyCalculator difficultyCalculator = new TestNetDifficultyCalculator(difficultyCalculatorContext);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(4033L);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
        Assert.assertTrue(difficulty.isSatisfiedBy(blockHeader.getHash()));
    }

    @Test
    public void should_calculate_difficulty_for_block_0000000030B3DC00BFD9E8AE426ECF36BD6D25F28D83B53AC9A7FDAF886A9CE8() {
        // Setup
        final BlockHeaderInflater blockHeaderInflater = new BlockHeaderInflater();
        final FakeDifficultyCalculatorContext difficultyCalculatorContext = new FakeDifficultyCalculatorContext();
        final HashMap<Long, BlockHeader> blockHeaders = difficultyCalculatorContext.getBlockHeaders();
        final HashMap<Long, MedianBlockTime> medianBlockTimes = difficultyCalculatorContext.getMedianBlockTimes();

        {
            final Long blockHeight = 4031L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000B06831147C84E7835E0AAAE59A9E56476896CF09F2493BE64928074B000000006096CD1532E9EDF3E52045A2F5C220D5782630C77A55C0EEF17C5F87B701774939C0BF4FFFFF001D186FBB4E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F0439C0BF4F011C172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A010000002321029CC239EB9233C0C6B27EDE841BD4ACDE244068C28F71E831A34FD37B79994604AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4032L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000C7B49F0DAB7CF7D20AC9F654C7E9B2E12921D7F8CC669199FCCF9C2E00000000AD205DC405B584A3FC8076299DFCFA2B52D436B40E7FF75E8B0E1499EDCFB03C3AC0BF4FC0FF3F1C1FD27A3E0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF1F043AC0BF4F0111172F503253482F49636549726F6E2D51432D6D696E65722FFFFFFFFF0100F2052A0100000023210224E7FF9142B9B0945910B16FA3139DC9177FEC58EF6749D50FA5F437830120C3AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        {
            final Long blockHeight = 4033L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString(IoUtil.getResource("/blocks/00000000DB623A1752143F2F805C4527573570D9B4CA0A3CFE371E703AC429AA")));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        int timestampHackCount = 0;
        for (long blockHeight = 4207L; blockHeight >= 4034L; --blockHeight) { // To simulate the real-scenario of perpetual 20-minute reset-blocks.
            final MutableBlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000297AF2563B650F6E99173E5D2AF552E5863566CC7367B55E601CDE60000000006FF5375F78FA999974677FB5FBB69C9C5961B120A81845B4AA97A0E159C00CE939F5C24FFFFF001D0E0084190101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF3F0439F5C24F0106372F503253482F204C6561677565206F66206173746F6E697368696E676C79206C75636B792061626163757320656E746875736961737473FFFFFFFF0100F2052A01000000232102FE9F54A1A09D1BB7BFE441650E52FFF7E4A0994005105784304240C11F3CAB0EAC00000000"));
            blockHeader.setTimestamp(blockHeader.getTimestamp() - (20L * 60L * timestampHackCount));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
            timestampHackCount += 1;
        }

        {
            final Long blockHeight = 4208L;
            final BlockHeader blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("01000000F8DCD822CD43CA2C0D9EA6A97E6C63692701E9FDF85B6012E1A438C300000000AB642CBF77DC28BFD12A5BB134AA8F753E95A33E94DEEF7DD7786305CCF18B52EAF9C24FFFFF001D148B509C0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF3F04EAF9C24F0110372F503253482F204C6561677565206F66206173746F6E697368696E676C79206C75636B792061626163757320656E746875736961737473FFFFFFFF0100F2052A010000002321034700EE1C343333E1956C5FBEBBCB553F19F2E5315E0246BA47E0A01C38CD1D85AC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final BlockHeader blockHeader;
        {
            final Long blockHeight = 4209L;
            blockHeader = blockHeaderInflater.fromBytes(ByteArray.fromHexString("010000004E16CDE4321E79D2DAD4A5453C3DC6558F2A5102222057CA46C1F0EC00000000E11EEB749B186B07DF928AC97F76413E2336175B0EC0AE5E7A2B49092F51886120ECC24FC0FF3F1CA70357FD0101000000010000000000000000000000000000000000000000000000000000000000000000FFFFFFFF0E0420ECC24F010F062F503253482FFFFFFFFF0100F2052A01000000232103C853C388F26B6DC0E2B915BF74D02AB7F45AF12D4880D783965A84AFFB7E552AAC00000000"));
            blockHeaders.put(blockHeight, blockHeader);
            medianBlockTimes.put(blockHeight, MedianBlockTime.fromSeconds(MedianBlockTime.GENESIS_BLOCK_TIMESTAMP));
        }

        final DifficultyCalculator difficultyCalculator = new TestNetDifficultyCalculator(difficultyCalculatorContext);

        // Action
        final Difficulty difficulty = difficultyCalculator.calculateRequiredDifficulty(4209L);

        // Assert
        Assert.assertEquals(blockHeader.getDifficulty(), difficulty);
        Assert.assertTrue(difficulty.isSatisfiedBy(blockHeader.getHash()));
    }
}

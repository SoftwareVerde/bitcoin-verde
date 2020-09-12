package com.softwareverde.bitcoin.block.validator.difficulty;

import com.softwareverde.bitcoin.bip.HF20201115;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.test.UnitTest;
import com.softwareverde.bitcoin.test.fake.FakeReferenceBlockLoaderContext;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.logging.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

public class AsertReferenceBlockLoaderTests extends UnitTest {

    protected static final BlockchainSegmentId BLOCKCHAIN_SEGMENT_ID = BlockchainSegmentId.wrap(1L);
    protected static final MedianBlockTime NOT_ACTIVATED_BLOCK_TIME = MedianBlockTime.fromSeconds(HF20201115.ACTIVATION_BLOCK_TIME - 1L);
    protected static final MedianBlockTime ACTIVATION_BLOCK_TIME = MedianBlockTime.fromSeconds(HF20201115.ACTIVATION_BLOCK_TIME);

    protected static final AtomicInteger TOTAL_LOOKUP_COUNT = new AtomicInteger(0);
    protected static final AtomicInteger TOTAL_MTP_CALCULATION_COUNT = new AtomicInteger(0);

    @AfterClass
    public static void afterClass() throws Exception {
        System.out.println();
        Logger.debug("Total Header Lookup Count: " + TOTAL_LOOKUP_COUNT.get());
        Logger.debug("Total MTP Calculation Count: " + TOTAL_MTP_CALCULATION_COUNT.get());
    }

    @Test
    public void should_return_null_reference_block_before_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        for (int i = 0; i < 10000; ++i) {
            final Long blockHeight = (699998L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 699999L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNull(asertReferenceBlock);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }

    @Test
    public void should_load_reference_block_at_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        for (int i = 0; i < 10000; ++i) {
            final Long blockHeight = (699999L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700000L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033273"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNotNull(asertReferenceBlock);
        Assert.assertEquals(BigInteger.valueOf(700000L), asertReferenceBlock.blockHeight);
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18033273")), asertReferenceBlock.difficulty);
        Assert.assertEquals(ACTIVATION_BLOCK_TIME, asertReferenceBlock.blockTime);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }

    @Test
    public void should_load_reference_block_one_past_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        for (int i = 0; i < 10000; ++i) {
            final Long blockHeight = (699999L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700000L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033273"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700001L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033274"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNotNull(asertReferenceBlock);
        Assert.assertEquals(BigInteger.valueOf(700000L), asertReferenceBlock.blockHeight);
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18033273")), asertReferenceBlock.difficulty);
        Assert.assertEquals(ACTIVATION_BLOCK_TIME, asertReferenceBlock.blockTime);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }

    @Test
    public void should_load_reference_block_shortly_past_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        final int afterCount = 128;

        for (int i = 0; i < 10000; ++i) {
            final Long blockHeight = (699999L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700000L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033273"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        for (int i = 0; i < afterCount; ++i) {
            final Long blockHeight = (700001L + i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033274"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNotNull(asertReferenceBlock);
        Assert.assertEquals(BigInteger.valueOf(700000L), asertReferenceBlock.blockHeight);
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18033273")), asertReferenceBlock.difficulty);
        Assert.assertEquals(ACTIVATION_BLOCK_TIME, asertReferenceBlock.blockTime);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }

    @Test
    public void should_load_reference_block_well_past_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        final int afterCount = 10000;

        for (int i = 0; i < afterCount; ++i) {
            final Long blockHeight = (699999L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700000L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033273"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        for (int i = 0; i < afterCount; ++i) {
            final Long blockHeight = (700001L + i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033274"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNotNull(asertReferenceBlock);
        Assert.assertEquals(BigInteger.valueOf(700000L), asertReferenceBlock.blockHeight);
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18033273")), asertReferenceBlock.difficulty);
        Assert.assertEquals(ACTIVATION_BLOCK_TIME, asertReferenceBlock.blockTime);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }

    @Test
    public void should_load_reference_block_excessively_past_activation_time() throws Exception {
        // Setup
        final FakeReferenceBlockLoaderContext context = new FakeReferenceBlockLoaderContext();

        final int afterCount = 100000;

        for (int i = 0; i < afterCount; ++i) {
            final Long blockHeight = (699999L - i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18031F32"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, NOT_ACTIVATED_BLOCK_TIME, difficulty);
        }

        {
            final Long blockHeight = 700000L;
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033273"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        for (int i = 0; i < afterCount; ++i) {
            final Long blockHeight = (700001L + i);
            final BlockId blockId = BlockId.wrap(blockHeight + 1L);
            final Difficulty difficulty = Difficulty.decode(ByteArray.fromHexString("18033274"));

            context.setBlockHeader(BLOCKCHAIN_SEGMENT_ID, blockId, blockHeight, ACTIVATION_BLOCK_TIME, difficulty);
        }

        final AsertReferenceBlockLoader asertReferenceBlockLoader = new AsertReferenceBlockLoader(context);

        // Action
        final AsertReferenceBlock asertReferenceBlock = asertReferenceBlockLoader.getAsertReferenceBlock(BLOCKCHAIN_SEGMENT_ID);

        // Assert
        Logger.debug("Header Lookup Count: " + context.getLookupCount());
        Logger.debug("MTP Calculation Count: " + context.getMedianTimePastCalculationCount());
        Assert.assertNotNull(asertReferenceBlock);
        Assert.assertEquals(BigInteger.valueOf(700000L), asertReferenceBlock.blockHeight);
        Assert.assertEquals(Difficulty.decode(ByteArray.fromHexString("18033273")), asertReferenceBlock.difficulty);
        Assert.assertEquals(ACTIVATION_BLOCK_TIME, asertReferenceBlock.blockTime);

        TOTAL_LOOKUP_COUNT.addAndGet(context.getLookupCount());
        TOTAL_MTP_CALCULATION_COUNT.addAndGet(context.getMedianTimePastCalculationCount());
    }
}

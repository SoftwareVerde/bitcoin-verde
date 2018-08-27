package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.MutableBlockHeader;
import org.junit.Assert;
import org.junit.Test;

public class MutableMedianBlockTimeTests {
    public static class FakeBlockHeader extends MutableBlockHeader {
        private final Long _timestamp;

        public FakeBlockHeader(final Long timestamp) {
            _timestamp = timestamp;
        }

        @Override
        public Long getTimestamp() {
            return _timestamp;
        }
    }

    @Test
    public void should_create_valid_subset_from_the_most_recent_blocks() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 12; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader((long) i));
        }

        // Action
        final MedianBlockTime subsetMedianBlockTime = medianBlockTime.subset(1);

        // Assert
        Assert.assertEquals(11L, subsetMedianBlockTime.getCurrentTimeInSeconds().longValue());
    }

    @Test
    public void should_create_valid_subset_from_the_most_recent_blocks_2() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 11; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader((long) i));
        }

        // Action
        final MedianBlockTime subsetMedianBlockTime = medianBlockTime.subset(5);

        // Assert
        Assert.assertEquals(8L, subsetMedianBlockTime.getCurrentTimeInSeconds().longValue());
    }

    @Test
    public void should_create_valid_subset_from_the_most_recent_blocks_3() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 11; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader(11L - i));
        }

        // Action
        final MedianBlockTime subsetMedianBlockTime = medianBlockTime.subset(5);

        // Assert
        Assert.assertEquals(3L, subsetMedianBlockTime.getCurrentTimeInSeconds().longValue());
    }

    @Test
    public void getBlockHeader_should_return_the_most_recent_block_for_index_0() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 11; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader((long) i));
        }

        // Action
        final BlockHeader blockHeader = medianBlockTime.getBlockHeader(0);

        // Assert
        Assert.assertEquals(10L, blockHeader.getTimestamp().longValue());
    }

    @Test
    public void getBlockHeader_should_return_the_most_recent_block_for_index_11() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 11; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader((long) i));
        }

        // Action
        final BlockHeader blockHeader = medianBlockTime.getBlockHeader(10);

        // Assert
        Assert.assertEquals(0L, blockHeader.getTimestamp().longValue());
    }

    @Test
    public void should_create_valid_subset_from_the_most_recent_blocks_when_more_blocks_have_been_added_than_max_capacity() {
        // Setup
        final MutableMedianBlockTime medianBlockTime = new MutableMedianBlockTime();
        for (int i = 0; i < 100; ++i) {
            medianBlockTime.addBlock(new FakeBlockHeader((long) i));
        }

        // Action
        final Long medianBlockTimeInSeconds = medianBlockTime.getCurrentTimeInSeconds();

        // Assert
        // 99 98 97 96 95 | 94 | 93 92 91 90 89
        Assert.assertEquals(94L, medianBlockTimeInSeconds.longValue());
    }
}

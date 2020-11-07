package com.softwareverde.bitcoin.test.fake.database;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MedianBlockTimeWithBlocks;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTimeTests;

public class FakeMedianBlockTimeWithBlocks implements MedianBlockTimeWithBlocks {

    @Override
    public MedianBlockTime subset(final Integer blockCount) {
        return MedianBlockTime.MAX_VALUE;
    }

    @Override
    public BlockHeader getBlockHeader(final Integer indexFromTip) {
        return new MutableMedianBlockTimeTests.FakeBlockHeader(Long.MAX_VALUE);
    }

    @Override
    public ImmutableMedianBlockTime asConst() {
        return MedianBlockTime.MAX_VALUE;
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        return Long.MAX_VALUE;
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return Long.MAX_VALUE;
    }
}

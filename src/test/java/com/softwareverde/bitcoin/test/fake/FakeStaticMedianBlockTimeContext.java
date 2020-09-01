package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;

public class FakeStaticMedianBlockTimeContext implements MedianBlockTimeContext {
    public static final MedianBlockTimeContext MAX_MEDIAN_BLOCK_TIME = new FakeStaticMedianBlockTimeContext(MedianBlockTime.MAX_VALUE);

    protected final MedianBlockTime _medianBlockTime;

    public FakeStaticMedianBlockTimeContext(final MedianBlockTime medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        return _medianBlockTime;
    }
}

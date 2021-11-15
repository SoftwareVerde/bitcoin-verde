package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;

import java.util.HashMap;

public class FakeMedianBlockTimeContext implements MedianBlockTimeContext {
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<>();

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        if (! _medianBlockTimes.containsKey(blockHeight)) {
            throw new RuntimeException("Requested undefined MedianBlockTime for BlockHeight: " + blockHeight);
        }

        return _medianBlockTimes.get(blockHeight);
    }

    public void setMedianBlockTime(final Long blockHeight, final MedianBlockTime medianBlockTime) {
        _medianBlockTimes.put(blockHeight, medianBlockTime);
    }
}

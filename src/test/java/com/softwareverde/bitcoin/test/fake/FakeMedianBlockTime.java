package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.chain.time.ImmutableMedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;

public class FakeMedianBlockTime extends MutableMedianBlockTime {
    protected Long _medianBlockTime;

    public FakeMedianBlockTime() {
        _medianBlockTime = Long.MAX_VALUE;
    }

    public FakeMedianBlockTime(final Long medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }

    @Override
    public ImmutableMedianBlockTime asConst() {
        return ImmutableMedianBlockTime.fromSeconds(_medianBlockTime);
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        return _medianBlockTime;
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return (_medianBlockTime * 1000L);
    }

    public void setMedianBlockTime(final Long medianBlockTime) {
        _medianBlockTime = medianBlockTime;
    }
}

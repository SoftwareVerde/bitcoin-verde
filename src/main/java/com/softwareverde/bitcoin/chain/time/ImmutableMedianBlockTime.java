package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.constable.Const;

public class ImmutableMedianBlockTime implements MedianBlockTime, Const {
    protected final Long _medianBlockTimeInMilliseconds;

    public ImmutableMedianBlockTime(final Long medianBlockTimeInMilliseconds) {
        _medianBlockTimeInMilliseconds = medianBlockTimeInMilliseconds;
    }

    public ImmutableMedianBlockTime(final MedianBlockTime medianBlockTime) {
        _medianBlockTimeInMilliseconds = medianBlockTime.getCurrentTimeInMilliSeconds();
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        return (_medianBlockTimeInMilliseconds / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return _medianBlockTimeInMilliseconds;
    }

    @Override
    public ImmutableMedianBlockTime asConst() {
        return this;
    }
}

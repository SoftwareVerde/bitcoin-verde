package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.constable.Const;

public class ImmutableMedianBlockTime extends MedianBlockTimeCore implements MedianBlockTime, Const {
    public static ImmutableMedianBlockTime fromSeconds(final Long medianBlockTimeInSeconds) {
        return new ImmutableMedianBlockTime(medianBlockTimeInSeconds * 1000L);
    }

    public static ImmutableMedianBlockTime fromMilliseconds(final Long medianBlockTimeInMilliseconds) {
        return new ImmutableMedianBlockTime(medianBlockTimeInMilliseconds);
    }

    protected final Long _medianBlockTimeInMilliseconds;

    protected ImmutableMedianBlockTime(final Long medianBlockTimeInMilliseconds) {
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

package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.Time;

public interface MedianBlockTime extends Time, Constable<ImmutableMedianBlockTime> {
    Long GENESIS_BLOCK_TIMESTAMP = 1231006505L; // In seconds.
    ImmutableMedianBlockTime MAX_VALUE = new ImmutableMedianBlockTime(Long.MAX_VALUE);

    static MedianBlockTime fromSeconds(final Long medianBlockTimeInSeconds) {
        return ImmutableMedianBlockTime.fromSeconds(medianBlockTimeInSeconds);
    }

    static MedianBlockTime fromMilliseconds(final Long medianBlockTimeInMilliseconds) {
        return ImmutableMedianBlockTime.fromMilliseconds(medianBlockTimeInMilliseconds);
    }

    @Override
    ImmutableMedianBlockTime asConst();
}

abstract class MedianBlockTimeCore implements MedianBlockTime {
    @Override
    public String toString() {
        final Long currentTimeInSeconds = this.getCurrentTimeInSeconds();
        return Util.coalesce(currentTimeInSeconds).toString();
    }
}

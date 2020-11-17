package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.constable.Constable;
import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.Time;

public interface MedianBlockTime extends Time, Constable<ImmutableMedianBlockTime> {
    Integer BLOCK_COUNT = 11;
    Long GENESIS_BLOCK_TIMESTAMP = BitcoinConstants.getGenesisBlockTimestamp(); // In seconds.
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

    @Override
    public int hashCode() {
        final Long timeInSeconds = this.getCurrentTimeInSeconds();
        if (timeInSeconds == null) { return 0; }

        return timeInSeconds.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof MedianBlockTime)) { return false; }

        final MedianBlockTime medianBlockTime = (MedianBlockTime) object;

        final Long timeInSeconds0 = this.getCurrentTimeInSeconds();
        final Long timeInSeconds1 = medianBlockTime.getCurrentTimeInSeconds();

        return Util.areEqual(timeInSeconds0, timeInSeconds1);
    }
}

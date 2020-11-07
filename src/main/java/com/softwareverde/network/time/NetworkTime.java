package com.softwareverde.network.time;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.type.time.SystemTime;
import com.softwareverde.util.type.time.Time;

public interface NetworkTime extends Time, Constable<ImmutableNetworkTime> {
    NetworkTime MAX_VALUE = new ImmutableNetworkTime(Long.MAX_VALUE);

    static NetworkTime fromSeconds(final Long medianNetworkTimeInSeconds) {
        return ImmutableNetworkTime.fromSeconds(medianNetworkTimeInSeconds);
    }

    static NetworkTime fromMilliseconds(final Long medianNetworkTimeInMilliseconds) {
        return ImmutableNetworkTime.fromMilliseconds(medianNetworkTimeInMilliseconds);
    }

    static MutableNetworkTime fromSystemTime(final SystemTime systemTime) {
        return MutableNetworkTime.fromSystemTime(systemTime);
    }

    @Override
    ImmutableNetworkTime asConst();
}

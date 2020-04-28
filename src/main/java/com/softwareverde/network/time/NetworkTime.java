package com.softwareverde.network.time;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.type.time.Time;

public interface NetworkTime extends Time, Constable<ImmutableNetworkTime> {
    NetworkTime MAX_VALUE = new ImmutableNetworkTime(Long.MAX_VALUE);

    @Override
    ImmutableNetworkTime asConst();
}

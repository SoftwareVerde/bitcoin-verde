package com.softwareverde.network.time;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.type.time.Time;

public interface NetworkTime extends Time, Constable<ImmutableNetworkTime> {

    @Override
    ImmutableNetworkTime asConst();
}

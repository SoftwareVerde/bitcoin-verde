package com.softwareverde.bitcoin.server.network;

import com.softwareverde.constable.Constable;
import com.softwareverde.util.type.time.Time;

public interface NetworkTime extends Time, Constable<ImmutableNetworkTime> {

    @Override
    ImmutableNetworkTime asConst();
}

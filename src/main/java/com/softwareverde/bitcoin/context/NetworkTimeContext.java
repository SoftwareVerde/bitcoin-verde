package com.softwareverde.bitcoin.context;

import com.softwareverde.network.time.VolatileNetworkTime;

public interface NetworkTimeContext {
    VolatileNetworkTime getNetworkTime();
}

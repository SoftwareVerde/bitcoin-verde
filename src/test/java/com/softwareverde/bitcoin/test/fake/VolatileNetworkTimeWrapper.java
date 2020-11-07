package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.network.time.ImmutableNetworkTime;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.network.time.VolatileNetworkTime;

public class VolatileNetworkTimeWrapper implements VolatileNetworkTime {
    public static VolatileNetworkTimeWrapper wrap(final NetworkTime networkTime) {
        if (networkTime instanceof VolatileNetworkTimeWrapper) {
            return (VolatileNetworkTimeWrapper) networkTime;
        }

        return new VolatileNetworkTimeWrapper(networkTime);
    }

    protected final NetworkTime _networkTime;

    public VolatileNetworkTimeWrapper(final NetworkTime networkTime) {
        _networkTime = networkTime;
    }

    @Override
    public ImmutableNetworkTime asConst() {
        return _networkTime.asConst();
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        return _networkTime.getCurrentTimeInSeconds();
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return _networkTime.getCurrentTimeInMilliSeconds();
    }
}

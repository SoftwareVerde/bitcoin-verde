package com.softwareverde.network.time;

import com.softwareverde.constable.Const;

public class ImmutableNetworkTime implements NetworkTime, Const {
    public static ImmutableNetworkTime fromSeconds(final Long medianNetworkTimeInSeconds) {
        return new ImmutableNetworkTime(medianNetworkTimeInSeconds * 1000L);
    }

    public static ImmutableNetworkTime fromMilliseconds(final Long medianNetworkTimeInMilliseconds) {
        return new ImmutableNetworkTime(medianNetworkTimeInMilliseconds);
    }

    protected final Long _medianNetworkTimeInMilliseconds;

    protected ImmutableNetworkTime(final Long medianNetworkTimeInMilliseconds) {
        _medianNetworkTimeInMilliseconds = medianNetworkTimeInMilliseconds;
    }

    public ImmutableNetworkTime(final NetworkTime networkTime) {
        _medianNetworkTimeInMilliseconds = networkTime.getCurrentTimeInMilliSeconds();
    }

    @Override
    public Long getCurrentTimeInSeconds() {
        return (_medianNetworkTimeInMilliseconds / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return _medianNetworkTimeInMilliseconds;
    }

    @Override
    public ImmutableNetworkTime asConst() {
        return this;
    }
}

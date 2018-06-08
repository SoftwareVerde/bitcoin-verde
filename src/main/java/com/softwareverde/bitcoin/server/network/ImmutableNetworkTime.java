package com.softwareverde.bitcoin.server.network;

import com.softwareverde.constable.Const;

public class ImmutableNetworkTime implements NetworkTime, Const {
    protected final Long _medianNetworkTimeInMilliseconds;

    public ImmutableNetworkTime(final Long medianNetworkTimeInMilliseconds) {
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

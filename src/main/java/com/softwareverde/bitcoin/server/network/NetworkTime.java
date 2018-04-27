package com.softwareverde.bitcoin.server.network;

import com.softwareverde.bitcoin.type.time.Time;

public class NetworkTime implements Time {

    @Override
    public Long getCurrentTimeInSeconds() {
        return System.currentTimeMillis() / 1000L; // TODO
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return System.currentTimeMillis(); // TODO
    }
}

package com.softwareverde.bitcoin.type.time;

public class SystemTime implements Time {
    @Override
    public Long getCurrentTimeInSeconds() {
        return (System.currentTimeMillis() / 1000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return System.currentTimeMillis();
    }
}

package com.softwareverde.util.timer;

import com.softwareverde.util.Util;

public class NanoTimer {
    protected Long _startTime;
    protected Long _endTime;

    public void start() {
        _startTime = System.nanoTime();
    }

    public void stop() {
        _endTime = System.nanoTime();
    }

    public void reset() {
        _startTime = null;
        _endTime = null;
    }

    public Double getMillisecondsElapsed() {
        final Long now = System.nanoTime();
        final Long endTime = Util.coalesce(_endTime, now);
        final Long startTime = Util.coalesce(_startTime, now);
        return ((endTime - startTime) / 1000000D);
    }
}

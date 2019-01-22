package com.softwareverde.util.timer;

public class NanoTimer {
    protected long _startTime;
    protected long _endTime;

    public void start() {
        _startTime = System.nanoTime();
    }

    public void stop() {
        _endTime = System.nanoTime();
    }

    public Double getMillisecondsElapsed() {
        return (_endTime - _startTime) / 1000000D;
    }
}

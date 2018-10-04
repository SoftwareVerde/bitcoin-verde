package com.softwareverde.util.timer;

public class MilliTimer {
    protected long _startTime;
    protected long _endTime;

    public void start() {
        _startTime = System.currentTimeMillis();
    }

    public void stop() {
        _endTime = System.currentTimeMillis();
    }

    public Long getMillisecondsElapsed() {
        return (_endTime - _startTime);
    }
}

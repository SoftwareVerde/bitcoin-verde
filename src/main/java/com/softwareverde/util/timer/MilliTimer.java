package com.softwareverde.util.timer;

import com.softwareverde.util.Util;
import com.softwareverde.util.type.time.SystemTime;

public class MilliTimer {
    protected final SystemTime _systemTime = new SystemTime();

    protected Long _startTime;
    protected Long _endTime;

    public void start() {
        _startTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    public void stop() {
        _endTime = _systemTime.getCurrentTimeInMilliSeconds();
    }

    public void reset() {
        _startTime = null;
        _endTime = null;
    }

    public Long getMillisecondsElapsed() {
        final Long now = _systemTime.getCurrentTimeInMilliSeconds();
        final Long endTime = Util.coalesce(_endTime, now);
        final Long startTime = Util.coalesce(_startTime, now);
        return (endTime - startTime);
    }
}

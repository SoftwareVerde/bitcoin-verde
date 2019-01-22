package com.softwareverde.test.time;

import com.softwareverde.util.type.time.SystemTime;

public class FakeSystemTime extends SystemTime {
    protected Long _timeMs = 0L;

    @Override
    public Long getCurrentTimeInSeconds() {
        return (_timeMs / 1_000L);
    }

    @Override
    public Long getCurrentTimeInMilliSeconds() {
        return _timeMs;
    }

    public void advanceTimeInMilliseconds(final Long milliseconds) {
        _timeMs += milliseconds;
    }
}

package com.softwareverde.util;

import com.softwareverde.util.timer.NanoTimer;

public class TimedPromise<T> extends Promise<T> {
    protected final NanoTimer _nanoTimer = new NanoTimer();

    public TimedPromise() {
        _nanoTimer.start();
    }

    public TimedPromise(final T value) {
        super(value);
        _nanoTimer.start();
        _nanoTimer.stop();
    }

    @Override
    public synchronized void setResult(final T result) {
        _nanoTimer.stop();
        super.setResult(result);
    }

    public Double getMsElapsed() {
        return _nanoTimer.getMillisecondsElapsed();
    }
}

package com.softwareverde.concurrent.service;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class PausableSleepyService extends GracefulSleepyService {
    private final AtomicInteger _pauseSemaphore = new AtomicInteger(0);
    private Boolean _hasSuppressedRun = false;

    @Override
    protected Boolean _shouldAbort() {
        return ( super._shouldAbort() || (_pauseSemaphore.get() > 0) );
    }

    protected abstract Boolean _execute();

    @Override
    protected final Boolean _run() {
        if (_pauseSemaphore.get() > 0) {
            _hasSuppressedRun = true;
            return false;
        }

        return _execute();
    }

    public synchronized void pause() {
        _pauseSemaphore.incrementAndGet();
    }

    public Boolean isPaused() {
        return (_pauseSemaphore.get() > 0);
    }

    public void resume() {
        final int lockCount = _pauseSemaphore.decrementAndGet();
        if (lockCount > 0) { return; }

        if (_hasSuppressedRun) {
            _hasSuppressedRun = false;
            this.wakeUp();
        }
    }
}

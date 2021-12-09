package com.softwareverde.concurrent.service;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class LockingSleepyService extends GracefulSleepyService {
    protected final AtomicInteger _lockSemaphore = new AtomicInteger(0);
    protected Boolean _hasSuppressedRun = false;

    protected abstract Boolean _execute();

    @Override
    protected final Boolean _run() {
        if (_lockSemaphore.get() > 0) {
            _hasSuppressedRun = true;
            return false;
        }

        return _execute();
    }

    public synchronized void lock() {
        _lockSemaphore.incrementAndGet();
    }

    public Boolean isLocked() {
        return (_lockSemaphore.get() > 0);
    }

    public void unlock() {
        final int lockCount = _lockSemaphore.decrementAndGet();
        if (lockCount > 0) { return; }

        if (_hasSuppressedRun) {
            _hasSuppressedRun = false;
            this.wakeUp();
        }
    }
}

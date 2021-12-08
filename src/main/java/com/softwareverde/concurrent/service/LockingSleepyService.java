package com.softwareverde.concurrent.service;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class LockingSleepyService extends GracefulSleepyService {
    protected final AtomicInteger _lockSemaphore = new AtomicInteger(0);

    protected abstract Boolean _runSynchronized();

    @Override
    protected final synchronized Boolean _run() {
        if (_lockSemaphore.get() > 0) { return false; }

        return _runSynchronized();
    }

    public synchronized void lock() {
        _lockSemaphore.incrementAndGet();
    }

    public Boolean isLocked() {
        return (_lockSemaphore.get() > 0);
    }

    public void unlock() {
        _lockSemaphore.decrementAndGet();
    }
}

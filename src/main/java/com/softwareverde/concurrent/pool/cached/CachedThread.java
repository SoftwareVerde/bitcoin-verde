package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.concurrent.pool.ThreadDeathException;

public class CachedThread extends Thread {
    public static CachedThread newInstance(final CachedThreadPool threadPool) {
        return new CachedThread(new CachedThreadRunnable(threadPool), threadPool);
    }

    protected final CachedThreadRunnable _coreRunnable;
    protected final CachedThreadPool _threadPool;

    protected CachedThread(final CachedThreadRunnable cachedThreadRunnable, final CachedThreadPool threadPool) {
        super(cachedThreadRunnable);

        _coreRunnable = cachedThreadRunnable;
        _threadPool = threadPool;
    }

    public void execute(final Runnable runnable) throws InterruptedException, ThreadDeathException {
        _coreRunnable.execute(runnable);
    }

    public Boolean isIdle() {
        return _coreRunnable.isIdle();
    }

    public Long getTaskTime() {
        return _coreRunnable.getTaskTime();
    }

    public Long getIdleTime() {
        return _coreRunnable.getIdleTime();
    }

    public void dieWhenDone() {
        _coreRunnable.dieWhenDone();
    }
}

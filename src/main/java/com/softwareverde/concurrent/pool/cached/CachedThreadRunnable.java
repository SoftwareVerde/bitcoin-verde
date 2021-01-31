package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.concurrent.pool.ThreadDeathException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedThreadRunnable implements Runnable {
    protected static final AtomicInteger THREAD_COUNT = new AtomicInteger(0);

    protected final AtomicBoolean _isDying = new AtomicBoolean(false);
    protected final SynchronousQueue<Runnable> _task = new SynchronousQueue<>();
    protected final CachedThreadPool _threadPool;

    protected NanoTimer _idleTimer;
    protected NanoTimer _taskTimer;

    public CachedThreadRunnable(final CachedThreadPool threadPool) {
        _threadPool = threadPool;

        THREAD_COUNT.incrementAndGet();
    }

    public void execute(final Runnable runnable) throws InterruptedException, ThreadDeathException {
        final Thread thread = Thread.currentThread();

        try {
            if (_isDying.get()) {
                throw new ThreadDeathException();
            }
            _task.put(runnable);
        }
        catch (final InterruptedException exception) {
            thread.interrupt();
            if (_isDying.get()) {
                throw new ThreadDeathException();
            }

            throw exception;
        }
    }

    @Override
    public void run() {
        final Thread thread = Thread.currentThread();

        try {
            while ( (! thread.isInterrupted()) && (! _isDying.get()) ) {
                final Runnable task = _task.take();

                synchronized (this) {
                    try {
                        _taskTimer = new NanoTimer();
                        _taskTimer.start();
                        task.run();
                    }
                    catch (final Exception exception) {
                        Logger.debug(exception);
                    }
                    finally {
                        _threadPool.returnThread((CachedThread) thread);
                        _taskTimer = null;

                        _idleTimer = new NanoTimer();
                        _idleTimer.start();
                    }
                }
            }
        }
        catch (final InterruptedException exception) {
            // Let the thread shutdown...
        }
        finally {
            THREAD_COUNT.decrementAndGet();

            _isDying.set(true);
            thread.interrupt(); // Alert any pending pending calls to execute that the thread has died.

            _idleTimer = null;
            _taskTimer = null;
            _threadPool.notifyThreadDeath((CachedThread) thread);
        }
    }

    public Boolean isIdle() {
        return (_taskTimer == null);
    }

    public Long getTaskTime() {
        final NanoTimer taskTimer = _taskTimer;
        if (taskTimer == null) { return 0L; }

        final Double msElapsed = taskTimer.getMillisecondsElapsed();
        return msElapsed.longValue();
    }

    public Long getIdleTime() {
        final NanoTimer idleTimer = _idleTimer;
        if (idleTimer == null) { return 0L; }

        final Double msElapsed = idleTimer.getMillisecondsElapsed();
        return msElapsed.longValue();
    }

    public void dieWhenDone() {
        _isDying.set(true);
    }
}

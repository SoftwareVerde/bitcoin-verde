package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.concurrent.pool.MutableThreadPool;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * CachedThreadPool is a MutableThreadPool that keeps threads cached until they've been idle for the configured duration.
 *  Threads that execute for longer than the longRunningThreshold are migrated out of the thread cache, freeing a new
 *  slot; migrated threads are not terminated early.
 *
 * Required Properties:
 *  - All tasks must eventually be executed.
 *  - Each task must run in a separate thread from its enqueuer in order to prevent deadlocks.
 *  - The queue should be bounded to avoid OOM.
 *  - Not every task should spawn its own thread (otherwise why bother with a threadpool).
 *  - Cached threads should eventually be reaped once inactive for a while.
 *  - A minimum number of threads should stay alive and avoid reaping.
 *
 * Optional Requirements:
 *  - A long-living task is transferred out of the cached thread set and allows an extra thread to be created.
 *      Upon termination of the long-living task, the thread is discarded.
 */

public class CachedThreadPool implements MutableThreadPool {
    public static Integer getAliveThreadCount() {
        return CachedThreadRunnable.THREAD_COUNT.get();
    }

    public static final Long DEFAULT_LONG_RUNNING_THRESHOLD = 60000L;
    public static final CachedThreadFactory DEFAULT_THREAD_FACTORY = new CachedThreadFactory() {
        @Override
        public CachedThread newThread(final CachedThreadPool cachedThreadPool) {
            final CachedThread cachedThread = CachedThread.newInstance(cachedThreadPool);
            cachedThread.setName("CachedThreadPool - Worker Thread " + cachedThread.getId());
            cachedThread.setDaemon(false);
            // cachedThread.setPriority(Thread.NORM_PRIORITY);
            cachedThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(final Thread thread, final Throwable exception) {
                    Logger.warn("Uncaught exception within CachedThreadPool.", exception);
                }
            });
            return cachedThread;
        }
    };

    public static CachedThreadFactory newThreadFactoryWithPriority(final Integer threadPriority) {
        return new CachedThreadFactory() {
            @Override
            public CachedThread newThread(final CachedThreadPool cachedThreadPool) {
                final CachedThread cachedThread = DEFAULT_THREAD_FACTORY.newThread(cachedThreadPool);
                cachedThread.setPriority(threadPriority);
                return cachedThread;
            }
        };
    }

    protected final CachedThreadFactory _threadFactory;
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(true);
    protected final ConcurrentLinkedDeque<CachedThread> _cachedThreads = new ConcurrentLinkedDeque<>();
    protected final ConcurrentHashMap<Long, CachedThread> _runningThreads = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<Long, CachedThread> _longRunningThreads = new ConcurrentHashMap<>();
    protected final AtomicInteger _pendingExecuteCount = new AtomicInteger(0);
    protected final SynchronousQueue<Runnable> _pendingExecutes = new SynchronousQueue<>();
    protected final LinkedBlockingQueue<Runnable> _dispatchQueue = new LinkedBlockingQueue<>();
    protected final Long _longRunningThreshold;

    protected final Long _maxIdleTime;
    protected final Integer _maxThreadCount;
    protected Thread _workerMaintenanceThread;
    protected Thread _dispatchThread;

    protected void returnThread(final CachedThread cachedThread) {
        if (_isShutdown.get()) {
            cachedThread.dieWhenDone();
            cachedThread.interrupt();
            return;
        }

        if (cachedThread.isAlive()) {
            _cachedThreads.add(cachedThread);
        }

        final Runnable pendingExecute = _pendingExecutes.poll();
        if (pendingExecute != null) {
            _pendingExecuteCount.decrementAndGet();
            _dispatchQueue.add(new Runnable() {
                @Override
                public void run() {
                    CachedThreadPool.this.execute(pendingExecute);
                }
            });

            synchronized (_dispatchQueue) {
                _dispatchQueue.notifyAll();
            }
        }
    }

    protected void notifyThreadDeath(final CachedThread cachedThread) {
        if (_isShutdown.get()) {
            cachedThread.dieWhenDone();
            cachedThread.interrupt();
            return;
        }

        final Long threadId = cachedThread.getId();

        _runningThreads.remove(threadId);
        _longRunningThreads.remove(threadId);

        final Runnable pendingExecute = _pendingExecutes.poll();
        if (pendingExecute != null) {
            _pendingExecuteCount.decrementAndGet();
            _dispatchQueue.add(new Runnable() {
                @Override
                public void run() {
                    CachedThreadPool.this.execute(pendingExecute);
                }
            });

            synchronized (_dispatchQueue) {
                _dispatchQueue.notifyAll();
            }
        }
    }

    public CachedThreadPool(final Integer maxThreadCount, final Long maxIdleTime) {
        this(maxThreadCount, maxIdleTime, DEFAULT_LONG_RUNNING_THRESHOLD);
    }

    public CachedThreadPool(final Integer maxThreadCount, final Long maxIdleTime, final Long longRunningThresholdMs) {
        this(maxThreadCount, maxIdleTime, longRunningThresholdMs, DEFAULT_THREAD_FACTORY);
    }

    public CachedThreadPool(final Integer maxThreadCount, final Long maxIdleTimeMs, final Long longRunningThresholdMs, final CachedThreadFactory threadFactory) {
        _threadFactory = Util.coalesce(threadFactory, DEFAULT_THREAD_FACTORY);
        _maxThreadCount = maxThreadCount;
        _maxIdleTime = maxIdleTimeMs;
        _longRunningThreshold = longRunningThresholdMs;
    }

    @Override
    public void start() {
        final boolean wasShutdown = _isShutdown.compareAndSet(true, false);
        if (! wasShutdown) { return; } // Redundant call to start.

        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.warn(exception);
                CachedThreadPool.this.stop();
            }
        };

        _dispatchThread = new Thread(new DispatchRunnable(_dispatchQueue));
        _dispatchThread.setName("CachedThreadPool Dispatch Thread");
        _dispatchThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);

        _workerMaintenanceThread = new Thread(new WorkerMaintenanceRunnable(this));
        _workerMaintenanceThread.setName("CachedThreadPool Maintenance Thread");
        _workerMaintenanceThread.setUncaughtExceptionHandler(uncaughtExceptionHandler);

        _dispatchThread.start();
        _workerMaintenanceThread.start();
    }

    @Override
    public void execute(final Runnable runnable) {
        if (_isShutdown.get()) { throw new RuntimeException("ThreadPool has shut down."); }

        { // Attempt to reuse a cached thread...
            CachedThread cachedThread;
            while ((cachedThread = _cachedThreads.poll()) != null) {
                try {
                    cachedThread.execute(runnable);
                    return;
                }
                catch (final Exception exception) { }
            }
        }

        synchronized (this) { // Create a new thread...
            if (_runningThreads.size() < _maxThreadCount) {
                final CachedThread cachedThread = _threadFactory.newThread(this);
                _runningThreads.put(cachedThread.getId(), cachedThread);
                if (! cachedThread.isAlive()) {
                    cachedThread.start();
                }

                try {
                    cachedThread.execute(runnable);
                }
                catch (final Exception exception) {
                    throw new RuntimeException(exception);
                }
                return;
            }
        }

        try {
            _pendingExecuteCount.incrementAndGet();
            _pendingExecutes.put(runnable);
        }
        catch (final InterruptedException exception) {
            final Thread thread = Thread.currentThread();
            thread.interrupt();
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void stop() {
        _isShutdown.set(true);

        for (final CachedThread cachedThread : _longRunningThreads.values()) {
            cachedThread.dieWhenDone();
            cachedThread.interrupt();
        }

        for (final CachedThread cachedThread : _runningThreads.values()) {
            cachedThread.dieWhenDone();
            cachedThread.interrupt();
        }

        for (final CachedThread cachedThread : _cachedThreads) {
            cachedThread.dieWhenDone();
            cachedThread.interrupt();
        }

        _dispatchThread.interrupt();
        _workerMaintenanceThread.interrupt();

        try {
            _dispatchThread.join(1000L);
            _workerMaintenanceThread.join(1000L);

            for (final CachedThread cachedThread : _longRunningThreads.values()) {
                cachedThread.join(1000L);
            }

            for (final CachedThread cachedThread : _runningThreads.values()) {
                cachedThread.join(1000L);
            }

            for (final CachedThread cachedThread : _cachedThreads) {
                cachedThread.join(1000L);
            }
        }
        catch (final Exception exception) { }
    }

    public Long getLongRunningThreshold() {
        return _longRunningThreshold;
    }

    public Integer getMaxThreadCount() {
        return _maxThreadCount;
    }

    public Integer getActiveThreadCount() {
        int activeCount = 0;
        for (final CachedThread cachedThread : _runningThreads.values()) {
            if ( (cachedThread != null) && (! cachedThread.isIdle()) ) {
                activeCount += 1;
            }
        }
        return (activeCount + _longRunningThreads.size());
    }

    public Integer getInactiveThreadCount() {
        int inactiveCount = 0;
        for (final CachedThread cachedThread : _runningThreads.values()) {
            if ( (cachedThread != null) && (cachedThread.isIdle()) ) {
                inactiveCount += 1;
            }
        }
        return inactiveCount;
    }

    public Integer getLongRunningThreadCount() {
        return _longRunningThreads.size();
    }

    public Integer getQueueCount() {
        return _pendingExecutes.size();
    }
}

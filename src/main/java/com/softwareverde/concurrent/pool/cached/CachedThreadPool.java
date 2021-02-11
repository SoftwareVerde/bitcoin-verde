package com.softwareverde.concurrent.pool.cached;

import com.softwareverde.concurrent.pool.MutableThreadPool;
import com.softwareverde.logging.Logger;

import java.util.LinkedList;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CachedThreadPool implements MutableThreadPool {
    public static final ThreadFactory DEFAULT_THREAD_FACTORY = new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable coreRunnable) {
            final Thread cachedThread = new Thread(coreRunnable);
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

    public static ThreadFactory newThreadFactoryWithPriority(final Integer threadPriority) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable coreRunnable) {
                final Thread cachedThread = DEFAULT_THREAD_FACTORY.newThread(coreRunnable);
                cachedThread.setPriority(threadPriority);
                return cachedThread;
            }
        };
    }

    protected static Integer getDefaultMinThreadCount(final Integer maxThreadCount) {
        final Runtime runtime = Runtime.getRuntime();
        final int processorCount = runtime.availableProcessors();
        return Math.min(maxThreadCount, (processorCount * 2));
    }

    protected final AtomicBoolean _isShutdown = new AtomicBoolean(false);
    protected final Integer _maxThreadCount;
    protected final ThreadPoolExecutor _threadPoolExecutor;
    protected final AtomicInteger _activeTaskCount = new AtomicInteger(0);
    protected final AtomicInteger _taskQueueCount = new AtomicInteger(0);
    protected final LinkedList<Runnable> _taskQueue = new LinkedList<>();

    protected void _beforeTaskExecuted(final Runnable runnable) { }
    protected void _afterTaskExecuted(final Runnable runnable) { }
    protected void _afterTaskQueued(final Runnable runnable) { }

    public CachedThreadPool(final Integer maxThreadCount, final Long maxIdleTime) {
        this(maxThreadCount, maxIdleTime, DEFAULT_THREAD_FACTORY);
    }

    public CachedThreadPool(final Integer maxThreadCount, final Long maxIdleTimeMs, final ThreadFactory threadFactory) {
        this(CachedThreadPool.getDefaultMinThreadCount(maxThreadCount), maxThreadCount, maxIdleTimeMs, threadFactory);
    }

    /**
     * CachedThreadPool maintains minThreadCount at all times.
     * Tasks submitted that would exceed minThreadCount create new threads that are cached for minIdleTimeMs.
     * CachedThreadPool never exceeds maxThreadCount, tasks submitted that would exceed maxThreadCount are queued.
     *  The internal task queue is unbounded.
     */
    public CachedThreadPool(final Integer minThreadCount, final Integer maxThreadCount, final Long maxIdleTimeMs, final ThreadFactory threadFactory) {
        _maxThreadCount = maxThreadCount;
        _threadPoolExecutor = new ThreadPoolExecutor(minThreadCount, Integer.MAX_VALUE, maxIdleTimeMs, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>()) {
            @Override
            protected void beforeExecute(final Thread thread, final Runnable runnable) {
                super.beforeExecute(thread, runnable);
            }

            @Override
            protected void afterExecute(final Runnable finishedRunnable, final Throwable exception) {
                synchronized (CachedThreadPool.this) {
                    _afterTaskExecuted(finishedRunnable);

                    final Runnable runnable = _taskQueue.pollFirst();
                    if (runnable != null) {
                        _beforeTaskExecuted(runnable);

                        _taskQueueCount.decrementAndGet();
                        _threadPoolExecutor.execute(runnable);
                    }
                    else {
                        _activeTaskCount.decrementAndGet();
                    }
                }

                super.afterExecute(finishedRunnable, exception);
            }
        };
        _threadPoolExecutor.setThreadFactory(threadFactory);
        _threadPoolExecutor.allowCoreThreadTimeOut(false);
    }

    @Override
    public void start() {
        _threadPoolExecutor.prestartAllCoreThreads();
    }

    /**
     * Delegates the execution of runnable to one of the cached threads, or spawns a new thread if there are fewer than maxThreadCount.
     * Enqueues the runnable for execution if the number of active threads meets maxThreadCount.
     */
    @Override
    public synchronized void execute(final Runnable runnable) {
        if (_isShutdown.get()) { throw new RuntimeException("Attempted to invoke execute after ThreadPool shutdown."); }

        final int currentlyBusyThreadCount = _activeTaskCount.get();
        if (currentlyBusyThreadCount >= _maxThreadCount) {
            _taskQueue.addLast(runnable);
            _taskQueueCount.incrementAndGet();

            _afterTaskQueued(runnable);
        }
        else {
            _beforeTaskExecuted(runnable);

            _activeTaskCount.incrementAndGet();
            _threadPoolExecutor.execute(runnable);
        }
    }

    @Override
    public void stop() {
        final boolean wasAlive = _isShutdown.compareAndSet(false, true);
        if (! wasAlive) { return; }

        final Thread thread = Thread.currentThread();
        try {
            while (_taskQueueCount.get() != 0) {
                Thread.sleep(100L);
            }
        }
        catch (final InterruptedException interruptedException) {
            thread.interrupt();
        }
        finally {
            _threadPoolExecutor.shutdown();

            try {
                _threadPoolExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
            catch (final InterruptedException exception) {
                thread.interrupt();
            }
        }
    }

    public Integer getMaxThreadCount() {
        return _maxThreadCount;
    }

    /**
     * Returns the number of threads that are currently in the pool.
     */
    public Integer getThreadCount() {
        return _threadPoolExecutor.getPoolSize();
    }

    /**
     * Returns the number of tasks that are currently being executed.
     */
    public Integer getActiveThreadCount() {
        return _activeTaskCount.get();
    }

    /**
     * Returns the number of threads that are currently alive but not executing a task.
     */
    public Integer getIdleThreadCount() {
        final int activeThreadCount = _activeTaskCount.get();
        final int totalThreadCount = _threadPoolExecutor.getPoolSize();
        return Math.max(0, (totalThreadCount - activeThreadCount));
    }

    /**
     * Returns the number of tasks that are pending in the thread pool, but are not currently being executed.
     */
    public Integer getQueuedTaskCount() {
        return _taskQueueCount.get();
    }
}

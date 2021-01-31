package com.softwareverde.concurrent.pool;

import com.softwareverde.logging.Logger;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorThreadPool implements MutableThreadPool {
    public static ExecutorThreadPool newCachedThreadInstance(final Integer maxThreadCount, final Long threadKeepAliveMilliseconds) {
        final Runtime runtime = Runtime.getRuntime();
        final int processorCount = runtime.availableProcessors();
        final Integer desiredThreadCount = Math.min(processorCount * 2, maxThreadCount);

        final BlockingQueue<Runnable> blockingQueue = new SynchronousQueue<>();
        final RejectedExecutionHandler blockUntilAvailablePolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        return new ExecutorThreadPool(desiredThreadCount, maxThreadCount, threadKeepAliveMilliseconds, true, blockingQueue, blockUntilAvailablePolicy);
    }

    public static ExecutorThreadPool newFixedThreadInstance(final Integer threadCount) {
        final BlockingQueue<Runnable> blockingQueue = new SynchronousQueue<>();
        final RejectedExecutionHandler blockUntilAvailablePolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        return new ExecutorThreadPool(threadCount, threadCount, 0L, false, blockingQueue, blockUntilAvailablePolicy);
    }

    protected final AtomicInteger _nextThreadId = new AtomicInteger(0);
    protected final BlockingQueue<Runnable> _queue;
    protected final Integer _minThreadCount;
    protected final Integer _maxThreadCount;
    protected final Boolean _shouldTimeoutCoreThreads;
    protected final Long _threadKeepAliveMilliseconds;
    protected final RejectedExecutionHandler _rejectedExecutionHandler;
    protected ThreadPoolExecutor _executorService;
    protected Runnable _shutdownCallback;
    protected Integer _threadPriority = Thread.NORM_PRIORITY;

    protected void _configureThread(final Integer nextThreadId, final Thread thread) {
        thread.setName("ExecutorThreadPool - " + nextThreadId);
        thread.setDaemon(false);
        thread.setPriority(_threadPriority);
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.error("Uncaught exception in Thread Pool.", throwable);
                }
                catch (final Throwable exception) { }

                if (throwable instanceof Error) {
                    final Runnable shutdownCallback = _shutdownCallback;
                    if (shutdownCallback != null) {
                        try {
                            shutdownCallback.run();
                        }
                        catch (final Throwable shutdownError) { }
                    }
                }
            }
        });
    }

    protected ThreadFactory _createThreadFactory() {
        return new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final int nextThreadId = _nextThreadId.incrementAndGet();
                final Thread thread = new Thread(runnable);
                _configureThread(nextThreadId, thread);
                return thread;
            }
        };
    }

    protected ThreadPoolExecutor _createExecutorService() {
        if (_maxThreadCount < 1) { return null; }

        final ThreadFactory threadFactory = _createThreadFactory();
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(_minThreadCount, _maxThreadCount, _threadKeepAliveMilliseconds, TimeUnit.MILLISECONDS, _queue, threadFactory);

        threadPoolExecutor.allowCoreThreadTimeOut(_shouldTimeoutCoreThreads);

        if (_rejectedExecutionHandler != null) {
            threadPoolExecutor.setRejectedExecutionHandler(_rejectedExecutionHandler);
        }

        return threadPoolExecutor;
    }

    public ExecutorThreadPool(final Integer minThreadCount, final Integer maxThreadCount, final Long threadKeepAliveMilliseconds, final Boolean shouldTimeoutCoreThreads, final BlockingQueue<Runnable> blockingQueue, final RejectedExecutionHandler rejectedExecutionHandler) {
        _queue = blockingQueue;
        _minThreadCount = minThreadCount;
        _maxThreadCount = maxThreadCount;
        _shouldTimeoutCoreThreads = shouldTimeoutCoreThreads;
        _threadKeepAliveMilliseconds = threadKeepAliveMilliseconds;
        _rejectedExecutionHandler = rejectedExecutionHandler;
    }

    public void setThreadPriority(final Integer threadPriority) {
        _threadPriority = threadPriority;
    }

    public Integer getThreadPriority() {
        return _threadPriority;
    }

    /**
     * Queues the Runnable to the ThreadPool.
     * If ThreadPool::stop has been invoked without a subsequent call to ThreadPool::start, the Runnable is dropped.
     */
    @Override
    public void execute(final Runnable runnable) {
        final ExecutorService executorService = _executorService;
        if (executorService == null) { return; }

        try {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        runnable.run();
                    }
                    catch (final Exception exception) {
                        Logger.warn(exception);
                    }
                }
            });
        }
        catch (final RejectedExecutionException exception) {
            Logger.warn(exception);
            // Execution rejected due to shutdown race condition...
        }
    }

    /**
     * Resets the ThreadPool after a call to ThreadPool::shutdown.
     * Invoking ThreadPool::start without invoking ThreadPool::stop is safe but does nothing.
     *  It is not necessary to invoke ThreadPool::start after instantiation.
     */
    @Override
    public void start() {
        if (_executorService != null) { return; }

        _executorService = _createExecutorService();
    }

    /**
     * Immediately interrupts all pending threads and awaits termination for up to 1 minute.
     *  Any calls to ThreadPool::execute after invoking ThreadPool::stop are ignored until ThreadPool::start is called.
     */
    @Override
    public void stop() {
        final ExecutorService executorService = _executorService;
        if (executorService == null) { return; }

        _executorService = null;

        executorService.shutdown(); // Disable new tasks from being submitted...
        try {
            // Wait a while for existing tasks to terminate...
            if (! executorService.awaitTermination(30, TimeUnit.SECONDS)) {
            executorService.shutdownNow(); // Cancel currently executing tasks...
            // Wait a while for tasks to respond to being cancelled...
            if (! executorService.awaitTermination(30, TimeUnit.SECONDS))
                Logger.warn("ThreadPool did not exit cleanly.");
            }
        }
        catch (final InterruptedException exception) {
            executorService.shutdownNow(); // Re-cancel if current thread also interrupted...
            Thread.currentThread().interrupt(); // Preserve interrupt status...
        }
    }

    /**
     * Sets the callback that is executed when an java.lang.Error is thrown.
     *  This procedure is not invoked if a thread encounters a recoverable Exception (i.e. An uncaught RuntimeException).
     *  The shutdownCallback should attempt to gracefully terminate the application and not attempt to "recover";
     *  System.exit() should not be executed within shutdownCallback.
     */
    public void setShutdownCallback(final Runnable shutdownCallback) {
        _shutdownCallback = shutdownCallback;
    }

    /**
     * Returns the current number of items in the ThreadPool queue.
     */
    public Integer getQueueCount() {
        return _queue.size();
    }

    public Integer getActiveThreadCount() {
        if (_executorService == null) { return 0; }

        return _executorService.getPoolSize();
    }

    public Integer getMaxThreadCount() {
        return _maxThreadCount;
    }
}

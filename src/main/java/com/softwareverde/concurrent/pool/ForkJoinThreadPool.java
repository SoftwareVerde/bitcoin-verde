package com.softwareverde.concurrent.pool;

import com.softwareverde.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ForkJoinThreadPool implements ThreadPool {
    protected final AtomicInteger _nextThreadId = new AtomicInteger(0);
    protected final Integer _maxConcurrentThreadCount;
    protected ForkJoinPool _executorService;
    protected Runnable _shutdownCallback;
    protected Integer _threadPriority = Thread.NORM_PRIORITY;

    protected Thread.UncaughtExceptionHandler _createExceptionHandler() {
        return new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable throwable) {
                try {
                    Logger.error("Uncaught exception in ForkJoin Thread Pool.", throwable);
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
        };
    }

    protected ForkJoinPool.ForkJoinWorkerThreadFactory _createThreadFactory(final Thread.UncaughtExceptionHandler exceptionHandler) {
        return new ForkJoinPool.ForkJoinWorkerThreadFactory() {
            @Override
            public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
                final int nextThreadId = _nextThreadId.incrementAndGet();

                final ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) { };

                thread.setName("ForkJoinThread - " + nextThreadId);
                thread.setDaemon(false);
                thread.setPriority(_threadPriority);
                thread.setUncaughtExceptionHandler(exceptionHandler);

                return thread;
            }
        };
    }

    protected ForkJoinPool _createExecutorService() {
        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = _createExceptionHandler();
        final ForkJoinPool.ForkJoinWorkerThreadFactory threadFactory = _createThreadFactory(uncaughtExceptionHandler);

        return new ForkJoinPool(_maxConcurrentThreadCount, threadFactory, uncaughtExceptionHandler, true);
    }

    public ForkJoinThreadPool() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ForkJoinThreadPool(final Integer maxConcurrentThreadCount) {
        _maxConcurrentThreadCount = maxConcurrentThreadCount;
        _executorService = _createExecutorService();
    }

    @Override
    public void execute(final Runnable runnable) {
        final ExecutorService executorService = _executorService;
        if (executorService == null) { return; }

        _executorService.execute(runnable);
    }

    public void setThreadPriority(final Integer threadPriority) {
        _threadPriority = threadPriority;
    }

    public Integer getThreadPriority() {
        return _threadPriority;
    }

    /**
     * Resets the ThreadPool after a call to ThreadPool::shutdown.
     * Invoking ThreadPool::start without invoking ThreadPool::stop is safe but does nothing.
     *  It is not necessary to invoke ThreadPool::start after instantiation.
     */
    public void start() {
        if (_executorService != null) { return; }

        _executorService = _createExecutorService();
    }

    /**
     * Immediately interrupts all pending threads and awaits termination for up to 1 minute.
     *  Any calls to ThreadPool::execute after invoking ThreadPool::stop are ignored until ThreadPool::start is called.
     */
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
        final ForkJoinPool executorService = _executorService;
        if (executorService == null) { return 0; }

        return executorService.getQueuedSubmissionCount();
    }

    public Integer getActiveThreadCount() {
        final ForkJoinPool executorService = _executorService;
        if (executorService == null) { return 0; }

        return executorService.getActiveThreadCount();
    }

    public Integer getMaxThreadCount() {
        final ForkJoinPool executorService = _executorService;
        if (executorService == null) { return 0; }

        return executorService.getPoolSize();
    }
}

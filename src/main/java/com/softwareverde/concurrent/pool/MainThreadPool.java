package com.softwareverde.concurrent.pool;

import com.softwareverde.io.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MainThreadPool implements ThreadPool {
    protected final LinkedBlockingQueue<Runnable> _queue;
    protected final Integer _maxThreadCount;
    protected final Long _threadKeepAliveMilliseconds;
    protected ThreadPoolExecutor _executorService;

    final AtomicInteger _nextThreadId = new AtomicInteger(0);

    protected ThreadPoolExecutor _createExecutorService() {
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(_maxThreadCount, _maxThreadCount, _threadKeepAliveMilliseconds, TimeUnit.MILLISECONDS, _queue, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                final int nextThreadId = _nextThreadId.incrementAndGet();

                final Thread thread = new Thread(runnable);
                thread.setName("MainThreadPool - " + nextThreadId);
                thread.setDaemon(false);
                return thread;
            }
        });

        threadPoolExecutor.allowCoreThreadTimeOut(true);

        return threadPoolExecutor;
    }

    public MainThreadPool(final Integer maxThreadCount, final Long threadKeepAliveMilliseconds) {
        _queue = new LinkedBlockingQueue<Runnable>();
        _maxThreadCount = maxThreadCount;
        _threadKeepAliveMilliseconds = threadKeepAliveMilliseconds;
        _executorService = _createExecutorService();
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
                        Logger.log(exception);
                    }
                }
            });
        }
        catch (final RejectedExecutionException exception) {
            Logger.log(exception);
            // Execution rejected due to shutdown race condition...
        }
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
                Logger.log("NOTICE: ThreadPool did not exit cleanly.");
            }
        }
        catch (final InterruptedException exception) {
            executorService.shutdownNow(); // Re-cancel if current thread also interrupted...
            Thread.currentThread().interrupt(); // Preserve interrupt status...
        }
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

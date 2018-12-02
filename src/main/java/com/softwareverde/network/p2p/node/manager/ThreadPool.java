package com.softwareverde.network.p2p.node.manager;

import java.util.concurrent.*;

public class ThreadPool {
    protected final LinkedBlockingQueue<Runnable> _queue;
    protected final Integer _minThreadCount;
    protected final Integer _maxThreadCount;
    protected final Long _threadKeepAliveMilliseconds;
    protected ExecutorService _executorService;

    public ThreadPool(final Integer minThreadCount, final Integer maxThreadCount, final Long threadKeepAliveMilliseconds) {
        _queue = new LinkedBlockingQueue<Runnable>();
        _minThreadCount = minThreadCount;
        _maxThreadCount = maxThreadCount;
        _threadKeepAliveMilliseconds = threadKeepAliveMilliseconds;

        _executorService = new ThreadPoolExecutor(_minThreadCount, _maxThreadCount, _threadKeepAliveMilliseconds, TimeUnit.MILLISECONDS, _queue);
    }

    /**
     * Queues the Runnable to the ThreadPool.
     * If ThreadPool::stop has been invoked without a subsequent call to ThreadPool::start, the Runnable is dropped.
     */
    public void execute(final Runnable runnable) {
        final ExecutorService executorService = _executorService;
        if (executorService == null) { return; }

        try {
            executorService.submit(runnable);
        }
        catch (final RejectedExecutionException exception) {
            exception.printStackTrace();
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

        _executorService = new ThreadPoolExecutor(_minThreadCount, _maxThreadCount, _threadKeepAliveMilliseconds, TimeUnit.MILLISECONDS, _queue);
    }

    /**
     * Immediately interrupts all pending threads and awaits termination for 1 minute.
     *  Any calls to ThreadPool::execute after invoking ThreadPool::stop are ignored until ThreadPool::start is called.
     */
    public void stop() {
        final ExecutorService executorService = _executorService;
        if (executorService == null) { return; }

        _executorService = null;

        executorService.shutdownNow();
        try {
            executorService.awaitTermination(60, TimeUnit.SECONDS);
        }
        catch (final InterruptedException exception) { }
    }

    /**
     * Returns the current number of items in the ThreadPool queue.
     */
    public Integer getQueueSize() {
        return _queue.size();
    }
}

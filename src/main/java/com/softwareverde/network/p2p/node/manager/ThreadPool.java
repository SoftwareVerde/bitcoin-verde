package com.softwareverde.network.p2p.node.manager;

import java.util.LinkedList;
import java.util.concurrent.*;

public class ThreadPool {
    protected final LinkedBlockingQueue<Runnable> _queue;
    protected final ExecutorService _executorService;
    protected final LinkedList<Future> _futures = new LinkedList<Future>();

    protected void _cleanupFutures(final Boolean shouldBlockUntilDone) {
        while (! _futures.isEmpty()) {
            final Future future = _futures.peek();
            if (! future.isDone()) {
                if (! shouldBlockUntilDone) { break; }

                try {
                    future.get();
                }
                catch (final Exception exception) { break; }
            }

            _futures.remove();
        }
    }

    public ThreadPool(final Integer minThreadCount, final Integer maxThreadCount, final Long threadKeepAliveMilliseconds) {
        _queue = new LinkedBlockingQueue<Runnable>();
        _executorService = new ThreadPoolExecutor(minThreadCount, maxThreadCount, threadKeepAliveMilliseconds, TimeUnit.MILLISECONDS, _queue);
    }

    public void execute(final Runnable runnable) {
        synchronized (_futures) {
            _cleanupFutures(false);

            final Future future = _executorService.submit(runnable);
            _futures.add(future);
        }
    }

    public Boolean isIdle() {
        synchronized (_futures) {
            _cleanupFutures(false);
            return _futures.isEmpty();
        }
    }

    public void waitUntilIdle() {
        synchronized (_futures) {
            _cleanupFutures(true);
        }
    }

    public void abortAll() {
        synchronized (_futures) {
            _cleanupFutures(false);
            while (! _futures.isEmpty()) {
                final Future future = _futures.peek();
                future.cancel(true);
                _futures.remove();
            }
        }
    }
}

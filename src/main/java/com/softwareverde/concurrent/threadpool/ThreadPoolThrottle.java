package com.softwareverde.concurrent.threadpool;

import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ThreadPoolThrottle extends SleepyService implements ThreadPool {
    public static final Integer MAX_QUEUE_SIZE = 10000;

    protected final SystemTime _systemTime = new SystemTime();
    protected final ConcurrentLinkedQueue<Runnable> _queue = new ConcurrentLinkedQueue<Runnable>();
    protected final AtomicInteger _queueSize = new AtomicInteger(0);
    protected final ThreadPool _threadPool;
    protected final Integer _maxSubmissionsPerSecond;
    protected Long _lastLogStatement = 0L;
    protected final AtomicLong _droppedSubmissionsCount = new AtomicLong(0);

    public ThreadPoolThrottle(final Integer maxSubmissionsPerSecond, final ThreadPool threadPool) {
        _maxSubmissionsPerSecond = maxSubmissionsPerSecond;
        _threadPool = threadPool;
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final Runnable runnable = _queue.poll();
        if (runnable == null) { return false; }

        _queueSize.decrementAndGet();

        _threadPool.execute(runnable);

        try {
            Thread.sleep(1000L / _maxSubmissionsPerSecond);
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt(); // Preserve the interrupted status...
            return false;
        }

        // Log when the queue exceeds the max submissions per second...
        if (_queueSize.get() > _maxSubmissionsPerSecond) {
            // Only log once every 5 seconds to prevent spam...
            final Long now = System.currentTimeMillis();
            if (now - _lastLogStatement > 5000L) {
                Logger.warn("ThreadPoolThrottle is " + (_queueSize.get() / _maxSubmissionsPerSecond) + " seconds behind.");
                _lastLogStatement = now;
            }
        }

        return (_queueSize.get() > 0);
    }

    @Override
    protected void _onSleep() { }

    @Override
    public void execute(final Runnable runnable) {
        if (_queueSize.get() >= MAX_QUEUE_SIZE) {
            if (_droppedSubmissionsCount.get() % _maxSubmissionsPerSecond == 0) {
                Logger.warn("ThreadPoolThrottle: Exceeded max queue size. " + _droppedSubmissionsCount);
            }

            _droppedSubmissionsCount.incrementAndGet();
            return;
        }

        _queue.offer(runnable);
        _queueSize.incrementAndGet();

        this.wakeUp();
    }
}

package com.softwareverde.concurrent.pool;

import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.io.Logger;
import com.softwareverde.util.type.time.SystemTime;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ThreadPoolThrottle extends SleepyService implements ThreadPool {
    public static final Integer MAX_QUEUE_SIZE = 75000;

    protected final SystemTime _systemTime = new SystemTime();
    protected final ConcurrentLinkedQueue<Runnable> _queue = new ConcurrentLinkedQueue<Runnable>();
    protected final ThreadPool _threadPool;
    protected final Integer _maxSubmissionsPerSecond;
    protected Long _lastLogStatement = 0L;
    protected Long _droppedSubmissionsCount = 0L;

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

        _threadPool.execute(runnable);

        try {
            Thread.sleep(1000L / _maxSubmissionsPerSecond);
        }
        catch (final InterruptedException exception) {
            Thread.currentThread().interrupt(); // Preserve the interrupted status...
            return false;
        }

        if (_queue.size() > (_maxSubmissionsPerSecond * 10)) {
            final Long now = System.currentTimeMillis();
            if (now - _lastLogStatement > 5000L) {
                Logger.log("ThreadPoolThrottle is " + (_queue.size() / _maxSubmissionsPerSecond) + " seconds behind.");
                _lastLogStatement = now;
            }
        }

        return (! _queue.isEmpty());
    }

    @Override
    protected void _onSleep() { }

    @Override
    public void execute(final Runnable runnable) {
        if (_queue.size() >= MAX_QUEUE_SIZE) {
            if (_droppedSubmissionsCount % _maxSubmissionsPerSecond == 0) {
                Logger.log("ThreadPoolThrottle: Exceeded max queue size. " + _droppedSubmissionsCount);
            }

            _droppedSubmissionsCount += 1L;
            return;
        }

        _queue.offer(runnable);
        this.wakeUp();
    }
}

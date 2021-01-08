package com.softwareverde.bitcoin.rpc.monitor;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RpcMonitor<T> implements Monitor {
    private final NanoTimer _timer = new NanoTimer();
    private final AtomicBoolean _isComplete = new AtomicBoolean(false);
    private final AtomicBoolean _threadHasStarted = new AtomicBoolean(false);
    private final Thread _cancelThread;
    private volatile Long _maxDurationMs = null;

    private Long _getDurationMs() {
        if (! _isComplete.get()) {
            _timer.stop();
        }

        final Double msElapsed = _timer.getMillisecondsElapsed();
        return msElapsed.longValue();
    }

    private void _startCancelThread() {
        final boolean isFirstInvocation = _threadHasStarted.compareAndSet(false, true);
        if (! isFirstInvocation) { return; }

        _cancelThread.start();
    }

    private void _stopCancelThread() {
        if (! _threadHasStarted.get()) { return; }
        _cancelThread.interrupt();

        try {
            _cancelThread.join();
        }
        catch (final Exception exception) { }
    }

    protected T _connection;
    protected abstract void _cancelRequest();

    public RpcMonitor() {
        _cancelThread = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean maxDurationReached = false;
                try {
                    while ( (! _cancelThread.isInterrupted()) && (! maxDurationReached) && (! _isComplete.get()) ) {
                        Thread.sleep(100L);

                        final long duration = _getDurationMs();
                        maxDurationReached = (duration > _maxDurationMs);

                        if (maxDurationReached) {
                            Logger.debug("Request timed out after " + duration + "ms.");
                        }
                    }
                }
                catch (final Exception exception) { }

                if (maxDurationReached) {
                    RpcMonitor.this.cancel();
                }
            }
        });
    }

    /**
     * Caller must always invoke afterRequestEnd if beforeRequestStart is invoked.
     */
    protected void beforeRequestStart(final T connection) {
        _connection = connection;
        _timer.start();

        _startCancelThread();
    }

    /**
     * Must always be called if beforeRequestStart is invoked.
     */
    protected void afterRequestEnd() {
        _isComplete.set(true);
        _timer.stop();

        _stopCancelThread();
    }

    @Override
    public Boolean isComplete() {
        return _isComplete.get();
    }

    @Override
    public Long getDurationMs() {
        return _getDurationMs();
    }

    @Override
    public void setMaxDurationMs(final Long maxDurationMs) {
        if (_isComplete.get()) { return; }
        _maxDurationMs = maxDurationMs;
    }

    @Override
    public void cancel() {
        _isComplete.set(true);
        _timer.stop();

        _stopCancelThread();

        final T connection = _connection;
        if (connection == null) { return; }

        _cancelRequest();
    }
}

package com.softwareverde.concurrent.service;

public abstract class GracefulSleepyService extends SleepyService {
    private volatile Boolean _isShuttingDown = false;

    @Override
    protected Boolean _shouldAbort() {
        return (super._shouldAbort() || _isShuttingDown);
    }

    @Override
    public synchronized void stop() {
        if (_isShuttingDown) {
            // stop was invoked twice; shutdown via interrupt.
            super.stop();
            return;
        }

        _isShuttingDown = true;
        try {
            for (int i = 0; i < 20; ++i) {
                if (_status == Status.ACTIVE) { break; }
                Thread.sleep(_stopTimeoutMs / 20L);
            }
            if (_status != Status.ACTIVE) { // If the service still hasn't exited then interrupt the thread.
                super.stop();
            }
        }
        catch (final Exception exception) {
            super.stop();
        }
        finally {
            _isShuttingDown = false;
        }
    }
}

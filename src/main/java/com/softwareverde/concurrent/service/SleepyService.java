package com.softwareverde.concurrent.service;

import com.softwareverde.io.Logger;

public abstract class SleepyService {
    public enum Status {
        ACTIVE, SLEEPING
    }

    public interface StatusMonitor {
        Status getStatus();
    }

    private final Object _threadMutex = new Object();

    private final Runnable _coreRunnable;
    private final StatusMonitor _statusMonitor;

    private Boolean _isShuttingDown = false;
    private Boolean _shouldRestart = false;
    private Thread _thread = null;

    private void _startThread() {
        _thread = new Thread(_coreRunnable);
        _thread.setName(this.getClass().getSimpleName());
        _thread.start();
    }

    protected abstract void _onStart();
    protected abstract Boolean _run();
    protected abstract void _onSleep();

    protected SleepyService() {
        _statusMonitor = new StatusMonitor() {
            @Override
            public Status getStatus() {
                synchronized (_threadMutex) {
                    if ( (_thread == null) || (_thread.isInterrupted()) ) {
                        return Status.SLEEPING;
                    }
                    return Status.ACTIVE;
                }
            }
        };

        _coreRunnable = new Runnable() {
            @Override
            public void run() {
                _onStart();

                final Thread thread = Thread.currentThread();
                do {
                    _shouldRestart = false;

                    while (! thread.isInterrupted()) {
                        try {
                            final Boolean shouldContinue = _run();

                            if (! shouldContinue) { break; }
                        }
                        catch (final Exception exception) {
                            Logger.log(exception);
                            break;
                        }
                    }

                } while ( (_shouldRestart) && (! thread.isInterrupted()) );

                synchronized (_threadMutex) {
                    _thread = null;
                }

                _onSleep();
            }
        };
    }

    public void start() {
        synchronized (_threadMutex) {
            _isShuttingDown = false;

            if (_thread == null) {
                _startThread();
            }
        }
    }

    public void wakeUp() {
        synchronized (_threadMutex) {
            _shouldRestart = true;
            if ( (_thread == null) && (! _isShuttingDown) ) {
                _startThread();
            }
        }
    }

    public void stop() {
        final Thread thread;
        synchronized (_threadMutex) {
            _isShuttingDown = true;

            _shouldRestart = false;
            thread = _thread;
            _thread = null;
        }

        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            }
            catch (final InterruptedException exception) { }
        }
    }

    public StatusMonitor getStatusMonitor() {
        return _statusMonitor;
    }
}

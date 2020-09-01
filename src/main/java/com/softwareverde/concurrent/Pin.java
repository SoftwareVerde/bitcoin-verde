package com.softwareverde.concurrent;

import com.softwareverde.util.timer.NanoTimer;

import java.util.concurrent.atomic.AtomicBoolean;

public class Pin {
    protected final AtomicBoolean _pin = new AtomicBoolean(false);

    protected Boolean _waitForRelease(final Long timeout) throws InterruptedException {
        synchronized (_pin) {
            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            if (_pin.get()) { return true; }
            _pin.wait(timeout);

            nanoTimer.stop();
            final Double msElapsed = nanoTimer.getMillisecondsElapsed(); // Timeout reached...
            if (msElapsed >= timeout) {
                return false;
            }

            return true;
        }
    }

    public Boolean wasReleased() {
        synchronized (_pin) {
            return _pin.get();
        }
    }

    public void release() {
        synchronized (_pin) {
            _pin.set(true);
            _pin.notifyAll();
        }
    }

    public void waitForRelease() {
        try {
            _waitForRelease(0L);
        }
        catch (final InterruptedException exception) {
            throw new RuntimeException(exception);
        }
    }

    public Boolean waitForRelease(final Long timeout) throws InterruptedException {
        return _waitForRelease(timeout);
    }
}

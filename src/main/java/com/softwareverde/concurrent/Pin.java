package com.softwareverde.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;

public class Pin {
    protected final AtomicBoolean _pin = new AtomicBoolean(false);

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
        synchronized (_pin) {
            try {
                if (_pin.get()) { return; }
                _pin.wait();
            }
            catch (final InterruptedException exception) {
                throw new RuntimeException(exception);
            }
        }
    }
}

package com.softwareverde.concurrent;

import com.softwareverde.logging.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public class Pin {
    protected final AtomicBoolean _pin = new AtomicBoolean(false);

    public void release() {
        synchronized (_pin) {
            _pin.set(true);
            _pin.notifyAll();
        }
    }

    public void waitFor() {
        synchronized (_pin) {
            try {
                if (_pin.get()) { return; }
                _pin.wait();
            }
            catch (final InterruptedException exception) {
                throw new RuntimeException(exception);
            }
            finally {
                _pin.set(false);
            }
        }
    }
}

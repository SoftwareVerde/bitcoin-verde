package com.softwareverde.util.timer;

public class DisabledMultiTimer extends MultiTimer {
    public static final String STRING_VALUE = DisabledMultiTimer.class.toString();

    @Override
    public void start() {
        // Nothing.
    }

    @Override
    public void mark(final String label) {
        // Nothing.
    }

    @Override
    public void stop(final String label) {
        // Nothing.
    }

    @Override
    public String toString() {
        return STRING_VALUE;
    }
}

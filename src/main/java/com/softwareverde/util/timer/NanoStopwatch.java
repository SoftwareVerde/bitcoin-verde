package com.softwareverde.util.timer;

public class NanoStopwatch extends NanoTimer {
    protected Double _msElapsed = 0D;

    @Override
    public void stop() {
        super.stop();
        _msElapsed += super.getMillisecondsElapsed();
        super.start();
    }

    @Override
    public void reset() {
        super.reset();
        _msElapsed = 0D;
    }

    @Override
    public Double getMillisecondsElapsed() {
        return _msElapsed;
    }
}

package com.softwareverde.util.timer;

import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.Map;

public class MultiTimer {
    protected final HashMap<String, Double> _laps = new HashMap<>();
    protected NanoTimer _currentTimer = null;

    public void start() {
        _currentTimer = new NanoTimer();
        _currentTimer.start();
    }

    public void mark(final String label) {
        if (_currentTimer != null) {
            _currentTimer.stop();
            final Double oldMsElapsed = Util.coalesce(_laps.get(label), 0D);
            final Double msElapsed = (oldMsElapsed + _currentTimer.getMillisecondsElapsed());
            _laps.put(label, msElapsed);
        }

        _currentTimer = new NanoTimer();
        _currentTimer.start();
    }

    public void stop(final String label) {
        if (_currentTimer != null) {
            _currentTimer.stop();
            final Double oldMsElapsed = Util.coalesce(_laps.get(label), 0D);
            final Double msElapsed = (oldMsElapsed + _currentTimer.getMillisecondsElapsed());
            _laps.put(label, msElapsed);
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        String delimiter = "";
        for (final Map.Entry<String, Double> tuple : _laps.entrySet()) {
            final String label = tuple.getKey();
            final Double msElapsed = tuple.getValue();

            stringBuilder.append(delimiter);
            stringBuilder.append(label);
            stringBuilder.append("=");
            stringBuilder.append(msElapsed);
            stringBuilder.append("ms");

            delimiter = ", ";
        }

        return stringBuilder.toString();
    }
}

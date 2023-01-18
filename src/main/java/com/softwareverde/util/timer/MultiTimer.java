package com.softwareverde.util.timer;

import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

public class MultiTimer {
    protected final MutableHashMap<String, Double> _laps = new MutableHashMap<>();
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
        for (final Tuple<String, Double> tuple : _laps) {
            final String label = tuple.first;
            final Double msElapsed = tuple.second;

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

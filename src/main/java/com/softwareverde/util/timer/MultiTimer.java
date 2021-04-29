package com.softwareverde.util.timer;

import com.softwareverde.util.Tuple;

import java.util.LinkedList;

public class MultiTimer {
    protected final LinkedList<Tuple<String, NanoTimer>> _laps = new LinkedList<>();
    protected NanoTimer _currentTimer = null;

    public void start() {
        _currentTimer = new NanoTimer();
        _currentTimer.start();
    }

    public void mark(final String label) {
        if (_currentTimer != null) {
            _currentTimer.stop();
            _laps.add(new Tuple<>(label, _currentTimer));
        }

        _currentTimer = new NanoTimer();
        _currentTimer.start();
    }

    public void stop(final String label) {
        if (_currentTimer != null) {
            _currentTimer.stop();
            _laps.add(new Tuple<>(label, _currentTimer));
        }
    }

    @Override
    public String toString() {
        final StringBuilder stringBuilder = new StringBuilder();

        String delimiter = "";
        for (final Tuple<String, NanoTimer> tuple : _laps) {
            final String label = tuple.first;
            final NanoTimer nanoTimer = tuple.second;

            stringBuilder.append(delimiter);
            stringBuilder.append(label);
            stringBuilder.append("=");
            stringBuilder.append(nanoTimer.getMillisecondsElapsed());
            stringBuilder.append("ms");

            delimiter = ", ";
        }

        return stringBuilder.toString();
    }
}

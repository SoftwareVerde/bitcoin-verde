package com.softwareverde.bitcoin.server.properties;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPropertiesStore implements PropertiesStore {
    protected final ConcurrentHashMap<String, String> _values = new ConcurrentHashMap<>();

    public InMemoryPropertiesStore() { }

    @Override
    public synchronized void set(final String key, final Long value) {
        final String stringValue = Converter.LONG.toString(value);
        _values.put(key, stringValue);
    }

    @Override
    public void set(final String key, final String value) {
        _values.put(key, value);
    }

    @Override
    public synchronized Long getLong(final String key) {
        final String stringValue = _values.get(key);
        return Converter.LONG.toType(stringValue);
    }

    @Override
    public String getString(final String key) {
        return _values.get(key);
    }

    @Override
    public void getAndSetString(final String key, final GetAndSetter<String> getAndSetter) {
        final String value = _values.get(key);
        final String newValue = getAndSetter.run(value);
        _values.put(key, newValue);
    }

    @Override
    public synchronized void getAndSetLong(final String key, final GetAndSetter<Long> getAndSetter) {
        final String stringValue = _values.get(key);
        final Long value = Converter.LONG.toType(stringValue);
        final Long newValue = getAndSetter.run(value);
        final String newStringValue = Converter.LONG.toString(newValue);
        _values.put(key, newStringValue);
    }

    public void clear() {
        _values.clear();
    }
}

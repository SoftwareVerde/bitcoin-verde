package com.softwareverde.bitcoin.server.properties;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPropertiesStore implements PropertiesStore {
    protected final ConcurrentHashMap<String, Long> _values = new ConcurrentHashMap<>();

    public InMemoryPropertiesStore() { }

    @Override
    public synchronized void set(final String key, final Long value) {
        _values.put(key, value);
    }

    @Override
    public synchronized Long get(final String key) {
        return _values.get(key);
    }

    @Override
    public synchronized void getAndSet(final String key, final GetAndSetter getAndSetter) {
        final Long value = _values.get(key);
        final Long newValue = getAndSetter.run(value);
        _values.put(key, newValue);
    }

    public void clear() {
        _values.clear();
    }
}

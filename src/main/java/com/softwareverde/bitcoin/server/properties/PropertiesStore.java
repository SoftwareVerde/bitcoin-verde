package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.util.type.identifier.Identifier;

public interface PropertiesStore {
    interface GetAndSetter<T> {
        T run(T value);
    }

    void set(String key, Long value);
    void set(String key, String value);

    default void set(final String key, final Identifier value) {
        final Long longValue = (value != null ? value.longValue() : null);
        this.set(key, longValue);
    }

    Long getLong(String key);
    String getString(String key);

    void getAndSetLong(String key, GetAndSetter<Long> getAndSetter);
    void getAndSetString(String key, GetAndSetter<String> getAndSetter);
}

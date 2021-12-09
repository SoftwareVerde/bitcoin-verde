package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.util.type.identifier.Identifier;

public interface PropertiesStore {
    interface GetAndSetter {
        Long run(Long value);
    }

    void set(final String key, final Long value);

    default void set(final String key, final Identifier value) {
        final Long longValue = (value != null ? value.longValue() : null);
        this.set(key, longValue);
    }

    Long get(final String key);

    void getAndSet(final String key, final GetAndSetter getAndSetter);
}

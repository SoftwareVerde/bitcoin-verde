package com.softwareverde.bitcoin.server.properties;

import com.softwareverde.util.Util;

public interface Converter<T> {
    T toType(String value);
    String toString(T value);

    Converter<String> STRING = new Converter<String>() {
        @Override
        public String toType(final String value) {
            return value;
        }

        @Override
        public String toString(final String value) {
            return value;
        }
    };

    Converter<Long> LONG = new Converter<Long>() {
        @Override
        public Long toType(final String value) {
            return Util.parseLong(value);
        }

        @Override
        public String toString(final Long value) {
            return (value != null ? value.toString() : null);
        }
    };
}
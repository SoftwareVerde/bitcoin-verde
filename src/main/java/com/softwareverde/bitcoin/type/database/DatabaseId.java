package com.softwareverde.bitcoin.type.database;

import com.softwareverde.constable.Const;

public class DatabaseId implements Const, Comparable<Long> {
    public static DatabaseId wrap(final Long value) {
        if (value == null) { return null; }
        return new DatabaseId(value);
    }

    protected final Long _value;

    protected DatabaseId(final Long value) {
        if (value == null) {
            throw new NullPointerException("DatabaseId._value must not be null. Return null directly instead.");
        }

        _value = value;
    }

    public long longValue() {
        return _value;
    }

    @Override
    public int hashCode() {
        return _value.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        return _value.equals(object);
    }

    @Override
    public String toString() {
        return _value.toString();
    }

    @Override
    public int compareTo(final Long value) {
        return _value.compareTo(value);
    }
}

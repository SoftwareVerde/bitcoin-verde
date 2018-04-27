package com.softwareverde.bitcoin.type.identifier;

import com.softwareverde.constable.Const;

public class Identifier implements Const, Comparable<Long> {
    public static Identifier wrap(final Long value) {
        if (value == null) { return null; }
        return new Identifier(value);
    }

    protected final Long _value;

    protected Identifier(final Long value) {
        if (value == null) {
            throw new NullPointerException("Identifier._value must not be null. Return null directly instead.");
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
        if (object instanceof Identifier) {
            final Identifier databaseId = (Identifier) object;
            return _value.equals(databaseId._value);
        }

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

package com.softwareverde.bitcoin.server.database.cache.utxo;

import com.softwareverde.constable.Const;
import com.softwareverde.util.Util;

/**
 * UtxoCount is a wrapper around java.lang.Long representing a count of unnspent TransactionOutputs.
 *  Typically, this wrapper is used to prevent the value from being confused with a byte count or other ambiguous unit.
 */
public class UtxoCount implements Const, Comparable<UtxoCount> {
    public static UtxoCount wrap(final Long value) {
        if (value == null) { return null; }

        return new UtxoCount(value);
    }

    protected final Long _value;

    protected UtxoCount(final Long value) {
        _value = value;
    }

    public Long unwrap() {
        return _value;
    }

    @Override
    public int hashCode() {
        return _value.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (object instanceof UtxoCount) {
            return Util.areEqual(_value, ((UtxoCount) object)._value);
        }

        return Util.areEqual(_value, object);
    }

    @Override
    public String toString() {
        return _value.toString();
    }

    @Override
    public int compareTo(final UtxoCount value) {
        return _value.compareTo(value._value);
    }
}

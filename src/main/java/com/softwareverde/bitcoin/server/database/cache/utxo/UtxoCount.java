package com.softwareverde.bitcoin.server.database.cache.utxo;

/**
 * UtxoCount is a wrapper around java.lang.Long representing a count of unnspent TransactionOutputs.
 *  Typically, this wrapper is used to prevent the value from being confused with a byte count or other ambiguous unit.
 */
public class UtxoCount {
    public static final UtxoCount wrap(final Long value) {
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
}

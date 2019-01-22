package com.softwareverde.bitcoin.transaction;

import com.softwareverde.util.type.identifier.Identifier;

public class TransactionId extends Identifier {
    public static TransactionId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionId(value);
    }

    protected TransactionId(final Long value) {
        super(value);
    }
}

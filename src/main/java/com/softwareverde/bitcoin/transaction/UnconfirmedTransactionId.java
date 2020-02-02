package com.softwareverde.bitcoin.transaction;

import com.softwareverde.util.type.identifier.Identifier;

public class UnconfirmedTransactionId extends Identifier {
    public static UnconfirmedTransactionId wrap(final Long value) {
        if (value == null) { return null; }
        return new UnconfirmedTransactionId(value);
    }

    protected UnconfirmedTransactionId(final Long value) {
        super(value);
    }
}

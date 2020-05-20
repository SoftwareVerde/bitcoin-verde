package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.util.type.identifier.Identifier;

public class UnconfirmedTransactionInputId extends Identifier {
    public static UnconfirmedTransactionInputId wrap(final Long value) {
        if (value == null) { return null; }
        return new UnconfirmedTransactionInputId(value);
    }

    protected UnconfirmedTransactionInputId(final Long value) {
        super(value);
    }
}

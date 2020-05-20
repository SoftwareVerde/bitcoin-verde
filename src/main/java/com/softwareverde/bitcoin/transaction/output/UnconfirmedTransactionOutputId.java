package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.util.type.identifier.Identifier;

public class UnconfirmedTransactionOutputId extends Identifier {
    public static UnconfirmedTransactionOutputId wrap(final Long value) {
        if (value == null) { return null; }
        return new UnconfirmedTransactionOutputId(value);
    }

    protected UnconfirmedTransactionOutputId(final Long value) {
        super(value);
    }
}

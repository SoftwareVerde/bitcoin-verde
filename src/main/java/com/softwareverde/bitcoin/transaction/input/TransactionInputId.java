package com.softwareverde.bitcoin.transaction.input;

import com.softwareverde.bitcoin.type.database.DatabaseId;

public class TransactionInputId extends DatabaseId {
    public static TransactionInputId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionInputId(value);
    }

    protected TransactionInputId(final Long value) {
        super(value);
    }
}

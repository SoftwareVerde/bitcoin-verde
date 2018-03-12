package com.softwareverde.bitcoin.transaction;

import com.softwareverde.bitcoin.type.database.DatabaseId;

public class TransactionId extends DatabaseId {
    public static TransactionId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionId(value);
    }

    protected TransactionId(final Long value) {
        super(value);
    }
}

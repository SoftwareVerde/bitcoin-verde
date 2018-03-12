package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.bitcoin.type.database.DatabaseId;

public class TransactionOutputId extends DatabaseId {
    public static TransactionOutputId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionOutputId(value);
    }

    protected TransactionOutputId(final Long value) {
        super(value);
    }
}

package com.softwareverde.bitcoin.server.module.node.database.address;

import com.softwareverde.util.type.identifier.Identifier;

public class TransactionOutputId extends Identifier {
    public static TransactionOutputId wrap(final Long value) { return new TransactionOutputId(value); }

    protected TransactionOutputId(final Long value) {
        super(value);
    }
}

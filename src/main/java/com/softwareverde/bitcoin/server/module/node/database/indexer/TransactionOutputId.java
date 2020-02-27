package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.util.type.identifier.Identifier;

public class TransactionOutputId extends Identifier {
    public static TransactionOutputId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionOutputId(value);
    }

    protected TransactionOutputId(final Long value) {
        super(value);
    }
}

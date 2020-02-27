package com.softwareverde.bitcoin.server.module.node.database.indexer;

import com.softwareverde.util.type.identifier.Identifier;

public class TransactionInputId extends Identifier {
    public static TransactionInputId wrap(final Long value) {
        if (value == null) { return null; }
        return new TransactionInputId(value);
    }

    protected TransactionInputId(final Long value) {
        super(value);
    }
}

package com.softwareverde.bitcoin.server.module.node.sync.transaction.pending;

import com.softwareverde.util.type.identifier.Identifier;

public class PendingTransactionId extends Identifier {
    public static PendingTransactionId wrap(final Long value) {
        if (value == null) { return null; }
        return new PendingTransactionId(value);
    }

    protected PendingTransactionId(final Long value) {
        super(value);
    }
}

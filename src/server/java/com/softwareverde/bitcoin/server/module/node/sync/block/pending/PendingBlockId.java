package com.softwareverde.bitcoin.server.module.node.sync.block.pending;

import com.softwareverde.util.type.identifier.Identifier;

public class PendingBlockId extends Identifier {
    public static PendingBlockId wrap(final Long value) {
        if (value == null) { return null; }
        return new PendingBlockId(value);
    }

    protected PendingBlockId(final Long value) {
        super(value);
    }
}

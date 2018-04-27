package com.softwareverde.bitcoin.server.node;

import com.softwareverde.bitcoin.type.identifier.Identifier;

public class NodeId extends Identifier {
    public static NodeId wrap(final Long value) {
        if (value == null) { return null; }
        return new NodeId(value);
    }

    protected NodeId(final Long value) {
        super(value);
    }
}

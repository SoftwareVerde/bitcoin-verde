package com.softwareverde.network.p2p.node;

import com.softwareverde.util.type.identifier.Identifier;

public class NodeId extends Identifier {
    public static NodeId wrap(final Long value) {
        if (value == null) { return null; }
        return new NodeId(value);
    }

    protected NodeId(final Long value) {
        super(value);
    }
}

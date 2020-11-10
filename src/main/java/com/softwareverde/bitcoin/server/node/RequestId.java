package com.softwareverde.bitcoin.server.node;

import com.softwareverde.util.type.identifier.Identifier;

public class RequestId extends Identifier {
    public static RequestId wrap(final Long value) {
        if (value == null) { return null; }
        return new RequestId(value);
    }

    protected RequestId(final Long value) {
        super(value);
    }
}

package com.softwareverde.bitcoin.miner.pool;

import com.softwareverde.util.type.identifier.Identifier;

public class WorkerId extends Identifier {
    public static WorkerId wrap(final Long value) {
        if (value == null) { return null; }
        return new WorkerId(value);
    }

    protected WorkerId(final Long value) {
        super(value);
    }
}

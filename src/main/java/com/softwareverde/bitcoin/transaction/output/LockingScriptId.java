package com.softwareverde.bitcoin.transaction.output;

import com.softwareverde.util.type.identifier.Identifier;

public class LockingScriptId extends Identifier {
    public static LockingScriptId wrap(final Long value) {
        if (value == null) { return null; }
        return new LockingScriptId(value);
    }

    protected LockingScriptId(final Long value) {
        super(value);
    }
}

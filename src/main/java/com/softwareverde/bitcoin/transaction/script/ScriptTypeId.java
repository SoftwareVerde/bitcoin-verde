package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.util.type.identifier.Identifier;

public class ScriptTypeId extends Identifier {
    public static ScriptTypeId wrap(final Long value) {
        if (value == null) { return null; }
        return new ScriptTypeId(value);
    }

    protected ScriptTypeId(final Long value) {
        super(value);
    }
}

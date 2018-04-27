package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.type.identifier.Identifier;

public class BlockId extends Identifier {
    public static BlockId wrap(final Long value) {
        if (value == null) { return null; }
        return new BlockId(value);
    }

    protected BlockId(final Long value) {
        super(value);
    }
}

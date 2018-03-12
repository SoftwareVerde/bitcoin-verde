package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.type.database.DatabaseId;

public class BlockId extends DatabaseId {
    public static BlockId wrap(final Long value) {
        if (value == null) { return null; }
        return new BlockId(value);
    }

    protected BlockId(final Long value) {
        super(value);
    }
}

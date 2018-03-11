package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.bitcoin.type.database.DatabaseId;

public class BlockChainSegmentId extends DatabaseId {
    public static BlockChainSegmentId wrap(final Long value) {
        if (value == null) { return null; }
        return new BlockChainSegmentId(value);
    }

    protected BlockChainSegmentId(final Long value) {
        super(value);
    }
}

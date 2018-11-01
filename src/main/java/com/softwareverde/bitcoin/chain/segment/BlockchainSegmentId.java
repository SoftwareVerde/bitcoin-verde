package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.util.type.identifier.Identifier;

public class BlockchainSegmentId extends Identifier {
    public static BlockchainSegmentId wrap(final Long value) {
        if (value == null) { return null; }
        return new BlockchainSegmentId(value);
    }

    protected BlockchainSegmentId(final Long value) {
        super(value);
    }
}

package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.util.type.identifier.Identifier;

public class BlockChainSegmentId extends Identifier {
    public static BlockChainSegmentId wrap(final Long value) {
        if (value == null) { return null; }
        return new BlockChainSegmentId(value);
    }

    protected BlockChainSegmentId(final Long value) {
        super(value);
    }
}

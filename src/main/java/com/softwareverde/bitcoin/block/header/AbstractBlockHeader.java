package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;

public class AbstractBlockHeader extends BlockHeaderCore {
    protected AbstractBlockHeader(final BlockHasher blockHasher) {
        super(blockHasher);
    }

    public AbstractBlockHeader() { }

    public AbstractBlockHeader(final BlockHeader blockHeader) {
        super(blockHeader);
    }
}

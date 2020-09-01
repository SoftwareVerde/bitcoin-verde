package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.constable.Const;

public class ImmutableBlockHeader extends BlockHeaderCore implements BlockHeader, Const {
    protected ImmutableBlockHeader(final BlockHasher blockHasher) {
        super(blockHasher);
    }

    public ImmutableBlockHeader() { }

    public ImmutableBlockHeader(final BlockHeader blockHeader) {
        super(blockHeader);
    }
}

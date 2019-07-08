package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCountInflater;

public interface ExtendedBlockHeaderInflaters extends BlockHeaderInflaters {
    BlockHeaderWithTransactionCountInflater getBlockHeaderWithTransactionCountInflater();
}

package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.header.BlockHeader;

public interface BlockHeaderContext {
    BlockHeader getBlockHeader(Long blockHeight);
}

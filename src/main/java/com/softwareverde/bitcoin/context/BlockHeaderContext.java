package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.block.header.*;

public interface BlockHeaderContext {
    BlockHeader getBlockHeader(Long blockHeight);
}

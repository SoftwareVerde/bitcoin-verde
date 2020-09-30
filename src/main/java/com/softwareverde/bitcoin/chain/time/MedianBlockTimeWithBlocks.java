package com.softwareverde.bitcoin.chain.time;

import com.softwareverde.bitcoin.block.header.BlockHeader;

public interface MedianBlockTimeWithBlocks extends MedianBlockTime {
    MedianBlockTime subset(Integer blockCount);
    BlockHeader getBlockHeader(Integer indexFromTip);
}

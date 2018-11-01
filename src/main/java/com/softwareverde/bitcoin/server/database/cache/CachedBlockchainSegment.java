package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;

public class CachedBlockchainSegment extends BlockchainSegment {
    public CachedBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        _id = blockchainSegmentId;
    }

    public CachedBlockchainSegment(final BlockchainSegment blockchainSegment) {
        _id = blockchainSegment.getId();
        _headBlockId = blockchainSegment.getHeadBlockId();
        _tailBlockId = blockchainSegment.getTailBlockId();
        _blockHeight = blockchainSegment.getBlockHeight();
        _blockCount = blockchainSegment.getBlockCount();
    }

    public void setHeadBlockId(final BlockId blockId) { _headBlockId = blockId; }
    public void setTailBlockId(final BlockId blockId) { _tailBlockId = blockId; }
    public void setBlockHeight (final Long blockHeight) { _blockHeight = blockHeight; }
    public void setBlockCount(final Long blockCount) { _blockCount = blockCount; }
}

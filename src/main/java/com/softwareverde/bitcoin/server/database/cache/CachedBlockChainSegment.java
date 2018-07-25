package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;

public class CachedBlockChainSegment extends BlockChainSegment {
    public CachedBlockChainSegment(final BlockChainSegmentId blockChainSegmentId) {
        _id = blockChainSegmentId;
    }

    public CachedBlockChainSegment(final BlockChainSegment blockChainSegment) {
        _id = blockChainSegment.getId();
        _headBlockId = blockChainSegment.getHeadBlockId();
        _tailBlockId = blockChainSegment.getTailBlockId();
        _blockHeight = blockChainSegment.getBlockHeight();
        _blockCount = blockChainSegment.getBlockCount();
    }

    public void setHeadBlockId(final BlockId blockId) { _headBlockId = blockId; }
    public void setTailBlockId(final BlockId blockId) { _tailBlockId = blockId; }
    public void setBlockHeight (final Long blockHeight) { _blockHeight = blockHeight; }
    public void setBlockCount(final Long blockCount) { _blockCount = blockCount; }
}

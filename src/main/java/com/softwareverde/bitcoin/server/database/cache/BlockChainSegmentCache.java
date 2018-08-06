package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.util.Util;

public class BlockChainSegmentCache {
    protected final Object _cacheMutex = new Object();
    protected BlockChainSegment _cachedBlockChainSegment = new CachedBlockChainSegment(BlockChainSegmentId.wrap(0L));

    public void clear() {
        _cachedBlockChainSegment = new CachedBlockChainSegment(BlockChainSegmentId.wrap(0L));
    }

    public BlockChainSegment loadCachedBlockChainSegment(final BlockChainSegmentId blockChainSegmentId) {
        synchronized (_cacheMutex) {
            if (Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId)) {
                return _cachedBlockChainSegment;
            }
        }

        return null;
    }

    public Boolean isCached(final BlockChainSegmentId blockChainSegmentId) {
        synchronized (_cacheMutex) {
            return Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId);
        }
    }

    public void cacheBlockChainSegment(final BlockChainSegment blockChainSegment) {
        synchronized (_cacheMutex) {
            _cachedBlockChainSegment = blockChainSegment;
        }
    }

    public void updateCachedBlockChainSegment(final BlockChainSegmentId blockChainSegmentId, final BlockId newBlockId) {
        synchronized (_cacheMutex) {
            if (Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId)) {
                final CachedBlockChainSegment cachedBlockChainSegment = new CachedBlockChainSegment(_cachedBlockChainSegment);
                cachedBlockChainSegment.setHeadBlockId(newBlockId);
                cachedBlockChainSegment.setBlockHeight(cachedBlockChainSegment.getBlockHeight() + 1);
                cachedBlockChainSegment.setBlockCount(cachedBlockChainSegment.getBlockCount() + 1);
                _cachedBlockChainSegment = cachedBlockChainSegment;
            }
        }
    }

    public void updateCachedBlockChainSegment(final BlockChainSegmentId blockChainSegmentId, final BlockId newBlockId, final Long newBlockHeight, final Integer blockCountDelta) {
        synchronized (_cacheMutex) {
            if (Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId)) {
                final CachedBlockChainSegment cachedBlockChainSegment = new CachedBlockChainSegment(_cachedBlockChainSegment);
                cachedBlockChainSegment.setHeadBlockId(newBlockId);
                cachedBlockChainSegment.setBlockHeight(newBlockHeight);
                cachedBlockChainSegment.setBlockCount(cachedBlockChainSegment.getBlockCount() - blockCountDelta);
                _cachedBlockChainSegment = cachedBlockChainSegment;
            }
        }
    }
}

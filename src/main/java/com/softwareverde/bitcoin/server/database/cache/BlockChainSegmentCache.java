package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.util.Util;

public class BlockChainSegmentCache {
    public final Object MUTEX = new Object();

    protected BlockChainSegment _cachedBlockChainSegment = new CachedBlockChainSegment(BlockChainSegmentId.wrap(0L));

    public void clear() {
        synchronized (MUTEX) {
            _cachedBlockChainSegment = new CachedBlockChainSegment(BlockChainSegmentId.wrap(0L));
        }
    }

    public BlockChainSegment loadCachedBlockChainSegment(final BlockChainSegmentId blockChainSegmentId) {
        synchronized (MUTEX) {
            if (Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId)) {
                return _cachedBlockChainSegment;
            }
        }

        return null;
    }

    public Boolean isCached(final BlockChainSegmentId blockChainSegmentId) {
        synchronized (MUTEX) {
            return Util.areEqual(_cachedBlockChainSegment.getId(), blockChainSegmentId);
        }
    }

    public void cacheBlockChainSegment(final BlockChainSegment blockChainSegment) {
        synchronized (MUTEX) {
            _cachedBlockChainSegment = blockChainSegment;
        }
    }

}

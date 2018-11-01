package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.util.Util;

public class BlockchainSegmentCache {
    public final Object MUTEX = new Object();

    protected BlockchainSegment _cachedBlockchainSegment = new CachedBlockchainSegment(BlockchainSegmentId.wrap(0L));

    public void clear() {
        synchronized (MUTEX) {
            _cachedBlockchainSegment = new CachedBlockchainSegment(BlockchainSegmentId.wrap(0L));
        }
    }

    public BlockchainSegment loadCachedBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) {
        synchronized (MUTEX) {
            if (Util.areEqual(_cachedBlockchainSegment.getId(), blockchainSegmentId)) {
                return _cachedBlockchainSegment;
            }
        }

        return null;
    }

    public Boolean isCached(final BlockchainSegmentId blockchainSegmentId) {
        synchronized (MUTEX) {
            return Util.areEqual(_cachedBlockchainSegment.getId(), blockchainSegmentId);
        }
    }

    public void cacheBlockchainSegment(final BlockchainSegment blockchainSegment) {
        synchronized (MUTEX) {
            _cachedBlockchainSegment = blockchainSegment;
        }
    }

}

package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.io.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

public class BlockChainSegmentIdCache {
    public final Object MUTEX = new Object();

    protected final Integer _cacheMaxSize = 144;
    protected final java.util.HashMap<BlockId, BlockChainSegmentId> _cache = new java.util.HashMap<BlockId, BlockChainSegmentId>(_cacheMaxSize);
    protected final java.util.LinkedList<BlockId> _recentBlockIds = new java.util.LinkedList<BlockId>();
    protected Integer _cacheSize = 0;

    protected Integer _cacheQueryCount = 0;
    protected Integer _cacheMissCount = 0;
    protected Double _msSpentSearching = 0D;

    public void clear() {
        _recentBlockIds.clear();
        _cache.clear();
        _cacheSize = 0;

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;
        _msSpentSearching = 0D;
    }

    protected void _markAsRecentBlockId(final int index, final BlockId blockId) {
        _recentBlockIds.remove(index);
        _recentBlockIds.addLast(blockId);
    }

    protected Integer _findIndex(final BlockId blockId) {
        final Timer timer = new Timer();
        timer.start();

        for (int i = 0; i < _cacheSize; ++i) {
            final int index = ((_cacheSize - i) - 1);
            final BlockId cachedBlockId = _recentBlockIds.get(index);
            if (Util.areEqual(cachedBlockId, blockId)) {
                timer.stop();
                _msSpentSearching += timer.getMillisecondsElapsed();

                return index;
            }
        }

        timer.stop();
        _msSpentSearching += timer.getMillisecondsElapsed();

        return null;
    }

    public Boolean isCached(final BlockId blockId) {
        return _cache.containsKey(blockId);
    }

    public void cacheBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) {
        synchronized (MUTEX) {
            if (_cache.containsKey(blockId)) {
                final Integer index = _findIndex(blockId);
                _markAsRecentBlockId(index, blockId);
                return;
            }

            if (_cacheSize >= _cacheMaxSize) {
                final BlockId oldestBlockId = _recentBlockIds.removeFirst();
                _cache.remove(oldestBlockId);
                _cacheSize -= 1;
            }

            _recentBlockIds.addLast(blockId);
            _cache.put(blockId, blockChainSegmentId);
            _cacheSize += 1;
        }
    }

    public BlockChainSegmentId getBlockChainSegmentId(final BlockId blockId) {
        synchronized (MUTEX) {
            _cacheQueryCount += 1;

            if (! _cache.containsKey(blockId)) {
                _cacheMissCount += 1;
                return null;
            }

            final Integer index = _findIndex(blockId);
            _markAsRecentBlockId(index, blockId);
            return _cache.get(blockId);
        }
    }

    public Integer getSize() {
        return _cacheSize;
    }

    public void debug() {
        Logger.log("BlockChainSegmentIdCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (_cacheMissCount.floatValue() / _cacheQueryCount.floatValue() * 100) +"% Miss) | Cache Size: " + _cacheSize + " | Time Spent Searching: " + _msSpentSearching);
    }
}

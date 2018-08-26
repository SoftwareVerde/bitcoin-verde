package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.io.Logger;

import java.util.HashMap;

public class BlockChainSegmentIdCache {
    public static final Integer DEFAULT_CACHE_SIZE = 1460;

    public final Object MUTEX = new Object();

    protected final Integer _cacheMaxSize = DEFAULT_CACHE_SIZE;
    protected final HashMap<BlockId, BlockChainSegmentId> _cache = new HashMap<BlockId, BlockChainSegmentId>(_cacheMaxSize);
    protected final RecentItemTracker<BlockId> _recentBlockIds = new RecentItemTracker<BlockId>(_cacheMaxSize);

    protected int _cacheSize = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    public void clear() {
        _recentBlockIds.clear();
        _cache.clear();
        _cacheSize = 0;
        _recentBlockIds.clear();

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;

        _recentBlockIds.clearDebug();
    }

    public void cacheBlockChainSegmentId(final BlockId blockId, final BlockChainSegmentId blockChainSegmentId) {
        synchronized (MUTEX) {
            _recentBlockIds.markRecent(blockId);

            if (_cache.containsKey(blockId)) {
                return;
            }

            if (_cacheSize >= _cacheMaxSize) {
                final BlockId oldestBlockId = _recentBlockIds.getOldestItem();
                _cache.remove(oldestBlockId);
                _cacheSize -= 1;
            }

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

            _recentBlockIds.markRecent(blockId);
            return _cache.get(blockId);
        }
    }

    public Integer getSize() {
        return _cache.size();
    }

    public void debug() {
        Logger.log("BlockChainSegmentIdCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (( (float)_cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _cacheSize + " | Time Spent Searching: " + _recentBlockIds.getMsSpentSearching());
    }
}

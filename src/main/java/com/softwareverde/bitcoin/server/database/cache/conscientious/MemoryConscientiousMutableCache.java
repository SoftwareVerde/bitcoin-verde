package com.softwareverde.bitcoin.server.database.cache.conscientious;

import com.softwareverde.bitcoin.server.database.cache.MutableCache;
import com.softwareverde.bitcoin.server.memory.JvmMemoryStatus;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;

public class MemoryConscientiousMutableCache<T, S> implements MutableCache<T, S> {

    protected final MemoryStatus _memoryStatus;
    protected final Float _memoryPercentThreshold;
    protected final MutableCache<T, S> _cache;

    protected void _pruneHalf() {
        Logger.log("NOTICE: Pruning cache by half: " + _cache.toString() + " (" + _memoryStatus.getMemoryUsedPercent() + " / " + _memoryPercentThreshold + ")");
        _memoryStatus.logCurrentMemoryUsage();

        boolean shouldPrune = true;
        for (final T key : _cache.getKeys()) {
            if (shouldPrune) {
                _cache.removeItem(key);
            }
            shouldPrune = (! shouldPrune);
        }
    }

    protected MemoryConscientiousMutableCache(final MutableCache<T, S> cache, final Float memoryPercentThreshold) {
        _cache = cache;
        _memoryStatus = new JvmMemoryStatus();
        _memoryPercentThreshold = memoryPercentThreshold;
    }

    protected MemoryConscientiousMutableCache(final MutableCache<T, S> cache, final Float memoryPercentThreshold, final MemoryStatus memoryStatus) {
        _cache = cache;
        _memoryStatus = memoryStatus;
        _memoryPercentThreshold = memoryPercentThreshold;
    }

    public Float getMemoryPercentThreshold() {
        return _memoryPercentThreshold;
    }

    public MutableCache<T, S> unwrap() {
        return _cache;
    }

    @Override
    public void cacheItem(final T key, final S value) {
        final Boolean isAboveThreshold = (_memoryStatus.getMemoryUsedPercent() >= _memoryPercentThreshold);
        if (isAboveThreshold) {
            _pruneHalf();
        }

        _cache.cacheItem(key, value);
    }

    @Override
    public S removeItem(final T key) {
        return _cache.removeItem(key);
    }

    @Override
    public void invalidate() {
        _cache.invalidate();
    }

    @Override
    public Boolean masterCacheWasInvalidated() {
        return _cache.masterCacheWasInvalidated();
    }

    @Override
    public List<T> getKeys() {
        return _cache.getKeys();
    }

    @Override
    public S getCachedItem(final T item) {
        return _cache.getCachedItem(item);
    }

    @Override
    public Integer getItemCount() {
        return _cache.getItemCount();
    }

    @Override
    public Integer getMaxItemCount() {
        return _cache.getMaxItemCount();
    }

    @Override
    public void debug() {
        _cache.debug();
    }
}

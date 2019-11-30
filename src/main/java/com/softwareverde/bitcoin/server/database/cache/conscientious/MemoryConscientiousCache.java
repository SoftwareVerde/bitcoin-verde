package com.softwareverde.bitcoin.server.database.cache.conscientious;

import com.softwareverde.bitcoin.server.database.cache.Cache;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.constable.list.List;
import com.softwareverde.logging.Logger;

public class MemoryConscientiousCache<T, S> implements Cache<T, S> {
    public static <T, S> MemoryConscientiousCache<T, S> wrap(final Float memoryPercentThreshold, final Cache<T, S> cache, final MemoryStatus memoryStatus) {
        if (cache == null) { return null; }

        if (cache instanceof MemoryConscientiousCache) {
            final MemoryConscientiousCache<T, S> memoryConscientiousCache = (MemoryConscientiousCache<T, S>) cache;
            final boolean memoryThresholdsAreEqual = ( ((int) (memoryConscientiousCache.getMemoryPercentThreshold() * 100)) == ((int) (memoryPercentThreshold * 100)) );
            if (memoryThresholdsAreEqual) {
                return memoryConscientiousCache;
            }
        }

        return new MemoryConscientiousCache<T, S>(cache, memoryPercentThreshold, memoryStatus);
    }

    protected final MemoryStatus _memoryStatus;
    protected final Float _memoryPercentThreshold;
    protected final Cache<T, S> _cache;

    protected void _pruneHalf() {
        Logger.debug("Pruning cache by half: " + _cache.toString() + " (" + _memoryStatus.getMemoryUsedPercent() + " / " + _memoryPercentThreshold + ")");
        _memoryStatus.logCurrentMemoryUsage();

        boolean shouldPrune = true;
        for (final T key : _cache.getKeys()) {
            if (shouldPrune) {
                _cache.remove(key);
            }
            shouldPrune = (! shouldPrune);
        }
    }

    protected MemoryConscientiousCache(final Cache<T, S> cache, final Float memoryPercentThreshold, final MemoryStatus memoryStatus) {
        _cache = cache;
        _memoryStatus = memoryStatus;
        _memoryPercentThreshold = memoryPercentThreshold;
    }

    public Float getMemoryPercentThreshold() {
        return _memoryPercentThreshold;
    }

    public Cache<T, S> unwrap() {
        return _cache;
    }

    @Override
    public void set(final T key, final S value) {
        final boolean isAboveThreshold = (_memoryStatus.getMemoryUsedPercent() >= _memoryPercentThreshold);
        if (isAboveThreshold) {
            _pruneHalf();
        }

        _cache.set(key, value);
    }

    @Override
    public S remove(final T key) {
        return _cache.remove(key);
    }

    @Override
    public void invalidate(final T item) {
        _cache.invalidate(item);
    }

    @Override
    public void invalidate() {
        _cache.invalidate();
    }

    @Override
    public List<T> getKeys() {
        return _cache.getKeys();
    }

    @Override
    public S get(final T item) {
        return _cache.get(item);
    }
}

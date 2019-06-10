package com.softwareverde.bitcoin.server.database.cache.conscientious;

import com.softwareverde.bitcoin.server.database.cache.MutableCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;

public abstract class MemoryConscientiousCache<T, S> implements MutableCache<T, S> {

    public static <T, S> MemoryConscientiousMutableCache<T, S> wrap(final Float memoryPercentThreshold, final MutableCache<T, S> cache, final MemoryStatus memoryStatus) {
        if (cache == null) { return null; }

        if (cache instanceof MemoryConscientiousMutableCache) {
            final MemoryConscientiousMutableCache<T, S> memoryConscientiousCache = (MemoryConscientiousMutableCache<T, S>) cache;
            final boolean memoryThresholdsAreEqual = ( ((int) (memoryConscientiousCache.getMemoryPercentThreshold() * 100)) == ((int) (memoryPercentThreshold * 100)) );
            if (memoryThresholdsAreEqual) {
                return memoryConscientiousCache;
            }
        }

        return new MemoryConscientiousMutableCache<T, S>(cache, memoryPercentThreshold, memoryStatus);
    }

    public static ConscientiousUnspentTransactionOutputCache wrap(final Float memoryPercentThreshold, final UnspentTransactionOutputCache cache) {
        if (cache == null) { return null; }

        if (cache instanceof ConscientiousUnspentTransactionOutputCache) {
            final ConscientiousUnspentTransactionOutputCache memoryConscientiousCache = (ConscientiousUnspentTransactionOutputCache) cache;
            final boolean memoryThresholdsAreEqual = ( ((int) (memoryConscientiousCache.getMemoryPercentThreshold() * 100)) == ((int) (memoryPercentThreshold * 100)) );
            if (memoryThresholdsAreEqual) {
                return memoryConscientiousCache;
            }
        }

        return new ConscientiousUnspentTransactionOutputCache(cache, memoryPercentThreshold);
    }

    protected MemoryConscientiousCache() { }
}

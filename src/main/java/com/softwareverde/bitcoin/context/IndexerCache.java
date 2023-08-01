package com.softwareverde.bitcoin.context;

import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.LogLevel;
import com.softwareverde.logging.Logger;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class IndexerCache {
    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final ReentrantReadWriteLock.ReadLock _readLock;

    protected final AtomicInteger _cacheIdentifierCounter = new AtomicInteger(0);
    protected final ArrayList<ConcurrentHashMap<Sha256Hash, TransactionId>> _cache;
    protected final Integer _bucketCount;

    protected AtomicInteger _cacheHits = new AtomicInteger(0);
    protected AtomicInteger _cacheMisses = new AtomicInteger(0);
    protected AtomicInteger _cachesDestroyed = new AtomicInteger(0);

    public IndexerCache(final Integer bucketCount) {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _writeLock = readWriteLock.writeLock();
        _readLock = readWriteLock.readLock();

        _bucketCount = bucketCount;
        _cache = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; ++i) {
            _cache.add(null);
        }
    }

    public Integer newCacheIdentifier() {
        _writeLock.lock();
        try {
            final Integer cacheIdentifier = _cacheIdentifierCounter.getAndIncrement();
            final int bucketIndex = (cacheIdentifier % _bucketCount);
            final Object oldCache = _cache.set(bucketIndex, new ConcurrentHashMap<>());
            if (oldCache != null) {
                _cachesDestroyed.incrementAndGet();
            }

            return cacheIdentifier;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void cacheTransactionId(final Integer cacheIdentifier, final Sha256Hash transactionHash, final TransactionId transactionId) {
        if ( (transactionHash == null) || (transactionId == null) ) { return; }

        _readLock.lock();
        try {
            final Integer maxValidCacheIdentifier = (_cacheIdentifierCounter.get() - _bucketCount);
            if (cacheIdentifier < maxValidCacheIdentifier) { return; }

            final int bucketIndex = (cacheIdentifier % _bucketCount);
            final ConcurrentHashMap<Sha256Hash, TransactionId> cache = _cache.get(bucketIndex);
            cache.put(transactionHash, transactionId);
        }
        finally {
            _readLock.unlock();
        }
    }

    public TransactionId getTransactionId(final Sha256Hash transactionHash) {
        _readLock.lock();
        try {
            for (final ConcurrentHashMap<Sha256Hash, TransactionId> cache : _cache) {
                if (cache == null) { continue; }

                final TransactionId transactionId = cache.get(transactionHash);
                if (transactionId != null) {
                    _cacheHits.incrementAndGet();
                    return transactionId;
                }
            }

            _cacheMisses.incrementAndGet();
            return null;
        }
        finally {
            _readLock.unlock();
        }
    }

    public void debug(final LogLevel logLevel) {
        Logger.log(logLevel, "cachesDestroyed=" + _cachesDestroyed.get() + ", cacheHits=" + _cacheHits.get() + ", _cacheMisses=" + _cacheMisses.get());
    }
}

package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HashMapCache<KEY, VALUE> implements Cache<KEY, VALUE> {
    public static final Integer DEFAULT_CACHE_SIZE = 65536;
    public static final Integer DISABLED_CACHE_SIZE = 0;

    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final ReentrantReadWriteLock.ReadLock _readLock;

    protected Cache<KEY, VALUE> _masterCache = new DisabledCache<KEY, VALUE>();

    protected final String _name;
    protected final Integer _maxItemCount;
    protected final HashMap<KEY, VALUE> _cache;
    protected final RecentItemTracker<KEY> _recentHashes;
    protected int _itemCount = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    public HashMapCache(final String name, final Integer cacheSize) {
        _name = name;
        _maxItemCount = cacheSize;

        _cache = new HashMap<KEY, VALUE>(_maxItemCount);
        _recentHashes = new RecentItemTracker<KEY>(_maxItemCount);

        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _writeLock = readWriteLock.writeLock();
        _readLock = readWriteLock.readLock();
    }

    @Override
    public void invalidate() {
        _writeLock.lock();

        try {
            _cache.clear();
            _itemCount = 0;
            _recentHashes.clear();

            _masterCache.invalidate();
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public void invalidate(final KEY key) {
        _masterCache.invalidate(key);

        _writeLock.lock();

        try {
            if (_cache.containsKey(key)) {
                _itemCount -= 1;
            }
            _cache.remove(key);
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public void set(final KEY key, final VALUE value) {
        _masterCache.invalidate(key);

        if (_maxItemCount < 1) { return; }

        _writeLock.lock();

        try {
            _recentHashes.markRecent(key);

            final boolean isReplacingExistingItem = _cache.containsKey(key);

            if (_itemCount >= _maxItemCount) {
                final KEY oldestItem = _recentHashes.getOldestItem();
                final VALUE oldestValue = _cache.remove(oldestItem);
                if (oldestValue != null) { // oldestValue can be null if the item was removed from the set...
                    _itemCount -= 1;
                }
            }

            _cache.put(key, value);
            if (! isReplacingExistingItem) {
                _itemCount += 1;
            }
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public VALUE remove(final KEY key) {
        _writeLock.lock();

        try {
            if (! _cache.containsKey(key)) { return null; }

                final VALUE item = _cache.remove(key);
                // _recentHashes.remove(key);
                _itemCount -= 1;
                return item;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setMasterCache(final Cache<KEY, VALUE> cache) {
        _masterCache = cache;
    }

    @Override
    public List<KEY> getKeys() {
        _readLock.lock();

        try {
            final MutableList<KEY> keys = new MutableList<KEY>(_cache.size());
            keys.addAll(_cache.keySet());
            return keys;
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public VALUE get(final KEY key) {
        _readLock.lock();

        try {
            final VALUE cachedItem = _masterCache.get(key);
            if (cachedItem != null) { return cachedItem; }

            if (_maxItemCount < 1) { return null; }

            _cacheQueryCount += 1;

            if (! _cache.containsKey(key)) {
                _cacheMissCount += 1;
                return null;
            }

            _recentHashes.markRecent(key);
            return _cache.get(key);
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public String toString() {
        return (_name + "(" + super.toString() + ")");
    }
}

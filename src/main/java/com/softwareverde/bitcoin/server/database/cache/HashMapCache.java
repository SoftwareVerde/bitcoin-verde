package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class HashMapCache<KEY, VALUE> implements Cache<KEY, VALUE>, MutableCache<KEY, VALUE> {
    public static final Integer DEFAULT_CACHE_SIZE = 65536;
    public static final Integer DISABLED_CACHE_SIZE = 0;

    protected final ReentrantReadWriteLock.WriteLock _writeLock;
    protected final ReentrantReadWriteLock.ReadLock _readLock;

    protected Cache<KEY, VALUE> _masterCache = new DisabledCache<KEY, VALUE>();
    protected Boolean _wasMasterCacheInvalidated = false;

    protected final String _name;
    protected final Integer _maxItemCount;
    protected final HashMap<KEY, VALUE> _cache;
    protected final RecentItemTracker<KEY> _recentHashes;
    protected int _itemCount = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    protected void _resetDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;

        _recentHashes.resetDebug();
    }

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

            _masterCache = new DisabledCache<KEY, VALUE>();
            _wasMasterCacheInvalidated = true;

            _resetDebug();
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void resetDebug() {
        _resetDebug();
    }

    public void invalidateItem(final KEY key) {
        _cache.remove(key);
    }

    @Override
    public void cacheItem(final KEY key, final VALUE value) {
        if (_maxItemCount < 1) { return; }

        _writeLock.lock();

        try {
            _recentHashes.markRecent(key);

            if (_cache.containsKey(key)) {
                return;
            }

            if (_itemCount >= _maxItemCount) {
                final KEY oldestItem = _recentHashes.getOldestItem();
                final VALUE oldestValue = _cache.remove(oldestItem);
                if (oldestValue != null) { // oldestValue can be null if the item was removed from the set...
                    _itemCount -= 1;
                }
            }

            _cache.put(key, value);
            _itemCount += 1;
        }
        finally {
            _writeLock.unlock();
        }
    }

    @Override
    public VALUE removeItem(final KEY key) {

        _writeLock.lock();

        try {
            final VALUE value = _cache.remove(key);
            if (value != null) {
                _itemCount -=1 ;
            }
            return value;
        }
        finally {
            _writeLock.unlock();
        }
    }

    public void setMasterCache(final Cache<KEY, VALUE> cache) {
        _masterCache = cache;
        _wasMasterCacheInvalidated = false;
    }

    @Override
    public Boolean masterCacheWasInvalidated() {
        return _wasMasterCacheInvalidated;
    }

    /**
     * Returns the set of keys cached in this cache.
     *  The returned keys do not include the keys within the MasterCache.
     *  The keys returned are not in order of most-recent access.
     */
    @Override
    public List<KEY> getKeys() {
        _readLock.lock();

        try {
            return new MutableList<KEY>(_cache.keySet());
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public VALUE getCachedItem(final KEY key) {
        _readLock.lock();

        try {
            final VALUE cachedItem = _masterCache.getCachedItem(key);
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
    public Integer getItemCount() {
        _readLock.lock();

        try {
            return (_itemCount + _masterCache.getItemCount());
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public Integer getMaxItemCount() {
        _readLock.lock();

        try {
            return (_maxItemCount + _masterCache.getMaxItemCount());
        }
        finally {
            _readLock.unlock();
        }
    }

    @Override
    public void debug() {
        _readLock.lock();

        try {
            Logger.log(_name + " Cache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _itemCount + "/" + _maxItemCount + " | Time Spent Searching: " + _recentHashes.getMsSpentSearching());
            _masterCache.debug();
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

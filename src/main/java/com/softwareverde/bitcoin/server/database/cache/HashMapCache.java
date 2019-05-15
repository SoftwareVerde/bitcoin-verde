package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.io.Logger;

import java.util.HashMap;

public class HashMapCache<KEY, VALUE> implements Cache<KEY, VALUE>, MutableCache<KEY, VALUE> {
    public static final Integer DEFAULT_CACHE_SIZE = 65536;
    public static final Integer DISABLED_CACHE_SIZE = 0;

    public final Object MUTEX = new Object();

    protected Cache<KEY, VALUE> _masterCache = new DisabledCache<>();
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

        _cache = new HashMap<>(_maxItemCount);
        _recentHashes = new RecentItemTracker<>(_maxItemCount);
    }

    @Override
    public void invalidate() {
        _cache.clear();
        _itemCount = 0;
        _recentHashes.clear();

        _masterCache = new DisabledCache<>();
        _wasMasterCacheInvalidated = true;

        _resetDebug();
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

        synchronized (MUTEX) {
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
    }

    @Override
    public VALUE removeItem(final KEY key) {
        synchronized (MUTEX) {
            final VALUE value = _cache.remove(key);
            if (value != null) {
                _itemCount -=1 ;
            }
            return value;
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
        synchronized (MUTEX) {
            return new MutableList<>(_cache.keySet());
        }
    }

    @Override
    public VALUE getCachedItem(final KEY key) {
        final VALUE cachedItem = _masterCache.getCachedItem(key);
        if (cachedItem != null) { return cachedItem; }

        if (_maxItemCount < 1) { return null; }

        synchronized (MUTEX) {
            _cacheQueryCount += 1;

            if (! _cache.containsKey(key)) {
                _cacheMissCount += 1;
                return null;
            }

            _recentHashes.markRecent(key);
            return _cache.get(key);
        }
    }

    @Override
    public Integer getItemCount() {
        return (_itemCount + _masterCache.getItemCount());
    }

    @Override
    public Integer getMaxItemCount() {
        return (_maxItemCount + _masterCache.getMaxItemCount());
    }

    @Override
    public void debug() {
        Logger.log(_name + " Cache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _itemCount + "/" + _maxItemCount + " | Time Spent Searching: " + _recentHashes.getMsSpentSearching());
        _masterCache.debug();
    }

    @Override
    public String toString() {
        return (_name + "(" + super.toString() + ")");
    }
}

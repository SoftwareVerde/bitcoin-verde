package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.io.Logger;

import java.util.HashMap;

public class Cache<KEY, VALUE> {
    public static final Integer DEFAULT_CACHE_SIZE = 65536;

    public final Object MUTEX = new Object();

    protected final String _name;
    protected final Integer _maxItemCount;
    protected final HashMap<KEY, VALUE> _cache;
    protected final RecentItemTracker<KEY> _recentHashes;
    protected int _itemCount = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    public Cache(final String name, final Integer cacheSize) {
        _name = name;
        _maxItemCount = cacheSize;

        _cache = new HashMap<KEY, VALUE>(_maxItemCount);
        _recentHashes = new RecentItemTracker<KEY>(_maxItemCount);
    }

    public void clear() {
        _cache.clear();
        _itemCount = 0;
        _recentHashes.clear();

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;

        _recentHashes.clearDebug();
    }

    public void invalidateItem(final KEY key) {
        _cache.remove(key);
    }

    public void cacheItem(final KEY key, final VALUE value) {
        synchronized (MUTEX) {
            _recentHashes.markRecent(key);

            if (_cache.containsKey(key)) {
                return;
            }

            if (_itemCount >= _maxItemCount) {
                final KEY oldestItem = _recentHashes.getOldestItem();
                _cache.remove(oldestItem);
                _itemCount -= 1;
            }

            _cache.put(key, value);
            _itemCount += 1;
        }
    }

    public VALUE getCachedItem(final KEY key) {
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

    public Integer getItemCount() {
        return _itemCount;
    }

    public Integer getMaxItemCount() {
        return _maxItemCount;
    }

    public void debug() {
        Logger.log(_name + " Cache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _itemCount + "/" + _maxItemCount + " | Time Spent Searching: " + _recentHashes.getMsSpentSearching());
    }
}

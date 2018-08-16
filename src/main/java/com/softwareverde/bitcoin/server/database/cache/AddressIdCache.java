package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.io.Logger;

import java.util.HashMap;

public class AddressIdCache {
    public static final Integer DEFAULT_CACHE_SIZE = 262144;

    public final Object MUTEX = new Object();

    protected final Integer _cacheMaxSize = DEFAULT_CACHE_SIZE;
    protected final HashMap<String, AddressId> _cache = new HashMap<String, AddressId>(_cacheMaxSize);
    protected final RecentItemTracker<String> _recentAddresses = new RecentItemTracker<String>(_cacheMaxSize);

    protected int _cacheSize = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    public void clear() {
        _recentAddresses.clear();
        _cache.clear();
        _cacheSize = 0;
        _recentAddresses.clear();

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;

        _recentAddresses.clearDebug();
    }

    public void cacheAddressId(final AddressId addressId, final String address) {
        synchronized (MUTEX) {
            _recentAddresses.markRecent(address);

            if (_cache.containsKey(address)) {
                return;
            }

            if (_cacheSize >= _cacheMaxSize) {
                final String oldestAddress = _recentAddresses.getOldestItem();
                _cache.remove(oldestAddress);
                _cacheSize -= 1;
            }

            _cache.put(address, addressId);
            _cacheSize += 1;
        }
    }

    public AddressId getCachedAddressId(final String address) {
        synchronized (MUTEX) {
            _cacheQueryCount += 1;

            if (! _cache.containsKey(address)) {
                _cacheMissCount += 1;
                return null;
            }

            _recentAddresses.markRecent(address);
            return _cache.get(address);
        }
    }

    public Integer getSize() {
        return _cache.size();
    }

    public void debug() {
        Logger.log("AddressCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _cache.size() + " | Time Spent Searching: " + _recentAddresses.getMsSpentSearching());
    }
}

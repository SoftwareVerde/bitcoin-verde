package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class DisabledCache<KEY, VALUE> implements Cache<KEY, VALUE> {

    protected final Cache<KEY, VALUE> _masterCache;

    public DisabledCache() {
        _masterCache = null;
    }

    public DisabledCache(final Cache<KEY, VALUE> masterCache) {
        _masterCache = masterCache;
    }

    @Override
    public List<KEY> getKeys() {
        return new MutableList<KEY>(0);
    }

    @Override
    public VALUE get(final KEY key) { return null; }

    @Override
    public void set(final KEY key, final VALUE value) {
        if (_masterCache != null) {
            _masterCache.invalidate(key);
        }
    }

    @Override
    public VALUE remove(final KEY key) {
        return null;
    }

    @Override
    public void invalidate(final KEY key) {
        if (_masterCache != null) {
            _masterCache.invalidate(key);
        }
    }

    @Override
    public void invalidate() {
        if (_masterCache != null) {
            _masterCache.invalidate();
        }
    }
}

package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class EmptyCache<KEY, VALUE> implements Cache<KEY, VALUE> {
    public EmptyCache() { }

    public void clear() { }

    public void clearDebug() { }

    protected void _clearDebug() { }

    public void invalidateItem(final KEY key) { }

    public void cacheItem(final KEY key, final VALUE value) { }

    @Override
    public Boolean masterCacheWasInvalidated() { return false; }

    @Override
    public List<KEY> getKeys() {
        return new MutableList<KEY>(0);
    }

    @Override
    public VALUE getCachedItem(final KEY key) { return null; }

    @Override
    public Integer getItemCount() {
        return 0;
    }

    @Override
    public Integer getMaxItemCount() {
        return 0;
    }

    @Override
    public void debug() { }
}

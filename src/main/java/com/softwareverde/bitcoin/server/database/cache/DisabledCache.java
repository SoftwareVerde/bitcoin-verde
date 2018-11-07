package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;

public class DisabledCache<T, S> implements MutableCache<T, S> {
    @Override
    public void cacheItem(final T key, final S value) { }

    @Override
    public S removeItem(final T key) { return null; }

    @Override
    public void invalidate() { }

    @Override
    public Boolean masterCacheWasInvalidated() { return false; }

    @Override
    public List<T> getKeys() { return new MutableList<T>(0); }

    @Override
    public S getCachedItem(final T t) { return null; }

    @Override
    public Integer getItemCount() { return 0; }

    @Override
    public Integer getMaxItemCount() { return 0; }

    @Override
    public void debug() { }
}

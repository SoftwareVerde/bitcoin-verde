package com.softwareverde.bitcoin.server.database.cache;

public interface MutableCache<T, S> extends Cache<T, S> {
    void cacheItem(T key, S value);
    S removeItem(T key);
    void invalidate();
}

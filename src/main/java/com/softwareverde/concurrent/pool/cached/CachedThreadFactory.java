package com.softwareverde.concurrent.pool.cached;

public interface CachedThreadFactory {
    CachedThread newThread(CachedThreadPool cachedThreadPool);
}
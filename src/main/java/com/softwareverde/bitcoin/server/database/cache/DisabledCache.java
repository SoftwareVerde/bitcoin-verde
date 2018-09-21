package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.io.Logger;

/**
 * The DisabledCache does nothing.
 *  The purpose behind this "Cache" is to be a placeholder due to the existing caching paradigm causing vulnerabilities
 *  and inconsistencies due to database rollbacks not being propagated to the cache.
 *  This class, and the current caching paradigm should be revisited since caching provides a substantial performance boost.
 */
public class DisabledCache<KEY, VALUE> extends Cache<KEY, VALUE> {
    public DisabledCache(final String name, final Integer cacheSize) {
        super(name, cacheSize);
    }

    public void clear() { }

    public void clearDebug() { }

    protected void _clearDebug() { }

    public void invalidateItem(final KEY key) { }

    public void cacheItem(final KEY key, final VALUE value) { }

    public VALUE getCachedItem(final KEY key) { return null; }

    public Integer getItemCount() {
        return 0;
    }

    public Integer getMaxItemCount() {
        return 0;
    }

    public void debug() {
        Logger.log(_name + " - DISABLED");
    }
}

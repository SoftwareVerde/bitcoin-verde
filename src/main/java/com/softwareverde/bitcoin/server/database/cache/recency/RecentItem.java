package com.softwareverde.bitcoin.server.database.cache.recency;

import com.softwareverde.util.Util;

class RecentItem<T> {
    public final T item;
    public Long lastAccess = null;

    public RecentItem(final T item) {
        this.item = item;
    }

    @Override
    public int hashCode() {
        return this.item.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof RecentItem)) { return false; }

        final RecentItem recentItem = (RecentItem) object;
        return Util.areEqual(recentItem.item, this.item);
    }
}

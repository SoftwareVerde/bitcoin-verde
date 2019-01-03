package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.constable.list.List;

public interface Cache<KEY, VALUE> {

    Boolean masterCacheWasInvalidated();
    List<KEY> getKeys();

    VALUE getCachedItem(KEY key);
    Integer getItemCount();
    Integer getMaxItemCount();

    void debug();
}

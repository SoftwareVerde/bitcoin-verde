package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.constable.list.List;

public interface Cache<KEY, VALUE> {
    List<KEY> getKeys();

    VALUE get(KEY key);
    void set(KEY key, VALUE value);

    VALUE remove(KEY key);

    void invalidate(KEY key);
    void invalidate();
}

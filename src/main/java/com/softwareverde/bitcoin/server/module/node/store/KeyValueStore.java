package com.softwareverde.bitcoin.server.module.node.store;

public interface KeyValueStore {
    String getString(String key);
    void putString(String key, String value);

    Boolean hasKey(String key);
    void removeKey(String key);

    void clear();
}

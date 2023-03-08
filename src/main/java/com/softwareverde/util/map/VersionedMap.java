package com.softwareverde.util.map;

public interface VersionedMap<Key, Value> extends Map<Key, Value> {
    Value get(Key key, Integer version);
    Boolean containsKey(Key key, Integer version);
    void visit(Visitor<Key, Value> visitor, Integer version);
}

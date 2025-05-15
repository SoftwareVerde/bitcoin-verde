package com.softwareverde.util.map;

import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.map.Map;
import com.softwareverde.util.Tuple;

public interface VersionedMap<Key, Value> extends Map<Key, Value> {
    Value get(Key key, Integer version);
    Boolean containsKey(Key key, Integer version);

    void visit(Visitor<Tuple<Key, Value>> visitor, Integer version);
}

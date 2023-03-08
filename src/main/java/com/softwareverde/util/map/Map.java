package com.softwareverde.util.map;

import com.softwareverde.constable.list.List;
import com.softwareverde.util.Tuple;

public interface Map<Key, Value> {
    interface Visitor<Key, Value> {
        boolean run(Tuple<Key, Value> entry);
    }

    Value get(Key key);
    Boolean containsKey(Key key);
    List<Key> getKeys();
    void visit(Visitor<Key, Value> visitor);
}

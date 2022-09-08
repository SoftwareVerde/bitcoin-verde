package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.util.Tuple;

public interface Map<Key, Value> {
    interface Visitor<Key, Value> {
        boolean run(Tuple<Key, Value> entry);
    }

    Value get(Key key);
    Boolean containsKey(Key key);
    void visit(Visitor<Key, Value> visitor);
}

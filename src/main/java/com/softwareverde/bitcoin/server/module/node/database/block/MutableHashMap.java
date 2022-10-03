package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.constable.list.JavaListWrapper;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Tuple;

import java.util.HashMap;

public class MutableHashMap<Key, Value> implements Map<Key, Value> {
    protected final HashMap<Key, Value> _map;

    public MutableHashMap() {
        _map = new HashMap<>();
    }

    public MutableHashMap(final int initialCapacity) {
        _map = new HashMap<>(initialCapacity);
    }

    public MutableHashMap(final int initialCapacity, final float loadFactor) {
        _map = new HashMap<>(initialCapacity, loadFactor);
    }

    @Override
    public Value get(final Key key) {
        return _map.get(key);
    }

    @Override
    public Boolean containsKey(final Key key) {
        return _map.containsKey(key);
    }

    @Override
    public List<Key> getKeys() {
        return JavaListWrapper.wrap(_map.keySet());
    }

    @Override
    public void visit(final Visitor<Key, Value> visitor) {
        for (final java.util.Map.Entry<Key, Value> mapEntry : _map.entrySet()) {
            final Key key = mapEntry.getKey();
            final Value value = mapEntry.getValue();

            final Tuple<Key, Value> entry = new Tuple<>(key, value);
            final boolean shouldContinue = visitor.run(entry);

            if (entry.second != value) {
                mapEntry.setValue(entry.second);
            }

            if (! shouldContinue) { break; }
        }
    }
}

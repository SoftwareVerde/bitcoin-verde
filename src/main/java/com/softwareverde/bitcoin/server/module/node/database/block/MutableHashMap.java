package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.util.Tuple;

import java.util.HashMap;

public class MutableHashMap<Key, Value> implements Map<Key, Value> {
    public Map<Key, Value> getBaseVersion() {
        return new MutableHashMap<>(_hashMap);
    }

    protected final HashMap<Key, Value> _hashMap;

    protected Integer _version = 0;
    protected final HashMap<Integer, HashMap<Key, Value>> _versions = new HashMap<>(0);

    protected MutableHashMap(final HashMap<Key, Value> hashMap) {
        _hashMap = hashMap;
    }

    public MutableHashMap() {
        _hashMap = new HashMap<>();
    }

    public MutableHashMap(final Integer initialSize) {
        _hashMap = new HashMap<>(initialSize);
    }

    public void pushVersion() {
        _version += 1;
        _versions.put(_version, new HashMap<>());
    }

    public void popVersion() {
        _versions.remove(_version);
        _version -= 1;
    }

    public void applyVersion() {
        final HashMap<Key, Value> versionMap = _versions.remove(_version);
        _hashMap.putAll(versionMap);
    }

    public void put(final Key key, final Value value) {
        if (_version == 0) {
            _hashMap.put(key, value);
        }
        else {
            final HashMap<Key, Value> versionMap = _versions.get(_version);
            versionMap.put(key, value);
        }
    }

    public void clear() {
        _versions.clear();
        _version = 0;
        _hashMap.clear();
    }

    @Override
    public Value get(final Key key) {
        for (int version = _version; version > 0; version -= 1) {
            final HashMap<Key, Value> versionMap = _versions.get(version);
            final Value value = versionMap.get(key);
            if (value != null) { return value; }
            else if (versionMap.containsKey(key)) { return null; } // Key's value was set to null...
        }

        return _hashMap.get(key);
    }

    @Override
    public Boolean containsKey(final Key key) {
        for (int version = _version; version > 0; version -= 1) {
            final HashMap<Key, Value> versionMap = _versions.get(version);
            if (versionMap.containsKey(key)) { return true; }
        }

        return _hashMap.containsKey(key);
    }

    protected Boolean _newerVersionContainsKey(final Key key, final Integer currentVersion) {
        int version = (currentVersion + 1);
        while (true) {
            final HashMap<Key, Value> map = _versions.get(version);
            if (map == null) { break; }

            if (map.containsKey(key)) { return true; }

            version += 1;
        }
        return false;
    }

    @Override
    public void visit(final Visitor<Key, Value> visitor) {
        for (int version = _version; version > 0; version -= 1) {
            final HashMap<Key, Value> versionMap = _versions.get(version);
            for (final java.util.Map.Entry<Key, Value> mapEntry : versionMap.entrySet()) {
                final Key key = mapEntry.getKey();
                final Value value = mapEntry.getValue();

                final boolean keyHasBeenVisited = _newerVersionContainsKey(key, version);
                if (keyHasBeenVisited) { continue; }

                final Tuple<Key, Value> entry = new Tuple<>(key, value);

                final boolean shouldContinue = visitor.run(entry);
                if (! shouldContinue) { return; }

                if (entry.second != value) {
                    mapEntry.setValue(entry.second);
                }
            }
        }

        for (final java.util.Map.Entry<Key, Value> mapEntry : _hashMap.entrySet()) {
            final Key key = mapEntry.getKey();
            final Value value = mapEntry.getValue();

            final boolean keyHasBeenVisited = _newerVersionContainsKey(key, 0);
            if (keyHasBeenVisited) { continue; }

            final Tuple<Key, Value> entry = new Tuple<>(key, value);

            final boolean shouldContinue = visitor.run(entry);
            if (! shouldContinue) { return; }

            if (entry.second != value) {
                mapEntry.setValue(entry.second);
            }
        }
    }
}

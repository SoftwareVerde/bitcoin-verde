package com.softwareverde.util.map;

import com.softwareverde.constable.Visitor;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableJavaMapWrapper;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.constable.set.Set;
import com.softwareverde.constable.set.mutable.MutableHashSet;
import com.softwareverde.util.Tuple;

import java.util.Iterator;

public class MutableVersionedHashMap<Key, Value> implements VersionedMap<Key, Value> {
    protected final MutableMap<Key, Value> _committedValues;

    protected Integer _version = 0;
    protected final MutableMap<Integer, MutableMap<Key, Value>> _stagedValues = new MutableHashMap<>(0);

    public static <Key, Value> MutableVersionedHashMap<Key, Value> wrap(final java.util.HashMap<Key, Value> hashMap) {
        return new MutableVersionedHashMap<>(
            new MutableJavaMapWrapper<>(hashMap) { }
        );
    }

    public static <Key, Value> MutableVersionedHashMap<Key, Value> wrap(final MutableHashMap<Key, Value> hashMap) {
        return new MutableVersionedHashMap<>(hashMap);
    }

    // public static <Key, Value> MutableVersionedHashMap<Key, Value> wrap(final MutableVersionedHashMap<Key, Value> hashMap) {
    //     return new MutableVersionedHashMap<Key, Value>(hashMap);
    // }

    protected Value _get(final Key key, final Integer version) {
        for (int i = version; i > 0; i -= 1) {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(i);
            final Value value = versionMap.get(key);
            if (value != null) { return value; }
            else if (versionMap.containsKey(key)) { return null; } // Key's value was set to null...
        }

        return _committedValues.get(key);
    }

    protected Boolean _containsKey(final Key key, final Integer version) {
        for (int i = version; i > 0; i -= 1) {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(i);
            if (versionMap.containsKey(key)) { return true; }
        }

        return _committedValues.containsKey(key);
    }

    protected void _visit(final Visitor<Tuple<Key, Value>> visitor, final Integer version, final Boolean allowUpdate) {
        for (int i = version; i > 0; i -= 1) {
            final int mapVersion = i;
            final MutableMap<Key, Value> versionMap = _stagedValues.get(mapVersion);

            final MutableMap.MutableVisitor<Key, Value> internalVisitor = new MutableMap.MutableVisitor<>() {
                @Override
                public boolean run(final Tuple<Key, Value> entry) {
                    final Key key = entry.first;

                    final boolean keyHasBeenVisited = _newerVersionContainsKey(key, mapVersion);
                    if (keyHasBeenVisited) { return true; }

                    final boolean shouldContinue = visitor.run(entry);
                    if (! shouldContinue) { return false; }

                    return true;
                }
            };

            if (allowUpdate) {
                versionMap.mutableVisit(internalVisitor);
            }
            else {
                versionMap.visit(internalVisitor);
            }
        }

        final MutableMap.MutableVisitor<Key, Value> internalVisitor = new MutableMap.MutableVisitor<>() {
            @Override
            public boolean run(final Tuple<Key, Value> entry) {
                final Key key = entry.first;

                final boolean keyHasBeenVisited = _newerVersionContainsKey(key, 0);
                if (keyHasBeenVisited) { return true; }

                final boolean shouldContinue = visitor.run(entry);
                if (! shouldContinue) { return false; }

                return true;
            }
        };

        if (allowUpdate) {
            _committedValues.mutableVisit(internalVisitor);
        }
        else {
            _committedValues.visit(internalVisitor);
        }
    }

    protected Boolean _newerVersionContainsKey(final Key key, final Integer currentVersion) {
        int version = (currentVersion + 1);
        while (true) {
            final MutableMap<Key, Value> map = _stagedValues.get(version);
            if (map == null) { break; }

            if (map.containsKey(key)) { return true; }

            version += 1;
        }
        return false;
    }

    protected MutableVersionedHashMap(final MutableMap<Key, Value> hashMap) {
        _committedValues = hashMap;
    }

    public MutableVersionedHashMap() {
        _committedValues = new MutableHashMap<>();
    }

    public MutableVersionedHashMap(final Integer initialSize) {
        _committedValues = new MutableHashMap<>(initialSize);
    }

    public void pushVersion() {
        _version += 1;
        _stagedValues.put(_version, new MutableHashMap<>());
    }

    public void popVersion() {
        if (_version < 1) { throw new RuntimeException("Attempted to pop non-existent version."); }

        _stagedValues.remove(_version);
        _version -= 1;
    }

    public void applyVersion() {
        if (_version < 1) { throw new RuntimeException("Attempted to apply non-existent version."); }

        final MutableMap<Key, Value> versionMap = _stagedValues.remove(_version);
        _committedValues.putAll(versionMap);
        _version -= 1;
    }

    public void applyStagedChanges(final MutableVersionedHashMap<Key, Value> hashMap) {
        // Commit any current staged changes...
        while (_version > 0) {
            final MutableMap<Key, Value> versionMap = _stagedValues.remove(_version);
            _committedValues.putAll(versionMap);
            _version -= 1;
        }

        for (int i = 1; i <= hashMap._version; i += 1) {
            final MutableMap<Key, Value> versionMap = hashMap._stagedValues.get(i);
            _committedValues.putAll(versionMap);
        }
    }

    public void put(final Key key, final Value value) {
        if (_version == 0) {
            _committedValues.put(key, value);
        }
        else {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(_version);
            versionMap.put(key, value);
        }
    }

    public void clear() {
        _stagedValues.clear();
        _version = 0;
        _committedValues.clear();
    }

    @Override
    public Value get(final Key key, final Integer version) {
        return _get(key, Math.min(_version, version));
    }

    public Value get(final Key key) {
        return _get(key, _version);
    }

    @Override
    public Boolean containsKey(final Key key, final Integer version) {
        return _containsKey(key, Math.min(_version, version));
    }

    public Boolean containsKey(final Key key) {
        return _containsKey(key, _version);
    }

    public Integer getCount() {
        int itemCount = _committedValues.getCount();

        for (int i = _version; i > 0; i -= 1) {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(i);
            itemCount += versionMap.getCount();
        }

        return itemCount;
    }

    public Set<Key> getKeys() {
        final MutableHashSet<Key> keySet = new MutableHashSet<>();

        for (int i = _version; i > 0; i -= 1) {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(i);
            for (final Key key : versionMap.getKeys()) {
                keySet.add(key);
            }
        }

        for (final Key key : _committedValues.getKeys()) {
            keySet.add(key);
        }

        return keySet;
    }

    @Override
    public Set<Value> getValues() {
        final MutableHashSet<Value> valueSet = new MutableHashSet<>();

        for (int i = _version; i > 0; i -= 1) {
            final MutableMap<Key, Value> versionMap = _stagedValues.get(i);
            for (final Value value : versionMap.getValues()) {
                valueSet.add(value);
            }
        }

        for (final Value value : _committedValues.getValues()) {
            valueSet.add(value);
        }

        return valueSet;
    }

    @Override
    public void visit(final Visitor<Tuple<Key, Value>> visitor, final Integer version) {
        _visit(visitor, Math.min(_version, version), false);
    }

    public void visit(final Visitor<Tuple<Key, Value>> visitor) {
        _visit(visitor, _version, false);
    }

    @Override
    public Iterator<Tuple<Key, Value>> iterator() {
        return new VersionedHashMapIterator<>(this);
    }
}

class VersionedHashMapIterator<Key, Value> implements Iterator<Tuple<Key, Value>> {
    protected final MutableVersionedHashMap<Key, Value> _map;
    protected int _version;
    protected Iterator<Tuple<Key, Value>> _iterator;

    public VersionedHashMapIterator(final MutableVersionedHashMap<Key, Value> versionedHashMap) {
        _map = versionedHashMap;
        _version = versionedHashMap._version;

        _iterator = versionedHashMap._committedValues.iterator();
        int version = versionedHashMap._version;
        while (version > 0) {
            final MutableMap<Key, Value> stagedValueMap = versionedHashMap._stagedValues.get(version);
            if (stagedValueMap != null) {
                _iterator = stagedValueMap.iterator();
                break;
            }
            --version;
        }
    }

    @Override
    public boolean hasNext() {
        if (_iterator.hasNext()) {
            return true;
        }
        else if (_version <= 0) { return false; }

        int version = (_version - 1);
        while (version > 0) {
            final MutableMap<Key, Value> stagedValues = _map._stagedValues.get(version);
            if (stagedValues == null) { continue; }

            final Iterator<Tuple<Key, Value>> iterator = stagedValues.iterator();
            if (iterator.hasNext()) { return true; }

            version -= 1;
        }

        final Iterator<Tuple<Key, Value>> iterator = _map._committedValues.iterator();
        return iterator.hasNext();
    }

    @Override
    public Tuple<Key, Value> next() {
        if (_iterator.hasNext()) {
            return _iterator.next();
        }
        else if (_version <= 0) { throw new IndexOutOfBoundsException(); }

        _iterator = null;

        _version -= 1;
        while (_version > 0) {
            final MutableMap<Key, Value> stagedValues = _map._stagedValues.get(_version);
            if (stagedValues == null) { continue; }

            _iterator = stagedValues.iterator();
            if (_iterator.hasNext()) {
                return _iterator.next();
            }

            _version -= 1;
        }

        _iterator = _map._committedValues.iterator();
        return _iterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
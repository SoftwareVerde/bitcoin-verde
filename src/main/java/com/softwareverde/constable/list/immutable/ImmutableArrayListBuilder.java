package com.softwareverde.constable.list.immutable;

import com.softwareverde.constable.list.List;

public class ImmutableArrayListBuilder<T> {

    protected final Integer _objectCount;
    private T[] _objectArray;
    private int _index = 0;

    @SuppressWarnings("unchecked")
    protected void _initObjectArray() {
        if (_objectArray != null) { return; }

        _objectArray = (T[]) (new Object[_objectCount]);
        _index = 0;
    }

    public ImmutableArrayListBuilder(final int exactSize) {
        _objectCount = exactSize;
        _initObjectArray();
    }

    public void add(final T item) {
        _initObjectArray();

        if (_index >= _objectArray.length) { return; }

        _objectArray[_index] = item;
        _index += 1;
    }

    public int getCount() {
        return _index;
    }

    public void addAll(final java.util.Collection<T> collection) {
        _initObjectArray();

        for (final T item : collection) {
            if (_index >= _objectArray.length) { return; }

            _objectArray[_index] = item;
            _index += 1;
        }
    }

    public void addAll(final List<T> collection) {
        _initObjectArray();

        for (final T item : collection) {
            if (_index >= _objectArray.length) { return; }

            _objectArray[_index] = item;
            _index += 1;
        }
    }

    public ImmutableArrayList<T> build() {
        final ImmutableArrayList<T> immutableList = ImmutableArrayList.wrap(_objectArray);

        _objectArray = null;
        _index = 0;

        return immutableList;
    }
}

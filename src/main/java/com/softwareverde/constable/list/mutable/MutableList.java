package com.softwareverde.constable.list.mutable;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class MutableList<T> implements List<T> {
    private final ArrayList<T> _items;

    public MutableList(final Collection<T> items) {
        _items = new ArrayList<T>(items);
    }

    public MutableList() {
        _items = new ArrayList<T>();
    }

    public MutableList(final int initialCapacity) {
        _items = new ArrayList<T>(initialCapacity);
    }

    public void add(final T item) {
        _items.add(item);
    }

    public void set(final int index, final T item) {
        _items.set(index, item);
    }

    public void addAll(final List<T> items) {
        // TODO: Improve the performance of this, since ArrayList.add() can't intelligently grow...
        for (final T item : items) {
            _items.add(item);
        }
    }

    public T remove(final int index) {
        return _items.remove(index);
    }

    public void clear() {
        _items.clear();
    }

    @Override
    public T get(final int index) {
        return _items.get(index);
    }

    @Override
    public int getSize() {
        return _items.size();
    }

    @Override
    public boolean isEmpty() {
        return _items.isEmpty();
    }

    @Override
    public boolean contains(final T item) {
        return _items.contains(item);
    }

    @Override
    public int indexOf(final T item) {
        return _items.indexOf(item);
    }

    @Override
    public ImmutableList<T> asConst() {
        return new ImmutableList<T>(this);
    }

    @Override
    public Iterator<T> iterator() {
        return _items.iterator();
    }
}

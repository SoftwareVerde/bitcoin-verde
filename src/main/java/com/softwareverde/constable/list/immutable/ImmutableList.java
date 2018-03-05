package com.softwareverde.constable.list.immutable;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class ImmutableList<T> implements List<T>, Const {
    private final java.util.List<T> _list;

    /**
     * Creates an ImmutableList using the provided list as its implementation.
     *  Use this function carefully, as leaking a reference to the provided list will break immutability.
     */
    protected static <T> ImmutableList<T> wrap(final java.util.List<T> list) {
        return new ImmutableList<T>(list);
    }

    private ImmutableList(final java.util.List<T> list) {
        _list = list;
    }

    public ImmutableList(final Collection<T> list) {
        _list = new ArrayList<T>(list);
    }

    public ImmutableList(final List<T> list) {
        _list = new ArrayList<T>(list.getSize());
        for (final T item : list) {
            _list.add(item);
        }
    }

    @Override
    public T get(final int index) {
        return _list.get(index);
    }

    @Override
    public int getSize() {
        return _list.size();
    }

    @Override
    public boolean isEmpty() {
        return _list.isEmpty();
    }

    @Override
    public boolean contains(final T item) {
        return _list.contains(item);
    }

    @Override
    public int indexOf(final T item) {
        return _list.indexOf(item);
    }

    @Override
    public Iterator<T> iterator() {
        return new ImmutableListIterator<T>(this);
    }

    @Override
    public ImmutableList<T> asConst() {
        return this;
    }
}

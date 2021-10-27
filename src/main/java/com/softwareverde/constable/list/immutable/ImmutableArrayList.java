package com.softwareverde.constable.list.immutable;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.Iterator;

public class ImmutableArrayList<T> implements List<T>, Const {

    /**
     * Creates an ImmutableArrayList using the provided list as its implementation.
     *  Use this function carefully, as leaking a reference to the provided list will break immutability.
     */
    protected static <T> ImmutableArrayList<T> wrap(final T[] objectArray) {
        return new ImmutableArrayList<>(objectArray);
    }

    public static <T> ImmutableArrayList<T> copyOf(final T[] objectArray) {
        final T[] copiedObjectArray = Util.copyArray(objectArray);
        return new ImmutableArrayList<>(copiedObjectArray);
    }

    protected final T[] _items;

    protected ImmutableArrayList(final T[] items) {
        _items = items;
    }

    @SuppressWarnings("unchecked")
    public ImmutableArrayList(final Collection<T> list) {
        final int itemCount = list.size();

        _items = (T[]) (new Object[itemCount]);

        int index = 0;
        for (final T item : list) {
            _items[index] = item;
            index += 1;
        }
    }

    @SuppressWarnings("unchecked")
    public ImmutableArrayList(final List<T> list) {
        final int itemCount = list.getCount();

        _items = (T[]) (new Object[itemCount]);

        for (int i = 0; i < itemCount; ++i) {
            final T item = list.get(i);
            _items[i] = item;
        }
    }

    @Override
    public T get(final int index) {
        return _items[index];
    }

    @Override
    @Deprecated
    public int getSize() {
        return _items.length;
    }

    @Override
    public boolean isEmpty() {
        return (_items.length == 0);
    }

    @Override
    public boolean contains(final T itemNeedle) {
        for (final T item : _items) {
            if (Util.areEqual(itemNeedle, item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int indexOf(final T item) {
        for (int i = 0; i < _items.length; ++i) {
            if (Util.areEqual(_items[i], item)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public ImmutableList<T> asConst() {
        return new ImmutableList<>(this);
    }

    @Override
    public Iterator<T> iterator() {
        return new ImmutableListIterator<>(this);
    }
}

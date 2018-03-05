package com.softwareverde.constable.list.immutable;

import com.softwareverde.constable.list.List;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class ImmutableListIterator<T> implements Iterator<T> {
    private final List<T> _items;
    private int _index = 0;

    ImmutableListIterator(final List<T> items) {
        _items = items;
    }

    @Override
    public boolean hasNext() {
        return (_index < _items.getSize());
    }

    @Override
    public T next() {
        if (! (_index < _items.getSize())) {
            throw new NoSuchElementException();
        }

        final T item = _items.get(_index);
        _index += 1;

        return item;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}

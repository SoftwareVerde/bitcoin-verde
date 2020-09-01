package com.softwareverde.constable.list;

import java.util.Iterator;

class ImmutableJavaUtilListIterator<T> implements Iterator<T> {
    private final Iterator<T> _iterator;

    public ImmutableJavaUtilListIterator(final Iterable<T> items) {
        _iterator = items.iterator();
    }

    @Override
    public boolean hasNext() {
        return _iterator.hasNext();
    }

    @Override
    public T next() {
        return _iterator.next();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}

package com.softwareverde.constable.list;

import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.util.Util;

import java.util.Iterator;

public class JavaListWrapper<T> implements List<T> {
    public static <T> List<T> wrap(final Iterable<T> javaIterable) {
        if (javaIterable instanceof List) {
            return (List<T>) javaIterable;
        }

        return new JavaListWrapper<T>(javaIterable);
    }

    protected final Iterable<T> _javaUtilIterable;

    protected JavaListWrapper(final Iterable<T> javaUtilIterable) {
        _javaUtilIterable = javaUtilIterable;
    }

    @Override
    public T get(final int index) {
        if (index < 0) { throw new IndexOutOfBoundsException(); }

        int i = 0;
        for (final T item : _javaUtilIterable) {
            if (i == index) {
                return item;
            }
            i += 1;
        }
        throw new IndexOutOfBoundsException();
    }

    @Override
    public int getSize() {
        int i = 0;
        for (final T item : _javaUtilIterable) {
            i += 1;
        }
        return i;
    }

    @Override
    public boolean isEmpty() {
        for (final T item : _javaUtilIterable) {
            return false;
        }
        return true;
    }

    @Override
    public boolean contains(final T desiredItem) {
        for (final T item : _javaUtilIterable) {
            if (Util.areEqual(desiredItem, item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int indexOf(final T desiredItem) {
        int i = 0;
        for (final T item : _javaUtilIterable) {
            if (Util.areEqual(desiredItem, item)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ImmutableList<T> asConst() {
        final ImmutableListBuilder<T> immutableListBuilder = new ImmutableListBuilder<T>();
        for (final T item : _javaUtilIterable) {
            immutableListBuilder.add(item);
        }
        return immutableListBuilder.build();
    }

    @Override
    public Iterator<T> iterator() {
        return new ImmutableJavaUtilListIterator<T>(_javaUtilIterable);
    }
}

package com.softwareverde.constable.list.immutable;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.list.List;

public class ImmutableListBuilder<T> {
    public static <T extends Constable<C>, C extends Const> ImmutableList<C> newConstListOfConstItems(final List<T> list) {
        final ImmutableListBuilder<C> immutableListBuilder = new ImmutableListBuilder<C>(list.getSize());
        for (final T item : list) {
            final C constItem = item.asConst();
            immutableListBuilder.add(constItem);
        }
        return immutableListBuilder.build();
    }

    private java.util.ArrayList<T> _javaList;

    public ImmutableListBuilder(final int size) {
        _javaList = new java.util.ArrayList<T>(size);
    }

    public ImmutableListBuilder() {
        _javaList = new java.util.ArrayList<T>();
    }

    public void add(final T item) {
        _javaList.add(item);
    }

    public int getCount() {
        return _javaList.size();
    }

    public void addAll(final java.util.Collection<T> collection) {
        _javaList.addAll(collection);
    }

    public void addAll(final List<T> collection) {
        final int newTotalSize = (_javaList.size() + collection.getSize());
        _javaList.ensureCapacity(newTotalSize);

        for (final T item : collection) {
            _javaList.add(item);
        }
    }

    public ImmutableList<T> build() {
        final ImmutableList<T> immutableList = ImmutableList.wrap(_javaList);
        _javaList = new java.util.ArrayList<T>();
        return immutableList;
    }
}

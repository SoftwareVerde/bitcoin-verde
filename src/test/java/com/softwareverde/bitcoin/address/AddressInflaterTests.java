package com.softwareverde.bitcoin.address;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.immutable.ImmutableListIterator;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;

public class AddressInflaterTests {
    @Test
    public void should_inflate_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final Address address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNotNull(address);
    }

    @Test
    public void should_return_null_for_invalid_base32_address() {
        // Setup
        final AddressInflater addressInflater = new AddressInflater();
        final String base32AddressString = "http://bitcoincash:qqswr73n8gzgsygazzfn9qm3qk46dtescsyrzewzuj";

        // Action
        final Address address = addressInflater.fromBase32Check(base32AddressString);

        // Assert
        Assert.assertNull(address);
    }

    @Test
    public void foo() {
        final int count = (1024 * 1024 * 100);
        final NanoTimer nanoTimer = new NanoTimer();

        final Integer[] objectArray = new Integer[count];
        final ImmutableListBuilder<Integer> immutableListBuilder = new ImmutableListBuilder<Integer>(count);

        for (int i = 0; i < count; ++i) {
            // objectArray[i] = (int) (Math.random() * Integer.MAX_VALUE);
            // immutableListBuilder.add((int) (Math.random() * Integer.MAX_VALUE));
            objectArray[i] = i;
            immutableListBuilder.add(i);
        }

        final List<Integer> objectList = immutableListBuilder.build();
        final List<Integer> objectArrayList = new ImmutableArrayList<Integer>(objectArray);

        final int half = (Integer.MAX_VALUE / 2);

        {
            nanoTimer.start();
            int aboveHalfCount = 0;
            for (int i = 0; i < count; ++i) {
                final Integer item = objectArray[i];
                if (item > half) {
                    aboveHalfCount += 1;
                }
            }
            nanoTimer.stop();
            System.out.println(aboveHalfCount + " - Java Array: " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }

        {
            nanoTimer.start();
            int aboveHalfCount = 0;
            for (int i = 0; i < count; ++i) {
                final Integer item = objectList.get(i);
                if (item > half) {
                    aboveHalfCount += 1;
                }
            }
            nanoTimer.stop();
            System.out.println(aboveHalfCount + " - Constable/JavaArray List: " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }

        {
            nanoTimer.start();
            int aboveHalfCount = 0;
            for (int i = 0; i < count; ++i) {
                final Integer item = objectArrayList.get(i);
                if (item > half) {
                    aboveHalfCount += 1;
                }
            }
            nanoTimer.stop();
            System.out.println(aboveHalfCount + " - Constable ArrayList: " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }
}

class ImmutableArrayList<T> implements List<T>, Const {

    protected final T[] _items;

    public ImmutableArrayList(final T[] items) {
        _items = Util.copyArray(items);
    }

    @Override
    public T get(final int index) {
        return _items[index];
    }

    @Override
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
        return 0;
    }

    @Override
    public ImmutableList<T> asConst() {
        return new ImmutableList<T>(this);
    }

    @Override
    public Iterator<T> iterator() {
        return new ImmutableListIterator<T>(this);
    }
}

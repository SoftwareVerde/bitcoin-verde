package com.softwareverde.constable.list;

import com.softwareverde.util.Util;

import java.util.Comparator;

public class ListUtil {
    protected static <T> Integer _binarySearch(final List<T> haystack, final T needle, final Comparator<T> comparator, final int minIndex, final int maxIndex) {
        if (maxIndex < minIndex) { return -1; }

        final int middleIndex = (minIndex + ((maxIndex - minIndex) / 2));
        final T midItem = haystack.get(middleIndex);

        if (Util.areEqual(needle, midItem)) { return middleIndex; }

        if (comparator.compare(midItem, needle) > 0) {
            return ListUtil._binarySearch(haystack, needle, comparator, minIndex, (middleIndex - 1));
        }

        return ListUtil._binarySearch(haystack, needle, comparator, (middleIndex + 1), maxIndex);
    }

    public static <T> Integer binarySearch(final List<T> sortedList, final T needle, final Comparator<T> comparator) {
        return ListUtil._binarySearch(sortedList, needle, comparator, 0, (sortedList.getCount() - 1));
    }
}

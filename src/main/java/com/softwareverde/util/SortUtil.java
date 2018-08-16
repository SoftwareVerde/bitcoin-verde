package com.softwareverde.util;

import java.util.Comparator;
import java.util.List;

public class SortUtil {
    public static final Comparator<Long> longComparator = new Comparator<Long>() {
        @Override
        public int compare(final Long o1, final Long o2) {
            return o1.compareTo(o2);
        }
    };

    public static final Comparator<Integer> integerComparator = new Comparator<Integer>() {
        @Override
        public int compare(final Integer o1, final Integer o2) {
            return o1.compareTo(o2);
        }
    };

    public static final Comparator<String> stringComparator = new Comparator<String>() {
        @Override
        public int compare(final String o1, final String o2) {
            return o1.compareTo(o2);
        }
    };

    // NOTE: InsertionSort is efficient for resorting nearly-sorted lists...
    public static <T> void insertionSort(final List<T> list, final Comparator<T> comparator) {
        final int n = list.size();
        for (int i = 1; i < n; ++i) {
            final T keyValue = list.get(i);

            int j = i - 1;
            while (j >= 0 && comparator.compare(list.get(j), keyValue) > 0) {
                list.set((j + 1), list.get(j));
                j = (j - 1);
            }
            list.set((j + 1), keyValue);
        }
    }

    protected SortUtil() { }
}

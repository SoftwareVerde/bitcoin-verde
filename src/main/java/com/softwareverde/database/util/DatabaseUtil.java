package com.softwareverde.database.util;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.Row;
import com.softwareverde.util.Util;

import java.util.Collection;
import java.util.Map;

public class DatabaseUtil {
    protected DatabaseUtil() { }

    protected static <T> String _createInClause(final Iterable<T> list, final Map<T, ?> keyMap) {
        final StringBuilder stringBuilder = new StringBuilder();

        String prefix = "";
        for (final T item : list) {
            stringBuilder.append(prefix);
            stringBuilder.append("'");
            stringBuilder.append(item);
            stringBuilder.append("'");
            prefix = ",";

            if (keyMap != null) {
                keyMap.put(item, null);
            }
        }

        return stringBuilder.toString();
    }

    /**
     * Creates a concatenated string for use within SQL "in" clauses.
     *  e.g. SELECT id FROM table WHERE value IN (?)
     *  NOTE: The order of the rows returned are NOT guaranteed to be in the same order as the provided list.
     *      Instead, consider using DatabaseUtil::createInClause(List, Map) in conjunction with DatabaseUtil::sortMappedRows(List, List, Map).
     */
    public static String createInClause(final List<?> list) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list, null);
    }

    public static String createInClause(final Collection<?> list) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list, null);
    }

    public static <T> String createInClause(final List<T> list, final Map<T, ?> keyMap) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list, keyMap);
    }

    public static <T> String createInClause(final Collection<T> list, final Map<T, ?> keyMap) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list, keyMap);
    }

    public static <T, V> List<V> sortMappedRows(final java.util.List<Row> rows, final List<T> rowOrder, final Map<T, V> keyMap) {
        final Integer itemCount = rows.size();
        if (! Util.areEqual(itemCount, rowOrder.getSize())) { return null; }

        final ImmutableListBuilder<V> lockingScriptsBuilder = new ImmutableListBuilder<V>(itemCount);
        for (final T key : rowOrder) {
            final V value = keyMap.get(key);
            lockingScriptsBuilder.add(value);
        }

        return lockingScriptsBuilder.build();
    }
}

package com.softwareverde.database.util;

import com.softwareverde.constable.list.List;

import java.util.Collection;

public class DatabaseUtil {
    protected DatabaseUtil() { }

    protected static String _createInClause(final Iterable<?> list) {
        final StringBuilder stringBuilder = new StringBuilder();

        String prefix = "";
        for (final Object item : list) {
            stringBuilder.append(prefix);
            stringBuilder.append("'");
            stringBuilder.append(item);
            stringBuilder.append("'");
            prefix = ",";
        }

        return stringBuilder.toString();
    }

    public static String createInClause(final List<?> list) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list);
    }

    public static String createInClause(final Collection<?> list) {
        if (list.isEmpty()) { return "NULL"; }
        return _createInClause(list);
    }
}

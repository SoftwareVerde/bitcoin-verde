package com.softwareverde.database.util;

import com.softwareverde.constable.list.List;

public class DatabaseUtil {
    protected DatabaseUtil() { }

    public static String createInClause(final List<?> list) {
        if (list.isEmpty()) { return "NULL"; }

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
}

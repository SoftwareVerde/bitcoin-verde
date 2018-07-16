package com.softwareverde.bitcoin.util;

public class StringUtil extends com.softwareverde.util.StringUtil {
    public static String formatNumberString(final Long value) {
        if (value == null) { return null; }
        return _numberFormatter.format(value);
    }

    protected StringUtil() {
        super();
    }
}

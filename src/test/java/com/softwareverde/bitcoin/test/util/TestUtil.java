package com.softwareverde.bitcoin.test.util;

import com.softwareverde.database.query.parameter.TypedParameter;
import com.softwareverde.database.row.Row;
import com.softwareverde.util.HexUtil;
import com.softwareverde.util.ReflectionUtil;

import java.util.HashMap;
import java.util.Map;

public class TestUtil {
    private static void _fail(final String message) {
        throw new AssertionError(message);
    }

    /**
     * Throws an AssertionError if the string does not match the matching pattern provided by expectedMaskedString.
     *  Mask Pattern:
     *      'X' denotes characters are masked but must be present
     */
    public static void assertMatchesMaskedHexString(final String stringMask, final String string) {
        if (string == null) {
            _fail("Unexpected null value within value.");
        }

        final String uppercaseString = string.toUpperCase();
        final int uppercaseStringLength = uppercaseString.length();

        final String uppercaseStrippedStringMask = stringMask.replaceAll("\\s+", "").toUpperCase();
        final int uppercaseStrippedStringMaskLength = uppercaseStrippedStringMask.length();

        for (int i = 0; i < uppercaseStrippedStringMask.length(); ++i) {
            final char maskCharacter = uppercaseStrippedStringMask.charAt(i);

            if (i >= uppercaseStringLength) {
                _fail("Provided value does not match expected value. (Expected length: "+ (uppercaseStrippedStringMaskLength/2) +", found: "+ (uppercaseStringLength/2) +".)\n" + stringMask + "\n" + string);
            }

            if (maskCharacter == 'X') { continue; }

            final char stringCharacter = uppercaseString.charAt(i);
            if (maskCharacter != stringCharacter) {
                _fail("Provided value does not match expected value.\n" + stringMask + "\n" + string);
            }
        }
    }

    public static void assertMatchesMaskedHexString(final String stringMask, final byte[] data) {
        assertMatchesMaskedHexString(stringMask, HexUtil.toHexString(data));
    }

    public static void assertEqual(final byte[] expectedBytes, final byte[] bytes) {
        assertMatchesMaskedHexString(HexUtil.toHexString(expectedBytes), HexUtil.toHexString(bytes));
    }

    public static Map<String, Object>[] debugQuery(final java.util.List<Row> rows) {
        final Map<String, Object>[] debugRows = new Map[rows.size()];
        int i = 0;
        for (final Row row : rows) {
            final Map<String, TypedParameter> values = ReflectionUtil.getValue(row, "_columnValues");
            final HashMap<String, Object> debugValues = new HashMap<String, Object>();
            for (final String columnName : row.getColumnNames()) {
                final TypedParameter typedParameter = values.get(columnName);
                if (typedParameter.value instanceof byte[]) {
                    debugValues.put(columnName, HexUtil.toHexString((byte[]) typedParameter.value));
                }
                else {
                    debugValues.put(columnName, typedParameter.value);
                }
            }
            debugRows[i] = debugValues;
            i += 1;
        }
        return debugRows;
    }
}

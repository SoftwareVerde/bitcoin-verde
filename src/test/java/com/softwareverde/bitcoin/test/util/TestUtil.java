package com.softwareverde.bitcoin.test.util;

import com.softwareverde.util.HexUtil;

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
        final Integer uppercaseStringLength = uppercaseString.length();

        final String uppercaseStrippedStringMask = stringMask.replaceAll("\\s+", "").toUpperCase();
        final Integer uppercaseStrippedStringMaskLength = uppercaseStrippedStringMask.length();

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
}

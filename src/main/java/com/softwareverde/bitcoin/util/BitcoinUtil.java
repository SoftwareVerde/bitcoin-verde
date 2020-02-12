package com.softwareverde.bitcoin.util;

import com.softwareverde.logging.Logger;
import com.softwareverde.util.Base32Util;
import com.softwareverde.util.Base58Util;

public class BitcoinUtil {

    public static String toBase58String(final byte[] bytes) {
        return Base58Util.toBase58String(bytes);
    }

    public static byte[] base58StringToBytes(final String base58String) {
        return Base58Util.base58StringToByteArray(base58String);
    }

    public static String toBase32String(final byte[] bytes) {
        return Base32Util.toBase32String(bytes);
    }

    public static byte[] base32StringToBytes(final String base58String) {
        return Base32Util.base32StringToByteArray(base58String);
    }

    public static String reverseEndianString(final String string) {
        final int charCount = string.length();
        final char[] reverseArray = new char[charCount];
        for (int i = 0; i < (charCount / 2); ++i) {
            int index = (charCount - (i * 2)) - 1;
            reverseArray[i * 2] = string.charAt(index - 1);
            reverseArray[(i * 2) + 1] = string.charAt(index);
        }
        return new String(reverseArray);
    }

    /**
     * Returns the Log (base2) of x, rounded down.
     *  Ex: log2(65280) -> 15 (Mathematically this value is 15.99...)
     */
    public static int log2(int x) {
        int log = 0;

        if ((x & 0xffff0000) != 0) {
            x >>>= 16;
            log = 16;
        }

        if (x >= 256) {
            x >>>= 8;
            log += 8;
        }

        if (x >= 16) {
            x >>>= 4;
            log += 4;
        }

        if (x >= 4) {
            x >>>= 2;
            log += 2;
        }

        return log + (x >>> 1);
    }

    public static void exitFailure() {
        Logger.flush();
        System.exit(1);
    }

    public static void exitSuccess() {
        Logger.flush();
        System.exit(0);
    }

    protected BitcoinUtil() { }

}

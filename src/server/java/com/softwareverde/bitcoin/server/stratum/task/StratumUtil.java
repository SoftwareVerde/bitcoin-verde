package com.softwareverde.bitcoin.server.stratum.task;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class StratumUtil {
    protected static String _createByteString(final char a, final char b) {
        return String.valueOf(a) + b;
    }

    public static ByteArray swabBytes(final ByteArray input) {
        // 00 01 02 03 | 04 05 06 07 -> 03 02 01 00 | 07 06 05 04
        final int byteCount = input.getByteCount();
        final MutableByteArray byteArray = new MutableByteArray(byteCount);

        for (int i = 0; i < (byteCount / 4); ++i) {
            final int index = (i * 4);
            final byte byte0 = input.getByte(index + 0);
            final byte byte1 = input.getByte(index + 1);
            final byte byte2 = input.getByte(index + 2);
            final byte byte3 = input.getByte(index + 3);

            byteArray.setByte(index + 0, byte3);
            byteArray.setByte(index + 1, byte2);
            byteArray.setByte(index + 2, byte1);
            byteArray.setByte(index + 3, byte0);
        }

        return byteArray;
    }

    public static String swabHexString(final String input) {
        // 00 01 02 03 | 04 05 06 07 -> 03 02 01 00 | 07 06 05 04
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < (input.length() / 8); ++i) {
            final int index = (i * 8);
            final String byteString0 = _createByteString(input.charAt(index + 0), input.charAt(index + 1));
            final String byteString1 = _createByteString(input.charAt(index + 2), input.charAt(index + 3));
            final String byteString2 = _createByteString(input.charAt(index + 4), input.charAt(index + 5));
            final String byteString3 = _createByteString(input.charAt(index + 6), input.charAt(index + 7));

            stringBuilder.append(byteString3);
            stringBuilder.append(byteString2);
            stringBuilder.append(byteString1);
            stringBuilder.append(byteString0);
        }
        return stringBuilder.toString();
    }
}

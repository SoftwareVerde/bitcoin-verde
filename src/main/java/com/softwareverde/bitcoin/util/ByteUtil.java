package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;

import java.math.BigInteger;

public class ByteUtil extends com.softwareverde.util.ByteUtil {
    public static byte[] variableLengthIntegerToBytes(final long value) {
        final byte[] bytes = ByteUtil.longToBytes(value);

        if (value < 0xFDL) {
            return new byte[] { bytes[7] };
        }
        else if (value <= 0xFFFFL) {
            return new byte[] {
                (byte) 0xFD,
                bytes[7],
                bytes[6]
            };
        }
        else if (value <= 0xFFFFFFFFL) {
            return new byte[] {
                (byte) 0xFE,
                bytes[7],
                bytes[6],
                bytes[5],
                bytes[4]
            };
        }
        else {
            return new byte[] {
                (byte) 0xFF,
                bytes[7],
                bytes[6],
                bytes[5],
                bytes[4],
                bytes[3],
                bytes[2],
                bytes[1],
                bytes[0]
            };
        }
    }

    public static byte[] variableLengthStringToBytes(final String variableLengthString) {
        final int stringLength = variableLengthString.length();
        final byte[] variableLengthIntegerBytes = ByteUtil.variableLengthIntegerToBytes(stringLength);
        final byte[] bytes = new byte[variableLengthString.length() + variableLengthIntegerBytes.length];
        ByteUtil.setBytes(bytes, variableLengthIntegerBytes);
        ByteUtil.setBytes(bytes, variableLengthString.getBytes(), variableLengthIntegerBytes.length);
        return bytes;
    }

    public static int compareByteArrayLexicographically(final ByteArray a, final ByteArray b) {
        final int aByteCount = a.getByteCount();
        final int bByteCount = b.getByteCount();

        final int minByteCount = Math.min(aByteCount, bByteCount);

        for (int i = 0; i < minByteCount; ++i) {
            final byte aByte = a.getByte(i);
            final byte bByte = b.getByte(i);
            final int compareValue = Byte.compare(aByte, bByte);
            if (compareValue != 0) { return compareValue; }
        }

        return Integer.compare(aByteCount, bByteCount);
    }

    public static BigInteger bytesToBigInteger(final ByteArray byteArray) {
        return ByteUtil.bytesToBigInteger(byteArray.getBytes());
    }

    public static BigInteger bytesToBigIntegerUnsigned(final ByteArray byteArray) {
        return ByteUtil.bytesToBigIntegerUnsigned(byteArray.getBytes());
    }

    public static BigInteger bytesToBigInteger(final byte[] bytes) {
        return new BigInteger(bytes);
    }

    public static BigInteger bytesToBigIntegerUnsigned(final byte[] bytes) {
        return new BigInteger(1, bytes);
    }
}

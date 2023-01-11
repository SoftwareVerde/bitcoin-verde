package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

import java.math.BigInteger;

public class ByteUtil extends com.softwareverde.util.ByteUtil {
    protected ByteUtil() { }

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

    public static void setBytes(final MutableByteArray destination, final byte[] data, final int destinationOffset, final int dataLength) {
        ByteUtil.setBytes(destination, MutableByteArray.wrap(data), destinationOffset, dataLength);
    }

    public static void setBytes(final MutableByteArray destination, final ByteArray data, final int destinationOffset, final int dataLength) {
        if (destinationOffset >= destination.getByteCount()) { throw new IndexOutOfBoundsException(); }
        if (dataLength > data.getByteCount()) { throw new IndexOutOfBoundsException(); }
        if ((destinationOffset + dataLength) >= destination.getByteCount()) { throw new IndexOutOfBoundsException(); }

        for (int i = 0; i < dataLength; ++i) {
            final byte b = data.getByte(i);
            destination.setByte(destinationOffset + i, b);
        }
    }
}

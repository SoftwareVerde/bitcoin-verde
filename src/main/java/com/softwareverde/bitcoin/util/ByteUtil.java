package com.softwareverde.bitcoin.util;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

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
        final Integer stringLength = variableLengthString.length();
        final byte[] variableLengthIntegerBytes = ByteUtil.variableLengthIntegerToBytes(stringLength);
        final byte[] bytes = new byte[variableLengthString.length() + variableLengthIntegerBytes.length];
        ByteUtil.setBytes(bytes, variableLengthIntegerBytes);
        ByteUtil.setBytes(bytes, variableLengthString.getBytes(), variableLengthIntegerBytes.length);
        return bytes;
    }

    public static Boolean areEqual(final ByteArray bytes0, final ByteArray bytes1) {
        final int byteCount0 = bytes0.getByteCount();
        if (byteCount0 != bytes1.getByteCount()) { return false; }

        for (int i = 0; i < byteCount0; ++i) {
            final byte b0 = bytes0.getByte(i);
            final byte b1 = bytes1.getByte(i);
            if (b0 != b1) { return false; }
        }
        return true;
    }

    public static void clearByteArray(final MutableByteArray bytes) {
        for (int i = 0; i < bytes.getByteCount(); i += 1) {
            bytes.set(i, (byte) 0x00);
        }
    }

    public static byte reverseBits(final byte b) {
        return (byte) (Integer.reverse(b) >>> 24);
    }

    public static void setBytes(final MutableByteArray destination, final ByteArray source, final Integer destinationOffset) {
        for (int i = 0; i < source.getByteCount(); ++i) {
            final int writeIndex = (i + destinationOffset);
            if (writeIndex >= destination.getByteCount()) { break; }
            destination.set(writeIndex, source.getByte(i));
        }
    }

    public static void setBytes(final MutableByteArray destination, final ByteArray source) {
        ByteUtil.setBytes(destination, source, 0);
    }

    public static byte[] getTailBytes(final byte[] bytes, final Integer byteCount) {
        return ByteUtil.getTailBytes(MutableByteArray.wrap(bytes), byteCount);
    }

    public static byte[] getTailBytes(final ByteArray byteArray, final Integer byteCount) {
        final int byteArrayByteCount = byteArray.getByteCount();
        final byte[] bytes = new byte[byteCount];
        for (int i = 0; i < bytes.length; ++i) {
            if (i >= byteArrayByteCount) { break; }
            bytes[bytes.length - i - 1] = byteArray.getByte(byteArrayByteCount - i - 1);
        }
        return bytes;
    }
}

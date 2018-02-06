package com.softwareverde.bitcoin.util;

public class ByteUtil {
    public static byte[] integerToBytes(final Integer value) {
        return new byte[] {
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) (value.intValue())
        };
    }

    public static byte[] longToBytes(final Long value) {
        return new byte[] {
            (byte) (value >>> 56),
            (byte) (value >>> 48),
            (byte) (value >>> 40),
            (byte) (value >>> 32),
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte) (value.intValue())
        };
    }

    public static byte[] reverseBytes(final byte[] bytes) {
        final byte[] reversedBytes = new byte[bytes.length];
        for (int i=0; i<bytes.length; ++i) {
            reversedBytes[i] = bytes[(bytes.length - 1) - i];
        }
        return reversedBytes;
    }

    public static byte[] copyBytes(final byte[] bytes) {
        final byte[] copiedBytes = new byte[bytes.length];
        for (int i=0; i<bytes.length; ++i) {
            copiedBytes[i] = bytes[i];
        }
        return copiedBytes;
    }

    public static void setBytes(final byte[] destination, final byte[] value, final Integer offset) {
        for (int i=0; (i+offset)<destination.length; ++i) {
            destination[i + offset] = (i < value.length ? value[i] : 0x00);
        }
    }

    public static void setBytes(final byte[] destination, final byte[] value) {
        ByteUtil.setBytes(destination, value, 0);
    }

    public static byte[] serializeVariableLengthInteger(final Long value) {
        if (value < 0xFD) {
            return new byte[] { (byte) value.intValue() };
        }
        else if (value <= 0xFFFF) {
            return new byte[] {
                (byte) 0xFD,
                (byte) (value >>> 8),
                (byte) (value.intValue())
            };
        }
        else if (value <= 0xFFFFFFFF) {
            return new byte[] {
                (byte) 0xFE,
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) (value.intValue())
            };
        }
        else {
            return new byte[] {
                (byte) 0xFF,
                (byte) (value >>> 48),
                (byte) (value >>> 40),
                (byte) (value >>> 32),
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) (value.intValue())
            };
        }
    }
}

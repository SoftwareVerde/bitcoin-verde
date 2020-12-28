package com.softwareverde.bitcoin.util;

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

    public static int compare(final byte b0, final byte b1) {
        return (Byte.toUnsignedInt(b0) - Byte.toUnsignedInt(b1));
    }
}

package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class CompactVariableLengthInteger {
    public static final int MIN_BYTE_COUNT = 1;
    public static final int MAX_BYTE_COUNT = 9;

    public static ByteArray variableLengthIntegerToBytes(final long value) {
        final byte[] bytes = ByteUtil.longToBytes(value);

        if (value < 0xFDL) {
            return MutableByteArray.wrap(new byte[] { bytes[7] });
        }
        else if (value <= 0xFFFFL) {
            return MutableByteArray.wrap(new byte[] {
                (byte) 0xFD,
                bytes[7],
                bytes[6]
            });
        }
        else if (value <= 0xFFFFFFFFL) {
            return MutableByteArray.wrap(new byte[] {
                (byte) 0xFE,
                bytes[7],
                bytes[6],
                bytes[5],
                bytes[4]
            });
        }
        else {
            return MutableByteArray.wrap(new byte[] {
                (byte) 0xFF,
                bytes[7],
                bytes[6],
                bytes[5],
                bytes[4],
                bytes[3],
                bytes[2],
                bytes[1],
                bytes[0]
            });
        }
    }

    public static ByteArray variableLengthStringToBytes(final String variableLengthString) {
        final int stringLength = variableLengthString.length();
        final ByteArray variableLengthIntegerBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(stringLength);
        final MutableByteArray bytes = new MutableByteArray(variableLengthString.length() + variableLengthIntegerBytes.getByteCount());
        bytes.setBytes(0, variableLengthIntegerBytes);
        bytes.setBytes(variableLengthIntegerBytes.getByteCount(), StringUtil.stringToBytes(variableLengthString));
        return bytes;
    }

    public static CompactVariableLengthInteger peakVariableLengthInteger(final ByteArrayReader byteArrayReader) {
        final int prefix = ByteUtil.byteToInteger(byteArrayReader.peakByte());

        if (prefix < 0xFD) {
            return new CompactVariableLengthInteger(prefix, 1);
        }

        final int intByteCount;
        {
            if (prefix < 0xFE) {
                intByteCount = 2;
            }
            else if (prefix < 0xFF) {
                intByteCount = 4;
            }
            else {
                intByteCount = 8;
            }
        }
        final int byteCountWithPrefix = (intByteCount + 1);

        final long value;
        {
            final byte[] rawBytesWithPrefix = byteArrayReader.peakBytes(byteCountWithPrefix);
            final byte[] intBytes = ByteUtil.getTailBytes(rawBytesWithPrefix, intByteCount);
            final byte[] intBytesLittleEndian = ByteUtil.reverseEndian(intBytes);
            value = ByteUtil.bytesToLong(intBytesLittleEndian);
        }

        return new CompactVariableLengthInteger(value, byteCountWithPrefix);
    }

    public static CompactVariableLengthInteger readVariableLengthInteger(final ByteArrayReader byteArrayReader) {
        final CompactVariableLengthInteger variableLengthInteger = CompactVariableLengthInteger.peakVariableLengthInteger(byteArrayReader);
        byteArrayReader.skipBytes(variableLengthInteger.bytesConsumedCount);
        return variableLengthInteger;
    }

    /**
     * Returns a String of CompactVariableLength size.
     *  Does not check for canonical-ness.
     */
    public static String readVariableLengthString(final ByteArrayReader byteArrayReader) {
        final CompactVariableLengthInteger stringByteCount = CompactVariableLengthInteger.peakVariableLengthInteger(byteArrayReader);
        byteArrayReader.skipBytes(stringByteCount.bytesConsumedCount);

        final int byteCount = Math.min(stringByteCount.intValue(), byteArrayReader.remainingByteCount());
        if (byteCount > Integer.MAX_VALUE) { return ""; }

        final byte[] bytes = byteArrayReader.readBytes(byteCount);
        return StringUtil.bytesToString(bytes);
    }

    public final long value;
    public final int bytesConsumedCount;

    public Boolean isCanonical() {
        // (Below ranges are inclusive)
        // 0-252: 1 byte                        (>   0x00000000000000FD)
        // 253-65535: prefix + 2 bytes          (>   0x0000000000010000)
        // 65536-4294967295: prefix + 4 bytes   (>   0x0000000100000000)
        // 4294967296+: prefix + 8 bytes        (> 0x010000000000000000)

        if (this.value < 0xFD) {
            return (this.bytesConsumedCount == 1);
        }

        final int valueByteCount = (this.bytesConsumedCount - 1);
        if (valueByteCount == 2) {
            return (this.value >= 0xFDL);
        }
        else if (valueByteCount == 4) {
            return (this.value > 0xFFFFL);
        }
        else {
            return (this.value > 0xFFFFFFFFL);
        }
    }

    public CompactVariableLengthInteger(final long value, final int byteCount) {
        this.value = value;
        this.bytesConsumedCount = byteCount;
    }

    public int intValue() {
        return (int) this.value;
    }
}

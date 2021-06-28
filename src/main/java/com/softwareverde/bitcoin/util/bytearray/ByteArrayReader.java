package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.bytearray.Endian;

public class ByteArrayReader extends com.softwareverde.util.bytearray.ByteArrayReader {
    public static class CompactVariableLengthInteger {
        public final long value;
        public final int bytesConsumedCount;

        public CompactVariableLengthInteger(final long value, final int byteCount) {
            this.value = value;
            this.bytesConsumedCount = byteCount;
        }
    }

    public static CompactVariableLengthInteger peakVariableLengthInteger(final com.softwareverde.util.bytearray.ByteArrayReader byteArrayReader) {
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

    public ByteArrayReader(final byte[] bytes) {
        super(bytes);
    }

    public ByteArrayReader(final ByteArray byteArray) {
        super(byteArray);
    }

    public Long readVariableLengthInteger() {
        final CompactVariableLengthInteger variableLengthInteger = ByteArrayReader.peakVariableLengthInteger(this);
        _index += variableLengthInteger.bytesConsumedCount;
        return variableLengthInteger.value;
    }

    public CompactVariableLengthInteger peakVariableLengthInteger() {
        return ByteArrayReader.peakVariableLengthInteger(this);
    }

    public String readVariableLengthString() {
        final CompactVariableLengthInteger stringByteCount = ByteArrayReader.peakVariableLengthInteger(this);
        _index += stringByteCount.bytesConsumedCount;

        if (stringByteCount.value > Integer.MAX_VALUE) { return ""; }

        final byte[] stringBytes = _consumeBytes((int) stringByteCount.value, Endian.BIG);

        return new String(stringBytes);
    }
}

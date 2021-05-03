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

    protected CompactVariableLengthInteger _peakVariableLengthInteger(final int index) {
        final int prefix = ByteUtil.byteToInteger(_getByte(index));

        if (prefix < 0xFD) {
            return new CompactVariableLengthInteger(prefix, 1);
        }

        if (prefix < 0xFE) {
            final long value = ByteUtil.bytesToLong(_getBytes(index+1, 2, Endian.LITTLE));
            return new CompactVariableLengthInteger(value, 3);
        }

        if (prefix < 0xFF) {
            final long value = ByteUtil.bytesToLong(_getBytes(index+1, 4, Endian.LITTLE));
            return new CompactVariableLengthInteger(value, 5);
        }

        final long value = ByteUtil.bytesToLong(_getBytes(index+1, 8, Endian.LITTLE));
        return new CompactVariableLengthInteger(value, 9);
    }

    public ByteArrayReader(final byte[] bytes) {
        super(bytes);
    }

    public ByteArrayReader(final ByteArray byteArray) {
        super(byteArray);
    }

    public Long readVariableLengthInteger() {
        final CompactVariableLengthInteger variableLengthInteger = _peakVariableLengthInteger(_index);
        _index += variableLengthInteger.bytesConsumedCount;
        return variableLengthInteger.value;
    }

    public CompactVariableLengthInteger peakVariableLengthInteger() {
        return _peakVariableLengthInteger(_index);
    }

    public String readVariableLengthString() {
        final CompactVariableLengthInteger stringByteCount = _peakVariableLengthInteger(_index);
        _index += stringByteCount.bytesConsumedCount;

        if (stringByteCount.value > Integer.MAX_VALUE) { return ""; }

        final byte[] stringBytes = _consumeBytes((int) stringByteCount.value, Endian.BIG);

        return new String(stringBytes);
    }
}

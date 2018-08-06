package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.bytearray.Endian;

public class ByteArrayReader extends com.softwareverde.util.bytearray.ByteArrayReader {
    public static class VariableSizedInteger {
        public final long value;
        public final int bytesConsumedCount;

        public VariableSizedInteger(final long value, final int byteCount) {
            this.value = value;
            this.bytesConsumedCount = byteCount;
        }
    }

    protected VariableSizedInteger _peakVariableSizedInteger(final int index) {
        final int prefix = ByteUtil.byteToInteger(_getByte(index));

        if (prefix < 0xFD) {
            return new VariableSizedInteger(prefix, 1);
        }

        if (prefix < 0xFE) {
            final long value = ByteUtil.bytesToLong(_getBytes(index+1, 2, Endian.LITTLE));
            return new VariableSizedInteger(value, 3);
        }

        if (prefix < 0xFF) {
            final long value = ByteUtil.bytesToLong(_getBytes(index+1, 4, Endian.LITTLE));
            return new VariableSizedInteger(value, 5);
        }

        final long value = ByteUtil.bytesToLong(_getBytes(index+1, 8, Endian.LITTLE));
        return new VariableSizedInteger(value, 9);
    }

    public ByteArrayReader(final byte[] bytes) {
        super(bytes);
    }

    public ByteArrayReader(final ByteArray byteArray) {
        super(byteArray);
    }

    public Long readVariableSizedInteger() {
        final VariableSizedInteger variableSizedInteger = _peakVariableSizedInteger(_index);
        _index += variableSizedInteger.bytesConsumedCount;
        return variableSizedInteger.value;
    }

    public VariableSizedInteger peakVariableSizedInteger() {
        return _peakVariableSizedInteger(_index);
    }

    public String readVariableLengthString() {
        final VariableSizedInteger stringByteCount = _peakVariableSizedInteger(_index);
        _index += stringByteCount.bytesConsumedCount;

        if (stringByteCount.value > Integer.MAX_VALUE) { return ""; }

        final byte[] stringBytes = _consumeBytes((int) stringByteCount.value, Endian.BIG);

        return new String(stringBytes);
    }
}

package com.softwareverde.bitcoin.util.bytearray;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class ByteArrayReader {
    protected final ByteArray _bytes;
    protected int _index;
    protected Boolean _ranOutOfBytes = false;

    /**
     * Copies byteCount number of _bytes starting at index (inclusive).
     *  If the end of _bytes is reached before byteCount is reached, the buffer is filled with 0x00.
     *  Does not increment _index to match the number of bytes read.
     *  Bytes are transferred to the buffer in reverse order by Endian.LITTLE is specified.
     *  Reading past the end of _bytes will set the _ranOutOfBytes flag.
     */
    protected byte[] _readBytes(final int index, final int byteCount, final Endian endian) {
        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            final int writeIndex = (endian == Endian.BIG) ? (i) : ((byteCount - i) - 1);

            if (index + i < _bytes.getByteCount()) {
                bytes[writeIndex] = _bytes.getByte(index + i);
            }
            else {
                _ranOutOfBytes = true;
                bytes[writeIndex] = (byte) 0x00;
            }
        }
        return bytes;
    }

    /**
     * Returns the byte at the specified index.
     *  If index is out of bounds, 0x00 is returned.
     *  Reading past the end of _bytes will set the _ranOutOfBytes flag.
     */
    protected byte _readByte(final int index) {
        if (index < _bytes.getByteCount()) {
            return _bytes.getByte(index);
        }
        else {
            _ranOutOfBytes = true;
            return (byte) 0x00;
        }
    }

    private static class VariableSizedInteger {
        final long value;
        final int bytesConsumedCount;

        public VariableSizedInteger(final long value, final int byteCount) {
            this.value = value;
            this.bytesConsumedCount = byteCount;
        }
    }

    protected VariableSizedInteger _readVariableSizedInteger(final int index) {
        final int prefix = ByteUtil.byteToInteger(_readByte(index));

        if (prefix < 0xFD) {
            return new VariableSizedInteger(prefix, 1);
        }

        if (prefix < 0xFE) {
            final long value = ByteUtil.bytesToLong(_readBytes(index+1, 2, Endian.LITTLE));
            return new VariableSizedInteger(value, 3);
        }

        if (prefix < 0xFF) {
            final long value = ByteUtil.bytesToLong(_readBytes(index+1, 4, Endian.LITTLE));
            return new VariableSizedInteger(value, 5);
        }

        final long value = ByteUtil.bytesToLong(_readBytes(index+1, 8, Endian.LITTLE));
        return new VariableSizedInteger(value, 9);
    }

    public ByteArrayReader(final byte[] bytes) {
        _bytes = MutableByteArray.wrap(bytes);  // Copying the bytes is not needed here, since ByteArrayReader is merely a convenience wrapper, and any outside-change is inconsequential for this class.
        _index = 0;
    }

    public ByteArrayReader(final ByteArray bytes) {
        _bytes = bytes; // ByteArray.asConst() is not needed here, since ByteArrayReader is merely a convenience wrapper, and any outside-change is inconsequential for this class.
        _index = 0;
    }

    public Integer remainingByteCount() {
        return Math.max(0, (_bytes.getByteCount() - _index));
    }

    public void skipBytes(final Integer byteCount) {
        _index += byteCount;
    }

    public byte[] readBytes(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _readBytes(_index, byteCount, endian);
        _index += byteCount;
        return bytes;
    }

    public byte readByte() {
        final byte[] bytes = _readBytes(_index, 1, Endian.BIG);
        _index += 1;
        return bytes[0];
    }

    public byte[] peakBytes(final Integer byteCount, final Endian endian) {
        return _readBytes(_index, byteCount, endian);
    }

    public byte peakByte() {
        final byte[] bytes = _readBytes(_index, 1, Endian.BIG);
        return bytes[0];
    }

    public String readString(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _readBytes(_index, byteCount, endian);
        _index += byteCount;
        return new String(bytes);
    }

    public Integer readInteger(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _readBytes(_index, byteCount, endian);
        _index += byteCount;
        return ByteUtil.bytesToInteger(bytes);
    }

    public Long readLong(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _readBytes(_index, byteCount, endian);
        _index += byteCount;
        return ByteUtil.bytesToLong(bytes);
    }

    public Boolean readBoolean() {
        final byte value = _readByte(_index);
        _index += 1;
        return (value != 0x00);
    }

    public Long readVariableSizedInteger() {
        final VariableSizedInteger variableSizedInteger = _readVariableSizedInteger(_index);
        _index += variableSizedInteger.bytesConsumedCount;
        return variableSizedInteger.value;
    }

    public String readVariableLengthString() {
        final VariableSizedInteger stringByteCount = _readVariableSizedInteger(_index);
        _index += stringByteCount.bytesConsumedCount;

        if (stringByteCount.value > Integer.MAX_VALUE) { return ""; }

        final byte[] stringBytes = _readBytes(_index, (int) stringByteCount.value, Endian.BIG);
        _index += stringByteCount.value;

        return new String(stringBytes);
    }

    public Boolean didOverflow() {
        return _ranOutOfBytes;
    }
}

package com.softwareverde.util.bytearray;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;

public class ByteArrayReader {
    protected final ByteArray _bytes;
    protected int _index;
    protected Boolean _ranOutOfBytes = false;

    /**
     * Returns the byte at the specified index.
     *  If index is out of bounds, 0x00 is returned.
     *  Reading past the end of _bytes will set the _ranOutOfBytes flag.
     */
    protected byte _getByte(final int index) {
        if (index < _bytes.getByteCount()) {
            return _bytes.getByte(index);
        }
        else {
            _ranOutOfBytes = true;
            return (byte) 0x00;
        }
    }

    /**
     * Copies byteCount number of _bytes starting at index (inclusive).
     *  If the end of _bytes is reached before byteCount is reached, the buffer is filled with 0x00.
     *  Does not increment _index to match the number of bytes read.
     *  Bytes are transferred to the buffer in reverse order by Endian.LITTLE is specified.
     *  Reading past the end of _bytes will set the _ranOutOfBytes flag.
     */
    protected byte[] _getBytes(final int index, final int byteCount, final Endian endian) {
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

    protected byte[] _consumeBytes(final int byteCount, final Endian endian) {
        final byte[] bytes = _getBytes(_index, byteCount, endian);
        _index += byteCount;
        return bytes;
    }

    protected byte _consumeByte() {
        final byte b = _getByte(_index);
        _index += 1;
        return b;
    }

    protected int _calculateRemainingByteCount() {
        return Math.max(0, (_bytes.getByteCount() - _index));
    }

    public ByteArrayReader(final byte[] bytes) {
        _bytes = MutableByteArray.wrap(bytes);  // Copying the bytes is not needed here, since ByteArrayReader is merely a convenience wrapper, and any outside-change is inconsequential for this class.
        _index = 0;
    }

    public ByteArrayReader(final ByteArray bytes) {
        _bytes = bytes; // ByteArray.asConst() is not needed here, since ByteArrayReader is merely a convenience wrapper, and any outside-change is inconsequential for this class.
        _index = 0;
    }

    public Integer getPosition() {
        return _index;
    }

    public void setPosition(final Integer index) {
        _index = index;
    }

    public Integer remainingByteCount() {
        return _calculateRemainingByteCount();
    }

    public Boolean hasBytes() {
        return (_calculateRemainingByteCount() > 0);
    }

    public void skipBytes(final Integer byteCount) {
        _index += byteCount;
    }

    public byte peakByte() {
        return _getByte(_index);
    }

    public byte[] peakBytes(final Integer byteCount) {
        return _getBytes(_index, byteCount, Endian.BIG);
    }

    public byte[] peakBytes(final Integer byteCount, final Endian endian) {
        return _getBytes(_index, byteCount, endian);
    }

    public byte readByte() {
        return _consumeByte();
    }

    public byte[] readBytes(final Integer byteCount) {
        return _consumeBytes(byteCount, Endian.BIG);
    }

    public byte[] readBytes(final Integer byteCount, final Endian endian) {
        return _consumeBytes(byteCount, endian);
    }

    public String readString(final Integer byteCount) {
        final byte[] bytes = _consumeBytes(byteCount, Endian.BIG);
        return new String(bytes);
    }

    public String readString(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _consumeBytes(byteCount, endian);
        return new String(bytes);
    }

    public Integer readInteger(final Integer byteCount) {
        final byte[] bytes = _consumeBytes(byteCount, Endian.BIG);
        return ByteUtil.bytesToInteger(bytes);
    }

    public Integer readInteger(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _consumeBytes(byteCount, endian);
        return ByteUtil.bytesToInteger(bytes);
    }

    public Long readLong(final Integer byteCount) {
        final byte[] bytes = _consumeBytes(byteCount, Endian.BIG);
        return ByteUtil.bytesToLong(bytes);
    }

    public Long readLong(final Integer byteCount, final Endian endian) {
        final byte[] bytes = _consumeBytes(byteCount, endian);
        return ByteUtil.bytesToLong(bytes);
    }

    public Boolean readBoolean() {
        final byte value = _consumeByte();
        return (value != 0x00);
    }

    public Boolean didOverflow() {
        return _ranOutOfBytes;
    }
}

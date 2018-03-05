package com.softwareverde.bitcoin.type.bytearray.overflow;

import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;

public class MutableOverflowByteArray extends MutableByteArray {
    public MutableOverflowByteArray(final byte[] bytes) {
        super(bytes);
    }

    public MutableOverflowByteArray(final int byteCount) {
        super(byteCount);
    }

    @Override
    public byte getByte(final int index) {
        if (index >= _bytes.length) { return 0x00; }
        return _bytes[index];
    }

    @Override
    public byte[] getBytes(final int startIndex, final int byteCount) {
        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            final int readIndex = (startIndex + i);
            if (readIndex >= _bytes.length) { break; }
            bytes[i] = _bytes[readIndex];
        }
        return bytes;
    }

    @Override
    public ImmutableOverflowingByteArray asConst() {
        return new ImmutableOverflowingByteArray(this);
    }
}

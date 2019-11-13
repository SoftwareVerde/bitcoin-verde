package com.softwareverde.bitcoin.bytearray.overflow;

import com.softwareverde.constable.bytearray.MutableByteArray;

public class MutableOverflowingByteArray extends MutableByteArray {
    public MutableOverflowingByteArray(final byte[] bytes) {
        super(bytes);
    }

    public MutableOverflowingByteArray(final int byteCount) {
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
        for (int i = 0; i < byteCount; ++i) {
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

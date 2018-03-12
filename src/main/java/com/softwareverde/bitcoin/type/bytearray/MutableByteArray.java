package com.softwareverde.bitcoin.type.bytearray;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

public class MutableByteArray implements ByteArray {
    public static MutableByteArray wrap(final byte[] bytes) {
        final MutableByteArray mutableByteArray = new MutableByteArray();
        mutableByteArray._bytes = bytes;
        return mutableByteArray;
    }

    protected byte[] _bytes;

    protected MutableByteArray() {
        _bytes = null;
    }

    public MutableByteArray(final byte[] bytes) {
        _bytes = ByteUtil.copyBytes(bytes);
    }

    public MutableByteArray(final int byteCount) {
        _bytes = new byte[byteCount];
    }

    public void set(final int index, final byte b) {
        if (index >= _bytes.length) { throw new IndexOutOfBoundsException(); }
        _bytes[index] = b;
    }

    @Override
    public byte getByte(final int index) {
        if (index >= _bytes.length) { throw new IndexOutOfBoundsException(); }

        return _bytes[index];
    }

    @Override
    public byte[] getBytes(final int index, final int byteCount) {
        if (index + byteCount >= _bytes.length) { throw new IndexOutOfBoundsException(); }

        final byte[] bytes = new byte[byteCount];
        for (int i=0; i<byteCount; ++i) {
            final int readIndex = (index + i);
            bytes[i] = _bytes[readIndex];
        }
        return bytes;
    }

    @Override
    public int getByteCount() {
        return _bytes.length;
    }

    @Override
    public boolean isEmpty() {
        return (_bytes.length == 0);
    }

    @Override
    public byte[] getBytes() {
        return _bytes;
    }

    @Override
    public ImmutableByteArray asConst() {
        return new ImmutableByteArray(this);
    }

    @Override
    public int hashCode() {
        long value = 0;
        for (byte b : _bytes) {
            value += ByteUtil.byteToLong(b);
        }
        return Long.valueOf(value).hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) { return false; }

        final byte[] bytes;
        {
            if (obj instanceof ByteArray) {
                final ByteArray object = (ByteArray) obj;
                bytes = object.getBytes();
            }
            else if (obj instanceof byte[]) {
                bytes = (byte[]) obj;
            }
            else { return false; }
        }

        return ByteUtil.areEqual(_bytes, bytes);
    }

    @Override
    public String toString() {
        return BitcoinUtil.toHexString(_bytes);
    }
}

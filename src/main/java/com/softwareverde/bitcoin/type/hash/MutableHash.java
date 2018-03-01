package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

public class MutableHash implements Hash {
    protected final byte[] _bytes = new byte[BYTE_COUNT];

    public static MutableHash fromHexString(final String hexString) {
        final byte[] hashBytes = BitcoinUtil.hexStringToByteArray(hexString);
        return new MutableHash(hashBytes);
    }

    public MutableHash() { }
    public MutableHash(final byte[] bytes) {
        ByteUtil.setBytes(_bytes, bytes);
    }

    @Override
    public byte get(final int index) {
        return _bytes[index];
    }

    @Override
    public byte[] getBytes() {
        return _bytes;
    }

    public void set(final int index, final byte value) {
        _bytes[index] = value;
    }

    public void setBytes(final byte[] values) {
        ByteUtil.setBytes(_bytes, values);
    }

    public void setBytes(final Hash hash) {
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    public void setBytes(final byte[] values, final int offset) {
        ByteUtil.setBytes(_bytes, values, offset);
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
        if (! (obj instanceof Hash)) { return false; }
        final Hash object = (Hash) obj;
        return ByteUtil.areEqual(_bytes, object.getBytes());
    }

    @Override
    public String toString() {
        return BitcoinUtil.toHexString(_bytes);
    }
}

package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class MutableHash extends MutableByteArray implements Hash {
    public static MutableHash fromHexString(final String hexString) {
        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        return new MutableHash(hashBytes);
    }

    public static MutableHash wrap(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new MutableHash(bytes);
    }

    public static MutableHash copyOf(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new MutableHash(ByteUtil.copyBytes(bytes));
    }

    protected MutableHash(final byte[] bytes) {
        super(bytes);
    }

    public MutableHash() {
        super(BYTE_COUNT);
    }

    public MutableHash(final Hash hash) {
        super(BYTE_COUNT);
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public Hash toReversedEndian() {
        return new MutableHash(ByteUtil.reverseEndian(_bytes));
    }

    public void setBytes(final byte[] bytes) {
        ByteUtil.setBytes(_bytes, bytes);
    }

    public void setBytes(final Hash hash) {
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public ImmutableHash asConst() {
        return new ImmutableHash(this);
    }
}

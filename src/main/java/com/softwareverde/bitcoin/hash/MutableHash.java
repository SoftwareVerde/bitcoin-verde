package com.softwareverde.bitcoin.hash;

import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.ByteUtil;
import com.softwareverde.util.HexUtil;

public class MutableHash extends MutableByteArray implements Hash {
    public static MutableHash fromHexString(final String hexString) {
        final byte[] hashBytes = HexUtil.hexStringToByteArray(hexString);
        return new MutableHash(hashBytes);
    }

    public static MutableHash wrap(final byte[] bytes) {
        return new MutableHash(bytes);
    }

    public static MutableHash copyOf(final byte[] bytes) {
        return new MutableHash(ByteUtil.copyBytes(bytes));
    }

    protected MutableHash(final byte[] bytes) {
        super(bytes);
    }

    public MutableHash(final Integer byteCount) {
        super(byteCount);
    }

    public MutableHash(final Hash hash) {
        super(hash);
    }

    @Override
    public Hash toReversedEndian() {
        return MutableHash.wrap(ByteUtil.reverseEndian(_bytes));
    }

    public void setBytes(final byte[] bytes) {
        if (_bytes.length != bytes.length) {
            _bytes = new byte[bytes.length];
        }
        ByteUtil.setBytes(_bytes, bytes);
    }

    public void setBytes(final Hash hash) {
        if (_bytes.length != hash.getByteCount()) {
            _bytes = new byte[hash.getByteCount()];
        }
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public ImmutableHash asConst() {
        return new ImmutableHash(this);
    }
}

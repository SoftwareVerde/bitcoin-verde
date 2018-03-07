package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.type.bytearray.MutableByteArray;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.bitcoin.util.ByteUtil;

public class MutableHash extends MutableByteArray implements Hash {
    public static MutableHash fromHexString(final String hexString) {
        final byte[] hashBytes = BitcoinUtil.hexStringToByteArray(hexString);
        return new MutableHash(hashBytes);
    }

    public MutableHash() {
        super(BYTE_COUNT);
    }

    public MutableHash(final byte[] bytes) {
        super(BYTE_COUNT);
        ByteUtil.setBytes(_bytes, bytes);
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

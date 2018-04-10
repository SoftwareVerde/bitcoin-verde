package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;

public class ImmutableHash extends ImmutableOverflowingByteArray implements Hash, Const {
    public static ImmutableHash copyOf(final byte[] bytes) {
        return new ImmutableHash(bytes);
    }

    protected ImmutableHash(final byte[] bytes) {
        super(new byte[BYTE_COUNT]);
        ByteUtil.setBytes(_bytes, bytes);
    }

    public ImmutableHash() {
        super(new byte[BYTE_COUNT]);
    }

    public ImmutableHash(final Hash hash) {
        super(new byte[BYTE_COUNT]);
        ByteUtil.setBytes(_bytes, hash.getBytes());
    }

    @Override
    public Hash toReversedEndian() {
        return new MutableHash(ByteUtil.reverseEndian(_bytes));
    }

    @Override
    public ImmutableHash asConst() {
        return this;
    }
}

package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.Const;

public class ImmutableHash extends ImmutableOverflowingByteArray implements Hash, Const {
    public static ImmutableHash copyOf(final byte[] bytes) {
        return new ImmutableHash(bytes);
    }

    protected ImmutableHash(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableHash() {
        super(new byte[0]);
    }

    public ImmutableHash(final Hash hash) {
        super(hash);
    }

    @Override
    public Hash toReversedEndian() {
        return MutableHash.wrap(ByteUtil.reverseEndian(_bytes));
    }

    @Override
    public ImmutableHash asConst() {
        return this;
    }
}

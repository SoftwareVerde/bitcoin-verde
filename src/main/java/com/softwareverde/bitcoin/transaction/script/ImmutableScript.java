package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.Const;

public class ImmutableScript extends ImmutableOverflowingByteArray implements Script, Const {

    public ImmutableScript(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public Hash getHash() {
        final byte[] hashBytes = BitcoinUtil.ripemd160(BitcoinUtil.sha256(_bytes));
        return MutableHash.wrap(hashBytes);
    }

    @Override
    public ImmutableScript asConst() {
        return this;
    }
}

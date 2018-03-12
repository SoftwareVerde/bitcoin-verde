package com.softwareverde.bitcoin.transaction.script;

import com.softwareverde.bitcoin.type.bytearray.overflow.ImmutableOverflowingByteArray;
import com.softwareverde.constable.Const;

public class ImmutableScript extends ImmutableOverflowingByteArray implements Script, Const {

    public ImmutableScript(final byte[] bytes) {
        super(bytes);
    }

    @Override
    public ImmutableScript asConst() {
        return this;
    }
}

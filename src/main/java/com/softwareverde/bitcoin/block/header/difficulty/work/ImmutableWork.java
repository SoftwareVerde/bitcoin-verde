package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;

public class ImmutableWork extends ImmutableByteArray implements Const {
    protected ImmutableWork(final ByteArray byteArray) {
        super(byteArray);
    }

    public ImmutableWork() {
        super(new byte[32]);
    }

    public ImmutableWork(final BlockWork blockWork) {
        super(blockWork);
    }

    @Override
    public ImmutableWork asConst() {
        return this;
    }
}

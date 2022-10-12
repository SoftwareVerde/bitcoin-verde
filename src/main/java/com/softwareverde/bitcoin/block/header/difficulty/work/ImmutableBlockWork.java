package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;

public class ImmutableBlockWork extends ImmutableWork implements BlockWork, Const {
    protected ImmutableBlockWork(final ByteArray byteArray) {
        super(byteArray);
    }

    public ImmutableBlockWork() {
        super();
    }

    public ImmutableBlockWork(final BlockWork blockWork) {
        super(blockWork);
    }

    public MutableChainWork add(final Work work) {
        final MutableChainWork chainWork = new MutableChainWork(this);
        chainWork.add(work);
        return chainWork;
    }

    @Override
    public ImmutableBlockWork asConst() {
        return this;
    }
}

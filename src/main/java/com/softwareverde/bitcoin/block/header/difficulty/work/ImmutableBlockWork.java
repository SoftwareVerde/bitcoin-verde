package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;

public class ImmutableBlockWork extends ImmutableByteArray implements BlockWork {
    protected ImmutableBlockWork(final ByteArray byteArray) {
        super(byteArray);
    }

    public ImmutableBlockWork() {
        super(new byte[32]);
    }

    public ImmutableBlockWork(final BlockWork blockWork) {
        super(blockWork);
    }

    public MutableChainWork add(final Work work) {
        final MutableChainWork chainWork = new MutableChainWork(this);
        chainWork.add(work);
        return chainWork;
    }
}

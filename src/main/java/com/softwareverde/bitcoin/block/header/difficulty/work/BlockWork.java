package com.softwareverde.bitcoin.block.header.difficulty.work;

import com.softwareverde.constable.bytearray.ByteArray;

public interface BlockWork extends Work {
    static BlockWork fromByteArray(final ByteArray byteArray) {
        if (byteArray.getByteCount() != 32) { return null; }
        return new ImmutableBlockWork(byteArray);
    }

    MutableChainWork add(final Work work);

    @Override
    ImmutableBlockWork asConst();
}

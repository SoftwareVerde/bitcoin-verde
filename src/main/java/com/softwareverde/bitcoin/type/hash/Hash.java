package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.constable.bytearray.ByteArray;

public interface Hash extends ByteArray {
    Integer SHA_256_BYTE_COUNT = 32;
    Integer RIPEMD_160_BYTE_COUNT = 20;

    Hash toReversedEndian();

    @Override
    ImmutableHash asConst();
}

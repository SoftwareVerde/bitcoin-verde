package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.constable.bytearray.ByteArray;

public interface Hash extends ByteArray {
    Integer BYTE_COUNT = 32;

    Hash toReversedEndian();

    @Override
    ImmutableHash asConst();
}

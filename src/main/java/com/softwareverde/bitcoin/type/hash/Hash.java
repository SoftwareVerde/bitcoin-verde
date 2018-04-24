package com.softwareverde.bitcoin.type.hash;

import com.softwareverde.constable.bytearray.ByteArray;

public interface Hash extends ByteArray {
    Hash toReversedEndian();

    @Override
    ImmutableHash asConst();
}

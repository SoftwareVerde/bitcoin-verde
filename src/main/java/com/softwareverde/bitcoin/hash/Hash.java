package com.softwareverde.bitcoin.hash;

import com.softwareverde.constable.bytearray.ByteArray;

public interface Hash extends ByteArray {
    Hash toReversedEndian();

    @Override
    ImmutableHash asConst();
}

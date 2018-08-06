package com.softwareverde.bitcoin.type.hash.ripemd160;

import com.softwareverde.bitcoin.type.hash.Hash;

public interface Ripemd160Hash extends Hash {
    Integer BYTE_COUNT = 20;

    @Override
    public Ripemd160Hash toReversedEndian();

    @Override
    ImmutableRipemd160Hash asConst();
}

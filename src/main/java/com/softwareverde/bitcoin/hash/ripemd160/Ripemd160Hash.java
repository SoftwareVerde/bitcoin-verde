package com.softwareverde.bitcoin.hash.ripemd160;

import com.softwareverde.bitcoin.hash.Hash;

public interface Ripemd160Hash extends Hash {
    Integer BYTE_COUNT = 20;

    @Override
    public Ripemd160Hash toReversedEndian();

    @Override
    ImmutableRipemd160Hash asConst();
}

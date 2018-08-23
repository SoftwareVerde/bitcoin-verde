package com.softwareverde.bitcoin.type.hash.sha256;

import com.softwareverde.bitcoin.type.hash.Hash;

public interface Sha256Hash extends Hash {
    Integer BYTE_COUNT = 32;
    ImmutableSha256Hash EMPTY_HASH = new ImmutableSha256Hash();

    @Override
    public Sha256Hash toReversedEndian();

    @Override
    ImmutableSha256Hash asConst();
}

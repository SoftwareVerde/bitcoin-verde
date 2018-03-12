package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.Hash;

public interface MerkleRoot extends Hash {

    @Override
    ImmutableMerkleRoot asConst();
}

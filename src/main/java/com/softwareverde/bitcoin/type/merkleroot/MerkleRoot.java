package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;

public interface MerkleRoot extends Sha256Hash {

    @Override
    ImmutableMerkleRoot asConst();
}

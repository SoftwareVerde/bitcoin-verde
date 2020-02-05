package com.softwareverde.bitcoin.merkleroot;

import com.softwareverde.security.hash.sha256.Sha256Hash;

public interface MerkleRoot extends Sha256Hash {

    @Override
    ImmutableMerkleRoot asConst();
}

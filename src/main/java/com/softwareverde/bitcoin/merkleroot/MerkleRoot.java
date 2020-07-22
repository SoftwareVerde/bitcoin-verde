package com.softwareverde.bitcoin.merkleroot;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface MerkleRoot extends Sha256Hash {

    @Override
    ImmutableMerkleRoot asConst();
}

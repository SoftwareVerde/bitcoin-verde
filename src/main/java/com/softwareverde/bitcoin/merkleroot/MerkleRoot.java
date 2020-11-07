package com.softwareverde.bitcoin.merkleroot;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface MerkleRoot extends Sha256Hash {
    static MerkleRoot fromHexString(final String hexString) {
        return ImmutableMerkleRoot.fromHexString(hexString);
    }

    static MerkleRoot copyOf(final byte[] bytes) {
        return ImmutableMerkleRoot.copyOf(bytes);
    }

    static MerkleRoot wrap(final byte[] bytes) {
        return MutableMerkleRoot.wrap(bytes);
    }

    @Override
    ImmutableMerkleRoot asConst();
}

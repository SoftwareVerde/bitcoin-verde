package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.constable.Const;

public class ImmutableMerkleRoot extends ImmutableSha256Hash implements MerkleRoot, Const {
    public static ImmutableMerkleRoot copyOf(final byte[] bytes) {
        if (bytes.length != BYTE_COUNT) { return null; }
        return new ImmutableMerkleRoot(bytes);
    }

    public ImmutableMerkleRoot() {
        super();
    }

    protected ImmutableMerkleRoot(final byte[] bytes) {
        super(bytes);
    }

    public ImmutableMerkleRoot(final MerkleRoot merkleRoot) {
        super(merkleRoot);
    }

    @Override
    public ImmutableMerkleRoot asConst() {
        return this;
    }
}

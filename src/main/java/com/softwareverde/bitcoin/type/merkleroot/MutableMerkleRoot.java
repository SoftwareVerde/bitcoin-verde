package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.MutableHash;

public class MutableMerkleRoot extends MutableHash implements MerkleRoot {

    public MutableMerkleRoot() {
        super();
    }

    public MutableMerkleRoot(final byte[] bytes) {
        super(bytes);
    }

    public MutableMerkleRoot(final MerkleRoot merkleRoot) {
        super(merkleRoot);
    }

    @Override
    public ImmutableMerkleRoot asConst() {
        return new ImmutableMerkleRoot(this);
    }
}

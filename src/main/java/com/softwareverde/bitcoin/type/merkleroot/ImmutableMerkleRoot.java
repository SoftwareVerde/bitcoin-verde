package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.util.ByteUtil;

public class ImmutableMerkleRoot implements MerkleRoot {
    private final MutableMerkleRoot _mutableMerkleRoot;

    public ImmutableMerkleRoot() {
        _mutableMerkleRoot = new MutableMerkleRoot();
    }

    public ImmutableMerkleRoot(final byte[] bytes) {
        _mutableMerkleRoot = new MutableMerkleRoot(ByteUtil.copyBytes(bytes));
    }

    @Override
    public byte get(final int index) {
        return _mutableMerkleRoot.get(index);
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_mutableMerkleRoot.getBytes());
    }
}

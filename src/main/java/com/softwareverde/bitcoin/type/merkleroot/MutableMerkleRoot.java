package com.softwareverde.bitcoin.type.merkleroot;

import com.softwareverde.bitcoin.type.hash.MutableHash;

public class MutableMerkleRoot implements MerkleRoot {
    private final MutableHash _mutableHash;

    public MutableMerkleRoot() {
        _mutableHash = new MutableHash();
    }

    public MutableMerkleRoot(final byte[] bytes) {
        _mutableHash = new MutableHash(bytes);
    }

    @Override
    public byte get(final int index) {
        return _mutableHash.get(index);
    }

    @Override
    public byte[] getBytes() {
        return _mutableHash.getBytes();
    }
}

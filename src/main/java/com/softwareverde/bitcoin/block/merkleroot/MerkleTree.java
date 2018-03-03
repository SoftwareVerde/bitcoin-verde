package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;

public interface MerkleTree {
    void addItem(final Hashable transaction);
    MerkleRoot getMerkleRoot();
}

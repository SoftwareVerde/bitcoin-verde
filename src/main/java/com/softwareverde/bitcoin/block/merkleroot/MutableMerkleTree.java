package com.softwareverde.bitcoin.block.merkleroot;

public interface MutableMerkleTree<T extends Hashable> extends MerkleTree<T> {
    void addItem(T item);
    void replaceItem(int index, T item);
}

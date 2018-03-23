package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.list.List;

public interface MerkleTree<T extends Hashable> {
    void addItem(T item);
    List<T> getItems();

    void replaceItem(int index, T item);
    int getItemCount();
    boolean isEmpty();

    MerkleRoot getMerkleRoot();
    List<Hash> getPartialTree(int transactionIndex);
}

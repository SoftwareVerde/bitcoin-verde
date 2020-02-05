package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.constable.list.List;

public interface MerkleTree<T extends Hashable> {
    interface Filter<T extends Hashable> {
        boolean shouldInclude(T item);
    }

    void addItem(T item);
    T getItem(int index);
    List<T> getItems();

    void replaceItem(int index, T item);
    int getItemCount();
    boolean isEmpty();

    MerkleRoot getMerkleRoot();
    List<Sha256Hash> getPartialTree(int transactionIndex);
    PartialMerkleTree getPartialTree(Filter<T> filter);
}

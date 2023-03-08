package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public interface MerkleTree<T extends Hashable> {
    interface Filter<T extends Hashable> {
        boolean shouldInclude(T item);
    }

    T getItem(int index);
    List<T> getItems();

    int getItemCount();
    boolean isEmpty();

    MerkleRoot getMerkleRoot();
    List<Sha256Hash> getPartialTree(int itemIndex);
    PartialMerkleTree getPartialTree(Filter<T> filter);
}
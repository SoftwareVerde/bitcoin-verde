package com.softwareverde.bitcoin.block.merkleroot;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public class MerkleTreeStruct {
    protected final Sha256Hash[][] _data;

    public MerkleTreeStruct(final int itemCount) {
        final int depth = (BitcoinUtil.log2(itemCount) + 1);

        _data = new Sha256Hash[depth][];
    }
}

package com.softwareverde.bitcoin.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTree;
import com.softwareverde.util.Util;

public class MerkleBlock extends ImmutableBlockHeader {
    protected final PartialMerkleTree _partialMerkleTree;

    public MerkleBlock(final BlockHeader blockHeader, final PartialMerkleTree partialMerkleTree) {
        super(blockHeader);
        _partialMerkleTree = partialMerkleTree;
    }

    public PartialMerkleTree getPartialMerkleTree() {
        return _partialMerkleTree;
    }

    @Override
    public Boolean isValid() {
        if (! super.isValid()) { return false; }
        return Util.areEqual(super.getMerkleRoot(), _partialMerkleTree.getMerkleRoot());
    }

    @Override
    public MerkleBlock asConst() { return this; }
}

package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeDeflater;
import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;

public interface MerkleTreeInflaters extends Inflater {
    PartialMerkleTreeInflater getPartialMerkleTreeInflater();
    PartialMerkleTreeDeflater getPartialMerkleTreeDeflater();
}

package com.softwareverde.bitcoin.inflater;

import com.softwareverde.bitcoin.block.merkleroot.PartialMerkleTreeInflater;

public interface MerkleTreeInflaters extends Inflater {
    PartialMerkleTreeInflater getPartialMerkleTreeInflater();
}

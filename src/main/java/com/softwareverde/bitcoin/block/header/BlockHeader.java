package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.Constable;
import com.softwareverde.util.HexUtil;

public interface BlockHeader extends Hashable, Constable<ImmutableBlockHeader> {
    Integer VERSION = 0x04;
    ImmutableHash GENESIS_BLOCK_HEADER_HASH = new ImmutableHash(HexUtil.hexStringToByteArray("000000000019D6689C085AE165831E934FF763AE46A2A6C172B3F1B60A8CE26F"));

    Hash getPreviousBlockHash();
    MerkleRoot getMerkleRoot();
    Difficulty getDifficulty();
    Integer getVersion();
    Long getTimestamp();
    Long getNonce();

    Boolean isValid();

    @Override
    ImmutableBlockHeader asConst();
}

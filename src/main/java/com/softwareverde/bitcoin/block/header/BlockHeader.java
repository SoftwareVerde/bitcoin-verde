package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.merkleroot.Hashable;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.Constable;

public interface BlockHeader extends Hashable, Constable<ImmutableBlockHeader> {
    static final Integer VERSION = 0x04;
    static final ImmutableHash GENESIS_BLOCK_HEADER_HASH = new ImmutableHash(BitcoinUtil.hexStringToByteArray("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"));

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

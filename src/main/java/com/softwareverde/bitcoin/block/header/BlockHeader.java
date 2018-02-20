package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.ImmutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.util.BitcoinUtil;

public interface BlockHeader {
    static final Integer VERSION = 0x04;
    static final ImmutableHash GENESIS_BLOCK_HEADER_HASH = new ImmutableHash(BitcoinUtil.hexStringToByteArray("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"));

    Integer getVersion();
    Hash getPreviousBlockHash();
    MerkleRoot getMerkleRoot();
    Long getTimestamp();
    Difficulty getDifficulty();
    Long getNonce();
    Hash calculateSha256Hash();
    byte[] getBytes();

    Boolean validateBlockHeader();
}

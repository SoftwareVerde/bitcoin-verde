package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.bitcoin.util.ByteUtil;

public class ImmutableBlockHeader implements BlockHeader {
    protected final MutableBlockHeader _mutableBlockHeader;

    public ImmutableBlockHeader() {
        _mutableBlockHeader = new MutableBlockHeader();
    }

    @Override
    public Integer getVersion() {
        return _mutableBlockHeader.getVersion();
    }

    @Override
    public Hash getPreviousBlockHash() {
        return _mutableBlockHeader.getPreviousBlockHash();
    }

    @Override
    public MutableMerkleRoot getMerkleRoot() {
        return _mutableBlockHeader.getMerkleRoot();
    }

    @Override
    public Long getTimestamp() {
        return _mutableBlockHeader.getTimestamp();
    }

    @Override
    public Difficulty getDifficulty() {
        return _mutableBlockHeader.getDifficulty();
    }

    @Override
    public Long getNonce() {
        return _mutableBlockHeader.getNonce();
    }

    @Override
    public Hash calculateSha256Hash() {
        return _mutableBlockHeader.calculateSha256Hash();
    }

    @Override
    public byte[] getBytes() {
        return ByteUtil.copyBytes(_mutableBlockHeader.getBytes());
    }
}

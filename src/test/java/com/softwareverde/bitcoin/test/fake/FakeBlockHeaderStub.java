package com.softwareverde.bitcoin.test.fake;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.ImmutableBlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.json.Json;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class FakeBlockHeaderStub implements BlockHeader {
    @Override
    public Sha256Hash getPreviousBlockHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Difficulty getDifficulty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getTimestamp() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long getNonce() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Boolean isValid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ImmutableBlockHeader asConst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Sha256Hash getHash() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Json toJson() {
        throw new UnsupportedOperationException();
    }
}
package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.Hash;
import com.softwareverde.bitcoin.type.hash.MutableHash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;

public class MutableBlockHeader implements BlockHeader {
    protected Integer _version;
    protected Hash _previousBlockHash = new MutableHash();
    protected MerkleRoot _merkleRoot = new MutableMerkleRoot();
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;

    public MutableBlockHeader() {
        _version = VERSION;
    }

    @Override
    public Integer getVersion() { return _version; }
    public void setVersion(final Integer version) { _version = version; }

    @Override
    public Hash getPreviousBlockHash() { return _previousBlockHash; }
    public void setPreviousBlockHash(final Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash;
    }

    @Override
    public MerkleRoot getMerkleRoot() { return _merkleRoot; }
    public void setMerkleRoot(final MerkleRoot merkleRoot) {
        _merkleRoot = merkleRoot;
    }

    @Override
    public Long getTimestamp() { return _timestamp; }
    public void setTimestamp(final Long timestamp) { _timestamp = timestamp; }

    @Override
    public Difficulty getDifficulty() { return _difficulty; }
    public void setDifficulty(final Difficulty difficulty) { _difficulty = difficulty; }

    @Override
    public Long getNonce() { return  _nonce; }
    public void setNonce(final Long nonce) { _nonce = nonce; }

    @Override
    public Hash getHash() {
        final BlockHasher blockHasher = new BlockHasher();
        return blockHasher.calculateBlockHash(this);
    }

    @Override
    public Boolean isValid() {
        final BlockHasher blockHasher = new BlockHasher();
        final Hash calculatedHash = blockHasher.calculateBlockHash(this);
        return (_difficulty.isSatisfiedBy(calculatedHash));
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return new ImmutableBlockHeader(this);
    }
}

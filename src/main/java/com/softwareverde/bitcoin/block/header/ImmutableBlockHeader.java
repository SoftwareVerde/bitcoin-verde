package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.constable.Const;
import com.softwareverde.json.Json;

public class ImmutableBlockHeader implements BlockHeader, Const {
    protected final Sha256Hash _hash;
    protected final Sha256Hash _previousBlockHash;
    protected final MerkleRoot _merkleRoot;
    protected final Long _version;
    protected final Long _timestamp;
    protected final Difficulty _difficulty;
    protected final Long _nonce;

    public ImmutableBlockHeader(final BlockHeader blockHeader) {
        _hash = blockHeader.getHash();
        _previousBlockHash = blockHeader.getPreviousBlockHash().asConst();
        _merkleRoot = blockHeader.getMerkleRoot().asConst();
        _version = blockHeader.getVersion();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = blockHeader.getDifficulty().asConst();
        _nonce = blockHeader.getNonce();
    }

    @Override
    public Long getVersion() {
        return _version;
    }

    @Override
    public Sha256Hash getPreviousBlockHash() {
        return _previousBlockHash;
    }

    @Override
    public MerkleRoot getMerkleRoot() {
        return _merkleRoot;
    }

    @Override
    public Long getTimestamp() {
        return _timestamp;
    }

    @Override
    public Difficulty getDifficulty() {
        return _difficulty;
    }

    @Override
    public Long getNonce() {
        return _nonce;
    }

    @Override
    public Sha256Hash getHash() {
        return _hash;
    }

    @Override
    public Boolean isValid() {
        final BlockHasher blockHasher = new BlockHasher();
        final Sha256Hash calculatedHash = blockHasher.calculateBlockHash(this);
        if (! _hash.equals(calculatedHash)) { return false; }

        return (_difficulty.isSatisfiedBy(calculatedHash));
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return this;
    }

    @Override
    public Json toJson() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        return blockHeaderDeflater.toJson(this);
    }
}

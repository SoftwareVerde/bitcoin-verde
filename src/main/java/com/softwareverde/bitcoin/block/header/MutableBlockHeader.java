package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.type.merkleroot.MerkleRoot;
import com.softwareverde.bitcoin.type.merkleroot.MutableMerkleRoot;
import com.softwareverde.json.Json;
import com.softwareverde.util.Util;

public class MutableBlockHeader implements BlockHeader {
    protected Long _version;
    protected Sha256Hash _previousBlockHash = new MutableSha256Hash();
    protected MerkleRoot _merkleRoot = new MutableMerkleRoot();
    protected Long _timestamp;
    protected Difficulty _difficulty;
    protected Long _nonce;

    protected Integer _cachedHashCode = null;

    public MutableBlockHeader() {
        _version = VERSION;
    }

    public MutableBlockHeader(final BlockHeader blockHeader) {
        _version = blockHeader.getVersion();
        _previousBlockHash = blockHeader.getPreviousBlockHash().asConst();
        _merkleRoot = blockHeader.getMerkleRoot().asConst();
        _timestamp = blockHeader.getTimestamp();
        _difficulty = blockHeader.getDifficulty().asConst();
        _nonce = blockHeader.getNonce();
    }

    @Override
    public Long getVersion() { return _version; }

    public void setVersion(final Long version) {
        _version = version;
        _cachedHashCode = null;
    }

    @Override
    public Sha256Hash getPreviousBlockHash() { return _previousBlockHash; }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = previousBlockHash.asConst();
        _cachedHashCode = null;
    }

    @Override
    public MerkleRoot getMerkleRoot() { return _merkleRoot; }

    public void setMerkleRoot(final MerkleRoot merkleRoot) {
        _merkleRoot = merkleRoot.asConst();
        _cachedHashCode = null;
    }

    @Override
    public Long getTimestamp() { return _timestamp; }

    public void setTimestamp(final Long timestamp) {
        _timestamp = timestamp;
        _cachedHashCode = null;
    }

    @Override
    public Difficulty getDifficulty() { return _difficulty; }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = difficulty.asConst();
        _cachedHashCode = null;
    }

    @Override
    public Long getNonce() { return  _nonce; }
    public void setNonce(final Long nonce) { _nonce = nonce; }

    @Override
    public Sha256Hash getHash() {
        final BlockHasher blockHasher = new BlockHasher();
        return blockHasher.calculateBlockHash(this);
    }

    @Override
    public Boolean isValid() {
        final BlockHasher blockHasher = new BlockHasher();
        final Sha256Hash calculatedHash = blockHasher.calculateBlockHash(this);
        return (_difficulty.isSatisfiedBy(calculatedHash));
    }

    @Override
    public ImmutableBlockHeader asConst() {
        return new ImmutableBlockHeader(this);
    }

    @Override
    public Json toJson() {
        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        return blockHeaderDeflater.toJson(this);
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        final Integer hashCode = blockHeaderDeflater.toBytes(this).hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        if (! (object instanceof BlockHeader)) { return false; }
        return Util.areEqual(this.getHash(), ((BlockHeader) object).getHash());
    }
}

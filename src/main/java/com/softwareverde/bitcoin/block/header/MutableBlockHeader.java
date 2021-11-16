package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class MutableBlockHeader extends BlockHeaderCore {
    protected Sha256Hash _cachedHash = null;
    protected Integer _cachedHashCode = null;
    protected Boolean _cachedValidity = null;

    protected void _invalidateCachedProperties() {
        _cachedHashCode = null;
        _cachedHash = null;
        _cachedValidity = null;
    }

    public MutableBlockHeader() { }

    public MutableBlockHeader(final BlockHasher blockHasher) {
        super(blockHasher);
    }

    public MutableBlockHeader(final BlockHeader blockHeader) {
        super(blockHeader);
    }

    public void setVersion(final Long version) {
        _version = version;

        _invalidateCachedProperties();
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = (Sha256Hash) ConstUtil.asConstOrNull(previousBlockHash);

        _invalidateCachedProperties();
    }

    public void setMerkleRoot(final MerkleRoot merkleRoot) {
        _merkleRoot = (MerkleRoot) ConstUtil.asConstOrNull(merkleRoot);

        _invalidateCachedProperties();
    }

    public void setTimestamp(final Long timestamp) {
        _timestamp = timestamp;

        _invalidateCachedProperties();
    }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = ConstUtil.asConstOrNull(difficulty);

        _invalidateCachedProperties();
    }

    public void setNonce(final Long nonce) {
        _nonce = nonce;

        _invalidateCachedProperties();
    }

    @Override
    public Sha256Hash getHash() {
        final Sha256Hash cachedHash = _cachedHash;
        if (cachedHash != null) { return cachedHash; }

        final Sha256Hash hash = super.getHash();
        _cachedHash = hash;
        return hash;
    }

    @Override
    public Boolean isValid() {
        final Boolean cachedValidity = _cachedValidity;
        if (cachedValidity != null) { return cachedValidity; }

        final Boolean isValid = super.isValid();
        _cachedValidity = isValid;
        return isValid;
    }

    @Override
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final int hashCode = super.hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }

    @Override
    public boolean equals(final Object object) {
        return super.equals(object);
    }
}

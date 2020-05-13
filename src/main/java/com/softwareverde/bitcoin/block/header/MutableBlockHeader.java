package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.bitcoin.merkleroot.MerkleRoot;
import com.softwareverde.security.hash.sha256.Sha256Hash;

public class MutableBlockHeader extends BlockHeaderCore {
    protected Sha256Hash _cachedHash = null;
    protected Integer _cachedHashCode = null;

    public MutableBlockHeader() { }

    public MutableBlockHeader(final BlockHasher blockHasher) {
        super(blockHasher);
    }

    public MutableBlockHeader(final BlockHeader blockHeader) {
        super(blockHeader);
    }

    public void setVersion(final Long version) {
        _version = version;
        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setPreviousBlockHash(final Sha256Hash previousBlockHash) {
        _previousBlockHash = (Sha256Hash) ConstUtil.asConstOrNull(previousBlockHash);
        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setMerkleRoot(final MerkleRoot merkleRoot) {
        _merkleRoot = (MerkleRoot) ConstUtil.asConstOrNull(merkleRoot);
        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setTimestamp(final Long timestamp) {
        _timestamp = timestamp;
        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setDifficulty(final Difficulty difficulty) {
        _difficulty = ConstUtil.asConstOrNull(difficulty);
        _cachedHashCode = null;
        _cachedHash = null;
    }

    public void setNonce(final Long nonce) {
        _nonce = nonce;
        _cachedHashCode = null;
        _cachedHash = null;
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
    public int hashCode() {
        final Integer cachedHashCode = _cachedHashCode;
        if (cachedHashCode != null) { return cachedHashCode; }

        final int hashCode = super.hashCode();
        _cachedHashCode = hashCode;
        return hashCode;
    }
}

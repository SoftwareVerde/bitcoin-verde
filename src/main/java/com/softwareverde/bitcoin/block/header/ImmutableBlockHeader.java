package com.softwareverde.bitcoin.block.header;

import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.constable.Const;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class ImmutableBlockHeader extends BlockHeaderCore implements BlockHeader, Const {
    protected Integer _cachedHashCode = null;
    protected Sha256Hash _cachedHash = null;
    protected Boolean _cachedValidity = null;

    protected ImmutableBlockHeader(final BlockHasher blockHasher) {
        super(blockHasher);
    }

    public ImmutableBlockHeader() { }

    public ImmutableBlockHeader(final BlockHeader blockHeader) {
        super(blockHeader);
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
}

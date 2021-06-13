package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class UtxoCommitmentMetadata {
    public final Sha256Hash blockHash;
    public final Long blockHeight;
    public final Sha256Hash multisetHash;
    public final Long byteCount;

    public UtxoCommitmentMetadata(final Sha256Hash blockHash, final Long blockHeight, final Sha256Hash multisetHash, final Long byteCount) {
        this.blockHash = blockHash;
        this.blockHeight = blockHeight;
        this.multisetHash = multisetHash;
        this.byteCount = byteCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof UtxoCommitmentMetadata)) { return false; }

        final UtxoCommitmentMetadata utxoCommitmentMetadata = (UtxoCommitmentMetadata) object;
        if (! Util.areEqual(this.multisetHash, utxoCommitmentMetadata.multisetHash)) { return false; }
        if (! Util.areEqual(this.blockHash, utxoCommitmentMetadata.blockHash)) { return false; }
        if (! Util.areEqual(this.blockHeight, utxoCommitmentMetadata.blockHeight)) { return false; }
        if (! Util.areEqual(this.byteCount, utxoCommitmentMetadata.byteCount)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return this.multisetHash.hashCode();
    }
}

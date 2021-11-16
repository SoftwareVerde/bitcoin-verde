package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;

public class UtxoCommitmentMetadata {
    public final Sha256Hash blockHash;
    public final Long blockHeight;
    public final PublicKey publicKey;
    public final Long byteCount;

    public UtxoCommitmentMetadata(final Sha256Hash blockHash, final Long blockHeight, final PublicKey publicKey, final Long byteCount) {
        final PublicKey compressedPublicKey = publicKey.compress();

        this.blockHash = blockHash;
        this.blockHeight = blockHeight;
        this.publicKey = compressedPublicKey;
        this.byteCount = byteCount;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof UtxoCommitmentMetadata)) { return false; }

        final UtxoCommitmentMetadata utxoCommitmentMetadata = (UtxoCommitmentMetadata) object;
        if (! Util.areEqual(this.publicKey, utxoCommitmentMetadata.publicKey)) { return false; }
        if (! Util.areEqual(this.blockHash, utxoCommitmentMetadata.blockHash)) { return false; }
        if (! Util.areEqual(this.blockHeight, utxoCommitmentMetadata.blockHeight)) { return false; }
        if (! Util.areEqual(this.byteCount, utxoCommitmentMetadata.byteCount)) { return false; }

        return true;
    }

    @Override
    public int hashCode() {
        return this.publicKey.hashCode();
    }
}

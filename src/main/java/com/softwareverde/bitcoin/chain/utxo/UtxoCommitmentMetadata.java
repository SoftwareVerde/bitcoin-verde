package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class UtxoCommitmentMetadata {
    public final Sha256Hash blockHash;
    public final Sha256Hash multisetHash;
    public final Long byteCount;

    public UtxoCommitmentMetadata(final Sha256Hash blockHash, final Sha256Hash multisetHash, final Long byteCount) {
        this.blockHash = blockHash;
        this.multisetHash = multisetHash;
        this.byteCount = byteCount;
    }
}

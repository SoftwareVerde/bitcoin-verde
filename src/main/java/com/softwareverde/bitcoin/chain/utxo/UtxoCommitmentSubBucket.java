package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class UtxoCommitmentSubBucket extends MultisetBucket{
    public UtxoCommitmentSubBucket(final PublicKey multisetPublicKey, final Long byteCount) {
        super(multisetPublicKey, byteCount);
    }
}

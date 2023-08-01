package com.softwareverde.bitcoin.server.module.node.utxo;

import com.softwareverde.cryptography.secp256k1.key.PublicKey;

public class UtxoDatabaseSubBucket {
    public final Integer bucketIndex;

    public final Integer subBucketIndex;
    public final PublicKey subBucketPublicKey;
    public final Integer subBucketUtxoCount;
    public final Long subBucketByteCount;

    public UtxoDatabaseSubBucket(final Integer bucketIndex, final Integer subBucketIndex, final PublicKey subBucketPublicKey, final Integer subBucketUtxoCount, final Long subBucketByteCount) {
        this.bucketIndex = bucketIndex;

        this.subBucketIndex = subBucketIndex;
        this.subBucketPublicKey = subBucketPublicKey;
        this.subBucketUtxoCount = subBucketUtxoCount;
        this.subBucketByteCount = subBucketByteCount;
    }
}

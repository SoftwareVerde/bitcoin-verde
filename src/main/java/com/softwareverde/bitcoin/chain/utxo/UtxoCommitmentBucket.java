package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;

public class UtxoCommitmentBucket extends MultisetBucket {
    public static Boolean areSubBucketsEqual(final UtxoCommitmentBucket utxoCommitmentBucket0, final UtxoCommitmentBucket utxoCommitmentBucket1) {
        return Util.areEqual(utxoCommitmentBucket0._subBuckets, utxoCommitmentBucket1._subBuckets);
    }

    protected final List<UtxoCommitmentSubBucket> _subBuckets;

    public UtxoCommitmentBucket(final PublicKey multisetPublicKey, final Long byteCount) {
        this(multisetPublicKey, byteCount, null);
    }

    public UtxoCommitmentBucket(final PublicKey multisetPublicKey, final Long byteCount, final List<UtxoCommitmentSubBucket> subBuckets) {
        super(multisetPublicKey, byteCount);
        _subBuckets = subBuckets;
    }

    public Boolean hasSubBuckets() {
        return ( (_subBuckets != null) && (! _subBuckets.isEmpty()) );
    }

    public List<UtxoCommitmentSubBucket> getSubBuckets() {
        return (_subBuckets != null ? _subBuckets : new MutableArrayList<>(0));
    }

    /**
     * Returns true if object is a UtxoCommitmentBucket with the same MultisetHash and ByteCount;
     *  this function does not consider whether the subBuckets are identical or not.
     */
    @Override
    public boolean equals(final Object object) {
        if (this == object) { return true; }
        if (! (object instanceof UtxoCommitmentBucket)) { return false; }
        return super.equals(object);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}

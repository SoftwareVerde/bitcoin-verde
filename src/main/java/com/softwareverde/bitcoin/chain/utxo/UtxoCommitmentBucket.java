package com.softwareverde.bitcoin.chain.utxo;

import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;

public class UtxoCommitmentBucket extends MultisetBucket {
    protected final List<MultisetBucket> _subBuckets;

    public UtxoCommitmentBucket(final Sha256Hash multisetHash, final Long byteCount) {
        this(multisetHash, byteCount, null);
    }

    public UtxoCommitmentBucket(final Sha256Hash multisetHash, final Long byteCount, final List<MultisetBucket> subBuckets) {
        super(multisetHash, byteCount);
        _subBuckets = subBuckets;
    }

    public Boolean hasSubBuckets() {
        return ( (_subBuckets != null) && (! _subBuckets.isEmpty()) );
    }

    public List<MultisetBucket> getSubBuckets() {
        return (_subBuckets != null ? _subBuckets : new MutableList<>(0));
    }
}

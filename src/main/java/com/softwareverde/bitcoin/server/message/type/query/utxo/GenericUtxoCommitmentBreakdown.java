package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;

public class GenericUtxoCommitmentBreakdown<T extends MultisetBucket> {
    protected final UtxoCommitmentMetadata _metadata;
    protected final List<T> _buckets;

    public GenericUtxoCommitmentBreakdown(final UtxoCommitmentMetadata utxoCommitmentMetadata, final List<T> buckets) {
        _metadata = utxoCommitmentMetadata;
        _buckets = buckets;
    }

    public UtxoCommitmentMetadata getMetadata() {
        return _metadata;
    }

    public List<T> getBuckets() {
        return _buckets;
    }

    public Boolean isValid() {
        final EcMultiset ecMultiset = new EcMultiset();
        for (final MultisetBucket utxoCommitmentBucket : _buckets) {
            final PublicKey publicKey = utxoCommitmentBucket.getPublicKey();
            ecMultiset.add(publicKey);
        }

        final PublicKey multisetPublicKey = ecMultiset.getPublicKey();
        return Util.areEqual(_metadata.publicKey, multisetPublicKey);
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof GenericUtxoCommitmentBreakdown)) { return false; }

        final GenericUtxoCommitmentBreakdown<?> utxoCommitmentBreakdown = (GenericUtxoCommitmentBreakdown<?>) object;
        if (!Util.areEqual(_metadata, utxoCommitmentBreakdown.getMetadata())) { return false; }
        return Util.areEqual(_buckets, utxoCommitmentBreakdown.getBuckets());
    }

    @Override
    public int hashCode() {
        return _metadata.hashCode();
    }
}

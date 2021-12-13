package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.secp256k1.EcMultiset;
import com.softwareverde.cryptography.secp256k1.key.PublicKey;
import com.softwareverde.util.Util;

public class UtxoCommitmentBreakdown {
    public final UtxoCommitmentMetadata metadata;
    public final List<UtxoCommitmentBucket> buckets;

    public UtxoCommitmentBreakdown(final UtxoCommitmentMetadata utxoCommitmentMetadata, final List<UtxoCommitmentBucket> buckets) {
        this.metadata = utxoCommitmentMetadata;
        this.buckets = buckets;
    }

    public Boolean isValid() {
        final EcMultiset ecMultiset = new EcMultiset();
        for (final UtxoCommitmentBucket utxoCommitmentBucket : this.buckets) {
            final PublicKey publicKey = utxoCommitmentBucket.getPublicKey();
            ecMultiset.add(publicKey);
        }

        final PublicKey multisetPublicKey = ecMultiset.getPublicKey();
        return Util.areEqual(this.metadata.publicKey, multisetPublicKey);
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) { return true; }
        if (! (object instanceof UtxoCommitmentBreakdown)) { return false; }

        final UtxoCommitmentBreakdown utxoCommitmentBreakdown = (UtxoCommitmentBreakdown) object;
        if (!Util.areEqual(this.metadata, utxoCommitmentBreakdown.metadata)) { return false; }
        return Util.areEqual(this.buckets, utxoCommitmentBreakdown.buckets);
    }

    @Override
    public int hashCode() {
        return this.metadata.hashCode();
    }
}

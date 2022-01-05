package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.MultisetBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.constable.list.List;

public class UtxoCommitmentBreakdown extends GenericUtxoCommitmentBreakdown<MultisetBucket> {
    public UtxoCommitmentBreakdown(final UtxoCommitmentMetadata utxoCommitmentMetadata, final List<MultisetBucket> buckets) {
        super(utxoCommitmentMetadata, buckets);
    }
}

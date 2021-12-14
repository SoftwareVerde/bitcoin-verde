package com.softwareverde.bitcoin.server.message.type.query.utxo;

import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentBucket;
import com.softwareverde.bitcoin.chain.utxo.UtxoCommitmentMetadata;
import com.softwareverde.bitcoin.constable.util.ConstUtil;
import com.softwareverde.constable.list.List;

public class NodeSpecificUtxoCommitmentBreakdown extends GenericUtxoCommitmentBreakdown<UtxoCommitmentBucket> {
    public NodeSpecificUtxoCommitmentBreakdown(final UtxoCommitmentMetadata utxoCommitmentMetadata, final List<UtxoCommitmentBucket> buckets) {
        super(utxoCommitmentMetadata, buckets);
    }

    public UtxoCommitmentBreakdown toUtxoCommitmentBreakdown() {
        return new UtxoCommitmentBreakdown(_metadata, ConstUtil.downcastList(_buckets));
    }
}

package com.softwareverde.bitcoin.chain.segment;

public class BlockchainSegment {
    public final BlockchainSegmentId id;
    public final BlockchainSegmentId parentBlockchainSegmentId;
    public final Long nestedSetLeft;
    public final Long nestedSetRight;

    public BlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final BlockchainSegmentId parentBlockchainSegmentId, final Long nestedSetLeft, final Long nestedSetRight) {
        this.id = blockchainSegmentId;
        this.parentBlockchainSegmentId = parentBlockchainSegmentId;
        this.nestedSetLeft = nestedSetLeft;
        this.nestedSetRight = nestedSetRight;
    }
}

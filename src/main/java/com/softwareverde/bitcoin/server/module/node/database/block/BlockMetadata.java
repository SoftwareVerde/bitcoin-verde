package com.softwareverde.bitcoin.server.module.node.database.block;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;

public class BlockMetadata {
    public final BlockchainSegmentId blockchainSegmentId;
    public final Long blockHeight;
    public final ChainWork chainWork;
    public final MedianBlockTime medianBlockTime;
    public final Boolean hasTransactions;
    public final BlockHeader blockHeader;

    public BlockMetadata(final BlockchainSegmentId blockchainSegmentId, final Long blockHeight, final ChainWork chainWork, final MedianBlockTime medianBlockTime, final Boolean hasTransactions, final BlockHeader blockHeader) {
        this.blockchainSegmentId = blockchainSegmentId;
        this.blockHeight = blockHeight;
        this.chainWork = chainWork;
        this.medianBlockTime = medianBlockTime;
        this.hasTransactions = hasTransactions;
        this.blockHeader = blockHeader;
    }
}

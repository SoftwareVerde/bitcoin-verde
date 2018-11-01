package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.database.Row;

public class BlockchainSegmentInflater {
    public BlockchainSegment fromRow(final Row row) {
        final BlockchainSegment blockchainSegment = new BlockchainSegment();
        blockchainSegment._id = BlockchainSegmentId.wrap(row.getLong("id"));
        blockchainSegment._headBlockId = BlockId.wrap(row.getLong("head_block_id"));
        blockchainSegment._tailBlockId = BlockId.wrap(row.getLong("tail_block_id"));
        blockchainSegment._blockHeight = row.getLong("block_height");
        blockchainSegment._blockCount = row.getLong("block_count");
        return blockchainSegment;
    }
}

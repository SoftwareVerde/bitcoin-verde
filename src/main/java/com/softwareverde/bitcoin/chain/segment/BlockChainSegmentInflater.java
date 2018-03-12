package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.database.Row;

public class BlockChainSegmentInflater {
    public BlockChainSegment fromRow(final Row row) {
        final BlockChainSegment blockChainSegment = new BlockChainSegment();
        blockChainSegment._id = BlockChainSegmentId.wrap(row.getLong("id"));
        blockChainSegment._headBlockId = BlockId.wrap(row.getLong("head_block_id"));
        blockChainSegment._tailBlockId = BlockId.wrap(row.getLong("tail_block_id"));
        blockChainSegment._blockHeight = row.getLong("block_height");
        blockChainSegment._blockCount = row.getLong("block_count");
        return blockChainSegment;
    }
}

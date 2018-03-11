package com.softwareverde.bitcoin.chain.segment;

import com.softwareverde.database.Row;

public class BlockChainSegmentInflater {
    public BlockChainSegment fromRow(final Row row) {
        final BlockChainSegment blockChainSegment = new BlockChainSegment();
        blockChainSegment._id = row.getLong("id");
        blockChainSegment._headBlockId = row.getLong("head_block_id");
        blockChainSegment._tailBlockId = row.getLong("tail_block_id");
        blockChainSegment._blockHeight = row.getLong("block_height");
        blockChainSegment._blockCount = row.getLong("block_count");
        return blockChainSegment;
    }
}

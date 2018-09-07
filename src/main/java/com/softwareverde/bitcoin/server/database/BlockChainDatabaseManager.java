package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentInflater;
import com.softwareverde.bitcoin.server.database.cache.BlockChainSegmentCache;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

public class BlockChainDatabaseManager {
    public static final BlockChainSegmentCache BLOCK_CHAIN_SEGMENT_CACHE = new BlockChainSegmentCache();

    protected final MysqlDatabaseConnection _databaseConnection;

    protected BlockChainSegment _inflateBlockChainSegmentFromId(final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        final BlockChainSegment cachedBlockChainSegment = BLOCK_CHAIN_SEGMENT_CACHE.loadCachedBlockChainSegment(blockChainSegmentId);
        if (cachedBlockChainSegment != null) { return cachedBlockChainSegment; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM block_chain_segments WHERE id = ?")
                .setParameter(blockChainSegmentId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final BlockChainSegmentInflater blockChainSegmentInflater = new BlockChainSegmentInflater();
        final BlockChainSegment blockChainSegment = blockChainSegmentInflater.fromRow(row);

        BLOCK_CHAIN_SEGMENT_CACHE.cacheBlockChainSegment(blockChainSegment);

        return blockChainSegment;
    }

    protected void _updateBlockChainsForNewBlock(final BlockHeader newBlock) throws DatabaseException {
        /*
            Each fork creates 2 new BlockChainSegments.
                Segment 0: Contains the blocks before the fork, but excluding any blocks from either side of the fork.
                    The head of this segment stops before the block causing the fork; therefore, this segment does not
                    contain the new block.  In the diagram below, this segment would include blocks 3 and 4.

                Segment 1: Contains the blocks existing before the fork and are siblings to the new block causing the
                    fork.  In the diagram below, this segment would include blocks 5 and 6.

                Segment 2: Contains the new block that is causing the fork.  In the diagram below, this segment would
                    include only block 5'.

            // Assuming block 5' arrives after 5, 6 have been created...

            [6]         // [5,6] = refactoredChainSegment
             |
            [5] [5']    // 5' = newBlock, [5'] = newChainSegment
             |   |
             +--[4]     // 4 = previousBlock, [...,3,4,5,6] = previousBlockChainSegment, [...,3,4] = baseBlockChainSegment
                 |
                [3]
         */

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        // 2. If the block is not contentious...
        //      2.1 If the newBlock is not the genesis block...
        //          2.1.1 Update the blockChainSegment's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
        //          2.1.2 Update the newBlock so its block_chain_segment_id points to the previousBlockChain's id.
        //      2.2 Else (the newBlock is the genesis block)...
        //          2.2.1 Create a new blockChainSegment and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
        //          2.2.2 Update the newBlock so its block_chain_segment_id points to the new blockChainSegment's id.
        //          2.2.3 Create a new
        // 3. Else (the block is contentious)...
        //      3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockChainSegment... ("refactoredBlocks")
        //      3.2 Update/revert the baseBlockChain.
        //          The head_block_id should point to the previousBlock.
        //          The block_height is the total number of blocks below this chain; this is equivalent to the original block height minus the number of blocks moved to the refactoredChain.
        //      3.3 If there are blocks that need to be refactored...
        //          3.3.1 Create a new block chain to house all of the already-existing blocks after the previousBlock.
        //              The tail_block_id of this chain should be set to the head_block_id of the baseChain.
        //              The block_count should be the number of refactored blocks (which excludes the head block of the baseChain).
        //              The block_height is the total number of blocks below this chain and all connected chains; this is equivalent to the original block height for this chain before it was refactored.
        //          3.3.2 Update the refactoredBlocks to belong to the new block chain.
        //      3.3 Update the new block chain to point to the correct tail_block_id, head_block_id, block_height, and block_count.
        //          3.3.1 Update the new block chain to point to the correct tail_block_id, head_block_id, block_height, and block_count.
        //          3.3.2 Update the refactoredBlocks to belong to the new refactoredBlockChain.
        //      3.4 Create a new block chain to house the newBlock (and its future children)...
        //          Set its head_block_id to the new block, and its tail_block_id to the previousBlockId (the head_block_id of the baseChain).
        //          Set its block_height to the baseChain's block_height plus 1, and its block_count to 1.
        //      3.5 Set the newBlock's block_chain_segment_id to the newBlockChain's id created in 3.4.

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        final BlockId newBlockId = blockDatabaseManager.getBlockIdFromHash(newBlock.getHash());
        final BlockId previousBlockId = blockDatabaseManager.getBlockIdFromHash(newBlock.getPreviousBlockHash());

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        final Boolean newBlockIsContentiousBlock = (blockDatabaseManager.getBlockDirectDescendantCount(previousBlockId) > 1);

        final BlockChainSegmentId previousBlockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(previousBlockId);
        if (! newBlockIsContentiousBlock) { // 2. If the block is not contentious...

            if (previousBlockChainSegmentId != null) { // 2.1 If the newBlock is not the genesis block...
                // 2.1.1 Update the blockChain's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
                BLOCK_CHAIN_SEGMENT_CACHE.clear(); // Invalidate cache due to update...
                _databaseConnection.executeSql(
                    new Query("UPDATE block_chain_segments SET head_block_id = ?, block_height = (block_height + 1), block_count = (block_count + 1) WHERE id = ?")
                        .setParameter(newBlockId)
                        .setParameter(previousBlockChainSegmentId)
                );

                // 2.1.2 Update the newBlock so its block_chain_segment_id points to the previousBlockChain's id.
                blockDatabaseManager.setBlockChainSegmentId(newBlockId, previousBlockChainSegmentId);
            }
            else { // 2.2 Else (the newBlock is the genesis block)...
                final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(newBlockId);
                if (blockChainSegmentId == null) { // If this block is not already assigned a blockChainSegment, create a new one...
                    // 2.2.1 Create a new blockChain and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
                    final BlockChainSegmentId genesisBlockChainSegmentId = BlockChainSegmentId.wrap(_databaseConnection.executeSql(
                        new Query("INSERT INTO block_chain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                            .setParameter(newBlockId)
                            .setParameter(newBlockId)
                            .setParameter(0)
                            .setParameter(1)
                    ));

                    // 2.2.2 Update the newBlock so its block_chain_segment_id points to the new blockChain's id.
                    blockDatabaseManager.setBlockChainSegmentId(newBlockId, genesisBlockChainSegmentId);
                }
            }
        }
        else { // 3. Else (the block is contentious)...
            final Long previousBlockBlockHeight = blockDatabaseManager.getBlockHeightForBlockId(previousBlockId);

            final BlockId refactoredChainHeadBlockId;
            final BlockId refactoredChainTailBlockId;
            final Long refactoredChainBlockHeight;
            final Integer refactoredChainBlockCount;
            { // 3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockChain... ("refactoredBlocks")
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id, block_height FROM blocks WHERE block_chain_segment_id = ? AND block_height > ? ORDER BY block_height ASC")
                        .setParameter(previousBlockChainSegmentId)
                        .setParameter(previousBlockBlockHeight)
                );

                refactoredChainBlockCount = rows.size();
                refactoredChainTailBlockId = previousBlockId;

                if (refactoredChainBlockCount > 0) {
                    final Row headBlockRow = rows.get(rows.size() - 1);
                    refactoredChainHeadBlockId = BlockId.wrap(headBlockRow.getLong("id"));
                    refactoredChainBlockHeight = headBlockRow.getLong("block_height");
                }
                else {
                    refactoredChainHeadBlockId = null;
                    refactoredChainBlockHeight = null;
                }
            }

            // 3.2 Update/revert the baseBlockChain.
            //  The head_block_id should point to the previousBlock.
            //  The block_height is the total number of blocks below this chain; this is equivalent to the original block height minus the number of blocks moved to the refactoredChain.
            BLOCK_CHAIN_SEGMENT_CACHE.clear(); // Invalidate cache due to update...
            _databaseConnection.executeSql(
                new Query("UPDATE block_chain_segments SET head_block_id = ?, block_height = ?, block_count = (block_count - ?) WHERE id = ?")
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight)
                    .setParameter(refactoredChainBlockCount)
                    .setParameter(previousBlockChainSegmentId)
            );

            if (refactoredChainBlockCount > 0) { // 3.3 If there are blocks that need to be refactored...
                // 3.3.1 Create a new block chain to house all of the already-existing blocks after the previousBlock.
                //  The tail_block_id of this chain should be set to the head_block_id of the baseChain.
                //  The block_count should be the number of refactored blocks (which excludes the head block of the baseChain).
                //  The block_height is the total number of blocks below this chain and all connected chains; this is equivalent to the original block height for this chain before it was refactored.
                final Long refactoredBlockChainSegmentId = _databaseConnection.executeSql(
                    new Query("INSERT INTO block_chain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                        .setParameter(refactoredChainHeadBlockId)
                        .setParameter(refactoredChainTailBlockId)
                        .setParameter(refactoredChainBlockHeight)
                        .setParameter(refactoredChainBlockCount)
                );

                // 3.3.2 Update the refactoredBlocks to belong to the new refactoredBlockChain.
                _databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_chain_segment_id = ? WHERE block_chain_segment_id = ? AND block_height > ?")
                        .setParameter(refactoredBlockChainSegmentId)
                        .setParameter(previousBlockChainSegmentId)
                        .setParameter(previousBlockBlockHeight)
                );
                BlockDatabaseManager.BLOCK_CHAIN_SEGMENT_CACHE.clear(); // Invalidate BlockChainSegmentId cache after update...
            }

            // 3.4 Create a new block chain to house the newBlock (and its future children)...
            //  Set its head_block_id to the new block, and its tail_block_id to the previousBlockId (the head_block_id of the baseChain).
            //  Set its block_height to the baseChain's block_height plus 1, and its block_count to 1.
            final BlockChainSegmentId newChainId = BlockChainSegmentId.wrap(_databaseConnection.executeSql(
                new Query("INSERT INTO block_chain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                    .setParameter(newBlockId)
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight + 1)
                    .setParameter(1)
            ));

            // 3.5 Set the newBlock's block_chain_id to the newBlockChain's id created in 3.4.
            blockDatabaseManager.setBlockChainSegmentId(newBlockId, newChainId);
        }
    }

    public BlockChainDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public void updateBlockChainsForNewBlock(final BlockHeader newBlock) throws DatabaseException {
        synchronized (BlockDatabaseManager.MUTEX) {
            _updateBlockChainsForNewBlock(newBlock);
        }
    }

    public BlockChainSegment getBlockChainSegment(final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        return _inflateBlockChainSegmentFromId(blockChainSegmentId);
    }

    public BlockChainSegmentId getHeadBlockChainSegmentId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM block_chain_segments ORDER BY block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockChainSegmentId.wrap(row.getLong("id"));
    }

    public BlockChainSegmentId getHeadBlockChainSegmentIdConnectedToBlockChainSegmentId(final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        final BlockChainSegment blockChainSegment = _inflateBlockChainSegmentFromId(blockChainSegmentId);
        if (blockChainSegment == null) { return null; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM block_chain_segments ORDER BY block_height DESC")
        );
        if (rows.isEmpty()) { return null; }

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);
        final BlockId blockId = blockChainSegment.getHeadBlockId();

        for (final Row row : rows) {
            final BlockChainSegmentId headBlockChainSegmentId = BlockChainSegmentId.wrap(row.getLong("id"));
            final Boolean blockIsConnectedToChain = blockDatabaseManager.isBlockConnectedToChain(blockId, headBlockChainSegmentId);

            if (blockIsConnectedToChain) {
                return headBlockChainSegmentId;
            }
        }

        return blockChainSegmentId;
    }

}

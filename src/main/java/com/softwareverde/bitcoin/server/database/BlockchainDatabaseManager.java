package com.softwareverde.bitcoin.server.database;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentInflater;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.io.Logger;

public class BlockchainDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected BlockchainSegment _inflateBlockchainSegmentFromId(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM blockchain_segments WHERE id = ?")
                .setParameter(blockchainSegmentId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final BlockchainSegmentInflater blockchainSegmentInflater = new BlockchainSegmentInflater();
        final BlockchainSegment blockchainSegment = blockchainSegmentInflater.fromRow(row);

        return blockchainSegment;
    }

    protected void _updateBlockchainsForNewBlock(final BlockHeader newBlock) throws DatabaseException {
        /*
            Each fork creates 2 new BlockchainSegments.
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
             +--[4]     // 4 = previousBlock, [...,3,4,5,6] = previousBlockchainSegment, [...,3,4] = baseBlockchainSegment
                 |
                [3]
         */

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        // 2. If the block is not contentious...
        //      2.1 If the newBlock is not the genesis block...
        //          2.1.1 Update the blockchainSegment's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
        //          2.1.2 Update the newBlock so its blockchain_segment_id points to the previousBlockchain's id.
        //      2.2 Else (the newBlock is the genesis block)...
        //          2.2.1 Create a new blockchainSegment and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
        //          2.2.2 Update the newBlock so its blockchain_segment_id points to the new blockchainSegment's id.
        //          2.2.3 Create a new
        // 3. Else (the block is contentious)...
        //      3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockchainSegment... ("refactoredBlocks")
        //      3.2 Update/revert the baseBlockchain.
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
        //          3.3.2 Update the refactoredBlocks to belong to the new refactoredBlockchain.
        //      3.4 Create a new block chain to house the newBlock (and its future children)...
        //          Set its head_block_id to the new block, and its tail_block_id to the previousBlockId (the head_block_id of the baseChain).
        //          Set its block_height to the baseChain's block_height plus 1, and its block_count to 1.
        //      3.5 Set the newBlock's blockchain_segment_id to the newBlockchain's id created in 3.4.

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);

        final BlockId newBlockId = blockHeaderDatabaseManager.getBlockHeaderId(newBlock.getHash());
        final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(newBlock.getPreviousBlockHash());

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        final Boolean newBlockIsContentiousBlock = (blockHeaderDatabaseManager.getBlockDirectDescendantCount(previousBlockId) > 1);

        final BlockchainSegmentId previousBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(previousBlockId);
        if (! newBlockIsContentiousBlock) { // 2. If the block is not contentious...

            if (previousBlockchainSegmentId != null) { // 2.1 If the newBlock is not the genesis block...
                // 2.1.1 Update the blockchain's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
                _databaseConnection.executeSql(
                    new Query("UPDATE blockchain_segments SET head_block_id = ?, block_height = (block_height + 1), block_count = (block_count + 1) WHERE id = ?")
                        .setParameter(newBlockId)
                        .setParameter(previousBlockchainSegmentId)
                );

                // 2.1.2 Update the newBlock so its blockchain_segment_id points to the previousBlockchain's id.
                blockHeaderDatabaseManager.setBlockchainSegmentId(newBlockId, previousBlockchainSegmentId);
            }
            else { // 2.2 Else (the newBlock is the genesis block)...
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newBlockId);
                if (blockchainSegmentId == null) { // If this block is not already assigned a blockchainSegment, create a new one...
                    // 2.2.1 Create a new blockchain and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
                    final BlockchainSegmentId genesisBlockchainSegmentId = BlockchainSegmentId.wrap(_databaseConnection.executeSql(
                        new Query("INSERT INTO blockchain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                            .setParameter(newBlockId)
                            .setParameter(newBlockId)
                            .setParameter(0)
                            .setParameter(1)
                    ));

                    // 2.2.2 Update the newBlock so its blockchain_segment_id points to the new blockchain's id.
                    blockHeaderDatabaseManager.setBlockchainSegmentId(newBlockId, genesisBlockchainSegmentId);
                }
            }
        }
        else { // 3. Else (the block is contentious)...
            final Long previousBlockBlockHeight = blockHeaderDatabaseManager.getBlockHeight(previousBlockId);

            final BlockId refactoredChainHeadBlockId;
            final BlockId refactoredChainTailBlockId;
            final Long refactoredChainBlockHeight;
            final Integer refactoredChainBlockCount;
            { // 3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockchain... ("refactoredBlocks")
                final java.util.List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id, block_height FROM blocks WHERE blockchain_segment_id = ? AND block_height > ? ORDER BY block_height ASC")
                        .setParameter(previousBlockchainSegmentId)
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

            // 3.2 Update/revert the baseBlockchain.
            //  The head_block_id should point to the previousBlock.
            //  The block_height is the total number of blocks below this chain; this is equivalent to the original block height minus the number of blocks moved to the refactoredChain.
            _databaseConnection.executeSql(
                new Query("UPDATE blockchain_segments SET head_block_id = ?, block_height = ?, block_count = (block_count - ?) WHERE id = ?")
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight)
                    .setParameter(refactoredChainBlockCount)
                    .setParameter(previousBlockchainSegmentId)
            );

            if (refactoredChainBlockCount > 0) { // 3.3 If there are blocks that need to be refactored...
                // 3.3.1 Create a new block chain to house all of the already-existing blocks after the previousBlock.
                //  The tail_block_id of this chain should be set to the head_block_id of the baseChain.
                //  The block_count should be the number of refactored blocks (which excludes the head block of the baseChain).
                //  The block_height is the total number of blocks below this chain and all connected chains; this is equivalent to the original block height for this chain before it was refactored.
                final Long refactoredBlockchainSegmentId = _databaseConnection.executeSql(
                    new Query("INSERT INTO blockchain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                        .setParameter(refactoredChainHeadBlockId)
                        .setParameter(refactoredChainTailBlockId)
                        .setParameter(refactoredChainBlockHeight)
                        .setParameter(refactoredChainBlockCount)
                );

                // 3.3.2 Update the refactoredBlocks to belong to the new refactoredBlockchain.
                _databaseConnection.executeSql(
                    new Query("UPDATE blocks SET blockchain_segment_id = ? WHERE blockchain_segment_id = ? AND block_height > ?")
                        .setParameter(refactoredBlockchainSegmentId)
                        .setParameter(previousBlockchainSegmentId)
                        .setParameter(previousBlockBlockHeight)
                );
                _databaseManagerCache.invalidateBlockIdBlockchainSegmentIdCache(); // Invalidate BlockchainSegmentId cache after update...
            }

            // 3.4 Create a new block chain to house the newBlock (and its future children)...
            //  Set its head_block_id to the new block, and its tail_block_id to the previousBlockId (the head_block_id of the baseChain).
            //  Set its block_height to the baseChain's block_height plus 1, and its block_count to 1.
            final BlockchainSegmentId newChainId = BlockchainSegmentId.wrap(_databaseConnection.executeSql(
                new Query("INSERT INTO blockchain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                    .setParameter(newBlockId)
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight + 1)
                    .setParameter(1)
            ));

            // 3.5 Set the newBlock's blockchain_id to the newBlockchain's id created in 3.4.
            blockHeaderDatabaseManager.setBlockchainSegmentId(newBlockId, newChainId);
        }
    }

    public BlockchainDatabaseManager(final MysqlDatabaseConnection databaseConnection, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnection = databaseConnection;
        _databaseManagerCache = databaseManagerCache;
    }

    public void updateBlockchainsForNewBlock(final BlockHeader newBlock) throws DatabaseException {
        final Sha256Hash blockHash = newBlock.getHash();

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) {
            Logger.log("NOTICE: Unable to update BlockchainSegment for block: " + blockHash);
            return;
        }

        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
        if (blockchainSegmentId != null) { return; }

        _updateBlockchainsForNewBlock(newBlock);
    }

    public BlockchainSegment getBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        return _inflateBlockchainSegmentFromId(blockchainSegmentId);
    }

    public BlockchainSegmentId getHeadBlockchainSegmentId() throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blockchain_segments ORDER BY block_height DESC LIMIT 1")
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockchainSegmentId.wrap(row.getLong("id"));
    }

    public BlockId getHeadBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id, head_block_id FROM blockchain_segments WHERE id = ?")
                .setParameter(blockchainSegmentId)
        );
        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockId.wrap(row.getLong("head_block_id"));
    }

    public BlockchainSegmentId getHeadBlockchainSegmentIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final BlockchainSegment blockchainSegment = _inflateBlockchainSegmentFromId(blockchainSegmentId);
        if (blockchainSegment == null) { return null; }

        final java.util.List<Row> rows = _databaseConnection.query(
            new Query("SELECT id FROM blockchain_segments ORDER BY block_height DESC")
        );
        if (rows.isEmpty()) { return null; }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(_databaseConnection, _databaseManagerCache);
        final BlockId blockId = blockchainSegment.getHeadBlockId();

        for (final Row row : rows) {
            final BlockchainSegmentId headBlockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("id"));
            final Boolean blockIsConnectedToChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANCESTOR);

            if (blockIsConnectedToChain) {
                return headBlockchainSegmentId;
            }
        }

        return blockchainSegmentId;
    }

}

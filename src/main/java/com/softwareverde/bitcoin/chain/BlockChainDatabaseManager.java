package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegment;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentInflater;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class BlockChainDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected BlockChainSegment _inflateBlockChainSegmentFromId(final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM block_chain_segments WHERE id = ?")
                .setParameter(blockChainSegmentId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final BlockChainSegmentInflater blockChainSegmentInflater = new BlockChainSegmentInflater();
        return blockChainSegmentInflater.fromRow(row);
    }

    protected BlockChainSegmentId _getBlockChainSegmentIdFromBlockId(final BlockId blockId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT block_chain_segment_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return BlockChainSegmentId.wrap(row.getLong("block_chain_segment_id"));
    }

    public BlockChainDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public void updateBlockChainsForNewBlock(final Block newBlock) throws DatabaseException {

        /*  Assuming block 5' arrives after 5, 6 have been created...

            [6]         // [5,6] = refactoredChain
             |
            [5] [5']    // 5' = newBlock, [5'] = newChain
             |   |
             +--[4]     // 4 = previousBlock, [...3,4,5,6] = previousBlockChain, [...3,4] = baseBlockChain
                 |
                [3]
         */

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        // 2. If the block is not contentious...
        //      2.1 If the newBlock is not the genesis block...
        //          2.1.1 Update the blockChain's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
        //          2.1.2 Update the newBlock so its block_chain_segment_id points to the previousBlockChain's id.
        //      2.2 Else (the newBlock is the genesis block)...
        //          2.2.1 Create a new blockChain and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
        //          2.2.2 Update the newBlock so its block_chain_segment_id points to the new blockChain's id.
        // 3. Else (the block is contentious)...
        //      3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockChain... ("refactoredBlocks")
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

        final BlockChainSegmentId previousBlockChainSegmentId = _getBlockChainSegmentIdFromBlockId(previousBlockId);
        if (! newBlockIsContentiousBlock) { // 2. If the block is not contentious...

            if (previousBlockChainSegmentId != null) { // 2.1 If the newBlock is not the genesis block...
                // 2.1.1 Update the blockChain's head_block_id to point to the newBlock, and increase its block_height and block_count by 1.
                _databaseConnection.executeSql(
                    new Query("UPDATE block_chain_segments SET head_block_id = ?, block_height = (block_height + 1), block_count = (block_count + 1) WHERE id = ?")
                        .setParameter(newBlockId)
                        .setParameter(previousBlockChainSegmentId)
                );

                // 2.1.2 Update the newBlock so its block_chain_segment_id points to the previousBlockChain's id.
                blockDatabaseManager.setBlockChainSegmentIdForBlockId(newBlockId, previousBlockChainSegmentId);
            }
            else { // 2.2 Else (the newBlock is the genesis block)...
                // 2.2.1 Create a new blockChain and set its block_count to 1, its block_height to 0, and its head_block_id and tail_block_id to the newBlock's id.
                final BlockChainSegmentId genesisBlockChainSegmentId = BlockChainSegmentId.wrap(_databaseConnection.executeSql(
                    new Query("INSERT INTO block_chain_segments (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                        .setParameter(newBlockId)
                        .setParameter(newBlockId)
                        .setParameter(0)
                        .setParameter(1)
                ));

                // 2.2.2 Update the newBlock so its block_chain_segment_id points to the new blockChain's id.
                blockDatabaseManager.setBlockChainSegmentIdForBlockId(newBlockId, genesisBlockChainSegmentId);
            }
        }
        else { // 3. Else (the block is contentious)...

            final Long previousBlockBlockHeight = blockDatabaseManager.getBlockHeightForBlockId(previousBlockId);

            final BlockId refactoredChainHeadBlockId;
            final BlockId refactoredChainTailBlockId;
            final Long refactoredChainBlockHeight;
            final Integer refactoredChainBlockCount;
            { // 3.1 Find all blocks after the previousBlock belonging to the previousBlock's blockChain... ("refactoredBlocks")
                final List<Row> rows = _databaseConnection.query(
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
            blockDatabaseManager.setBlockChainSegmentIdForBlockId(newBlockId, newChainId);
        }
    }

    public com.softwareverde.constable.list.List<BlockChainId> getBlockChainId(final BlockChainSegmentId blockChainSegmentId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(new Query("SELECT block_chain_id FROM block_chain_block_segments WHERE block_chain_segment_id = ?")
            .setParameter(blockChainSegmentId)
        );

        final ImmutableListBuilder<BlockChainId> listBuilder = new ImmutableListBuilder<BlockChainId>(rows.size());
        for (final Row row : rows) {
            listBuilder.add(BlockChainId.wrap(row.getLong("block_chain_id")));
        }
        return listBuilder.build();
    }
}

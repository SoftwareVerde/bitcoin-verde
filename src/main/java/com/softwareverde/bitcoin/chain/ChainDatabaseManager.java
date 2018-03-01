package com.softwareverde.bitcoin.chain;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;

import java.util.List;

public class ChainDatabaseManager {
    protected final MysqlDatabaseConnection _databaseConnection;

    protected BlockChain _inflateBlockChainFromId(final Long blockChainId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT * FROM block_chains WHERE id = ?")
                .setParameter(blockChainId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        final BlockChain blockChain = new BlockChain();
        blockChain._id = row.getLong("id");
        blockChain._headBlockId = row.getLong("head_block_id");
        blockChain._tailBlockId = row.getLong("tail_block_id");
        blockChain._blockHeight = row.getLong("block_height");
        blockChain._blockCount = row.getLong("block_count");
        return blockChain;
    }

    protected Long _getBlockChainIdFromBlockId(final Long blockId) throws DatabaseException {
        final List<Row> rows = _databaseConnection.query(
            new Query("SELECT block_chain_id FROM blocks WHERE id = ?")
                .setParameter(blockId)
        );

        if (rows.isEmpty()) { return null; }

        final Row row = rows.get(0);
        return row.getLong("block_chain_id");
    }

    public ChainDatabaseManager(final MysqlDatabaseConnection databaseConnection) {
        _databaseConnection = databaseConnection;
    }

    public void updateBlockChainsForNewBlock(final Block newBlock) throws DatabaseException {
        /*
                     [8'']
                      |
            [7]      [7'']
             |        |
            [6] [6'] [6'']
             |   |    |
            [5] [5'] [5'']
             |   |    |
             +--[4]---+
                 |
                [3]
                 |

         */


        /*  Assuming block 5' arrives after 5, 6 have been created...

            [6]         // [5,6] = refactoredChain
             |
            [5] [5']    // 5' = newBlock, [5'] = newChain
             |   |
             +--[4]     // 4 = previousBlock, [...3,4,5,6] = previousBlockChain, [...3,4] = newBaseBlockChain
                 |
                [3]
         */

        // 1. Check if the parent block has any children.  This determines if the new block is contentious.
        // 2. If the block is not contentious...
        //      2.1 Update the block_chain to point to the new block, and increase its block height and block count.
        //      2.2 Update the new block so that its block_chain_id points to the previousBlock's block_chain id.
        // 3. If the block is contentious...
        //      3.1 Create a new block chain to house all of the existing blocks after the previousBlock.
        //      3.2 Find all blocks after the previousBlock belonging to the previousBlock's blockChain...
        //          3.2.1 Update these blocks to belong to the new block chain.
        //          3.2.2 Update the new block chain to point to the correct tail_block_id, head_block_id, block_height, and block_count.
        //      3.3 Update the newBaseBlockChain and revert its head_block_id, block_height, and block_count.
        //      3.4 Create a new block chain to house the contentious block and its future children.
        //          Set its head_block_id to the new block, and its tail_block_id to the previousBlockId (the last block within the original chain).
        //          Set its block_height to the proper value, and its block_count to 1.
        //      3.5 Set the new block block_chain_id to the blockChain created in 3.3.

        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(_databaseConnection);

        final Long newBlockId = blockDatabaseManager.getBlockIdFromHash(newBlock.calculateSha256Hash());
        final Long previousBlockId = blockDatabaseManager.getBlockIdFromHash(newBlock.getPreviousBlockHash());
        final Boolean newBlockIsContentiousBlock = (blockDatabaseManager.getBlockDirectDescendantCount(previousBlockId) > 1);

        final Long previousBlockChainId = _getBlockChainIdFromBlockId(previousBlockId);
        if (! newBlockIsContentiousBlock) {
            if (previousBlockChainId != null) {
                _databaseConnection.executeSql(
                    new Query("UPDATE block_chains SET head_block_id = ?, block_height = (block_height + 1), block_count = (block_count + 1) WHERE id = ?")
                        .setParameter(newBlockId)
                        .setParameter(previousBlockChainId)
                );
                blockDatabaseManager.setBlockChainIdForBlockId(newBlockId, previousBlockChainId);
            }
            else {
                final Long genesisBlockChainId = _databaseConnection.executeSql(
                    new Query("INSERT INTO block_chains (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                        .setParameter(newBlockId)
                        .setParameter(newBlockId)
                        .setParameter(0)
                        .setParameter(1)
                );
                blockDatabaseManager.setBlockChainIdForBlockId(newBlockId, genesisBlockChainId);
            }
        }
        else {
            final Long previousBlockBlockHeight = blockDatabaseManager.getBlockHeightForBlockId(previousBlockId);

            final Long refactoredChainHeadBlockId;
            final Long refactoredChainTailBlockId;
            final Long refactoredChainBlockHeight;
            final Integer refactoredChainBlockCount;
            {
                // 3.2 Find all blocks after the previousBlock belonging to the previousBlock's blockChain...
                final List<Row> rows = _databaseConnection.query(
                    new Query("SELECT id, block_height FROM blocks WHERE block_chain_id = ? AND block_height > ? ORDER BY block_height ASC")
                        .setParameter(previousBlockChainId)
                        .setParameter(previousBlockBlockHeight)
                );

                refactoredChainBlockCount = rows.size();
                refactoredChainTailBlockId = previousBlockId;

                if (refactoredChainBlockCount > 0) {
                    final Row headBlockRow = rows.get(rows.size() - 1);
                    refactoredChainHeadBlockId = headBlockRow.getLong("id");
                    refactoredChainBlockHeight = headBlockRow.getLong("block_height");
                }
                else {
                    refactoredChainHeadBlockId = null;
                    refactoredChainBlockHeight = null;
                }
            }

            // 3.3 Update the newBaseBlockChain to revert its head_block_id, block_height, and block_count.
            _databaseConnection.executeSql(
                new Query("UPDATE block_chains SET head_block_id = ?, block_height = ?, block_count = (block_count - ?) WHERE id = ?")
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight)
                    .setParameter(refactoredChainBlockCount)
                    .setParameter(previousBlockChainId)
            );

            // 3.2.2 Update the new block chain to point to the correct tail_block_id, head_block_id, block_height, and block_count.
            if (refactoredChainBlockCount > 0) {
                final Long refactoredBlockChainId = _databaseConnection.executeSql(
                    new Query("INSERT INTO block_chains (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                        .setParameter(refactoredChainHeadBlockId)
                        .setParameter(refactoredChainTailBlockId)
                        .setParameter(refactoredChainBlockHeight)
                        .setParameter(refactoredChainBlockCount)
                );

                // 3.2.1 Update these blocks to belong to the new block chain.
                _databaseConnection.executeSql(
                    new Query("UPDATE blocks SET block_chain_id = ? WHERE block_chain_id = ? AND block_height > ?")
                        .setParameter(refactoredBlockChainId)
                        .setParameter(previousBlockChainId)
                        .setParameter(previousBlockBlockHeight)
                );
            }

            // 3.4 Create a new block chain to house the contentious block and its future children.
            final Long newChainId = _databaseConnection.executeSql(
                new Query("INSERT INTO block_chains (head_block_id, tail_block_id, block_height, block_count) VALUES (?, ?, ?, ?)")
                    .setParameter(newBlockId)
                    .setParameter(previousBlockId)
                    .setParameter(previousBlockBlockHeight + 1)
                    .setParameter(1)
            );

            // 3.5 Set the new block block_chain_id to the blockChain created in 3.3.
            blockDatabaseManager.setBlockChainIdForBlockId(newBlockId, newChainId);
        }
    }
}

package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.Query;
import com.softwareverde.database.Row;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public abstract class AbstractQueryBlocksHandler implements BitcoinNode.QueryBlockHeadersCallback {
    protected static class StartingBlock {
        public final BlockChainSegmentId selectedBlockChainSegmentId;
        public final BlockId startingBlockId;

        public StartingBlock(final BlockChainSegmentId blockChainSegmentId, final BlockId startingBlockId) {
            this.selectedBlockChainSegmentId = blockChainSegmentId;
            this.startingBlockId = startingBlockId;
        }
    }

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;

    protected AbstractQueryBlocksHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory) {
        _databaseConnectionFactory = databaseConnectionFactory;
    }

    protected List<BlockId> _findBlockChildrenIds(final BlockId blockId, final Sha256Hash desiredBlockHash, final BlockChainSegmentId blockChainSegmentId, final Integer maxCount, final BlockDatabaseManager blockDatabaseManager) throws DatabaseException {
        final MutableList<BlockId> returnedBlockIds = new MutableList<BlockId>();

        BlockId nextBlockId = blockId;
        while (true) {
            nextBlockId = blockDatabaseManager.getChildBlockId(blockChainSegmentId, nextBlockId);
            if (nextBlockId == null) { break; }

            final Sha256Hash addedBlockHash = blockDatabaseManager.getBlockHashFromId(nextBlockId);
            if (addedBlockHash == null) { break; }

            returnedBlockIds.add(nextBlockId);

            if (addedBlockHash.equals(desiredBlockHash)) { break; }
            if (returnedBlockIds.getSize() >= maxCount) { break; }
        }

        return returnedBlockIds;
    }

    protected StartingBlock _getStartingBlock(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

        final BlockChainSegmentId blockChainSegmentId;
        final BlockId startingBlockId;
        {
            BlockId foundBlockId = null;
            for (final Sha256Hash blockHash : blockHashes) {
                final java.util.List<Row> rows = databaseConnection.query(
                    new Query("SELECT id FROM blocks WHERE hash = ?")
                        .setParameter(blockHash)
                );
                if (rows.isEmpty()) {
                    continue;
                }

                final BlockId blockId = BlockId.wrap(rows.get(0).getLong("id"));
                if (blockId != null) {
                    foundBlockId = blockId;
                    break;
                }
            }

            if (foundBlockId == null) {
                final Sha256Hash headBlockHash = blockDatabaseManager.getHeadBlockHash();
                if (headBlockHash != null) {
                    final BlockId genesisBlockId = blockDatabaseManager.getBlockIdFromHash(Block.GENESIS_BLOCK_HASH);
                    foundBlockId = genesisBlockId;

                    final BlockId headBlockHashId = blockDatabaseManager.getBlockIdFromHash(headBlockHash);
                    blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(headBlockHashId);
                }
                else {
                    foundBlockId = null;
                    blockChainSegmentId = null;
                }
            }
            else {
                final BlockId desiredBlockId = blockDatabaseManager.getBlockIdFromHash(desiredBlockHash);
                if (desiredBlockId != null) {
                    blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(desiredBlockId);
                }
                else {
                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                    blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(headBlockId);
                }
            }

            startingBlockId = foundBlockId;
        }

        if ( (blockChainSegmentId == null) || (startingBlockId == null) ) {
            Logger.log("QueryBlocksHandler._getStartingBlock: " + blockChainSegmentId + " " + startingBlockId);
            return null;
        }

        return new StartingBlock(blockChainSegmentId, startingBlockId);
    }
}

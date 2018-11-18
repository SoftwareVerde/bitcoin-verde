package com.softwareverde.bitcoin.server.module.node.handler;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.DatabaseManagerCache;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.io.Logger;

public abstract class AbstractQueryBlocksHandler implements BitcoinNode.QueryBlockHeadersCallback {
    protected static class StartingBlock {
        public final BlockchainSegmentId selectedBlockchainSegmentId;
        public final BlockId startingBlockId;

        public StartingBlock(final BlockchainSegmentId blockchainSegmentId, final BlockId startingBlockId) {
            this.selectedBlockchainSegmentId = blockchainSegmentId;
            this.startingBlockId = startingBlockId;
        }
    }

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final DatabaseManagerCache _databaseManagerCache;

    protected AbstractQueryBlocksHandler(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final DatabaseManagerCache databaseManagerCache) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _databaseManagerCache = databaseManagerCache;
    }

    protected List<BlockId> _findBlockChildrenIds(final BlockId blockId, final Sha256Hash desiredBlockHash, final BlockchainSegmentId blockchainSegmentId, final Integer maxCount, final BlockHeaderDatabaseManager blockDatabaseManager) throws DatabaseException {
        final MutableList<BlockId> returnedBlockIds = new MutableList<BlockId>();

        BlockId nextBlockId = blockId;
        while (true) {
            nextBlockId = blockDatabaseManager.getChildBlockId(blockchainSegmentId, nextBlockId);
            if (nextBlockId == null) { break; }

            final Sha256Hash addedBlockHash = blockDatabaseManager.getBlockHash(nextBlockId);
            if (addedBlockHash == null) { break; }

            returnedBlockIds.add(nextBlockId);

            if (addedBlockHash.equals(desiredBlockHash)) { break; }
            if (returnedBlockIds.getSize() >= maxCount) { break; }
        }

        return returnedBlockIds;
    }

    protected StartingBlock _getStartingBlock(final List<Sha256Hash> blockHashes, final Sha256Hash desiredBlockHash, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, _databaseManagerCache);
        final BlockchainDatabaseManager blockchainDatabaseManager = new BlockchainDatabaseManager(databaseConnection, _databaseManagerCache);

        final BlockchainSegmentId blockchainSegmentId;
        final BlockId startingBlockId;
        {
            BlockId foundBlockId = null;
            for (final Sha256Hash blockHash : blockHashes) {
                final Boolean blockExists = blockDatabaseManager.blockHeaderHasTransactions(blockHash);
                if (blockExists) {
                    foundBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    break;
                }
            }

            if (foundBlockId != null) {
                final BlockId desiredBlockId = blockHeaderDatabaseManager.getBlockHeaderId(desiredBlockHash);
                if (desiredBlockId != null) {
                    blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(desiredBlockId);
                }
                else {
                    final BlockchainSegmentId foundBlockBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(foundBlockId);
                    blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentIdOfBlockchainSegment(foundBlockBlockchainSegmentId);
                }
            }
            else {
                final Sha256Hash headBlockHash = blockDatabaseManager.getHeadBlockHash();
                if (headBlockHash != null) {
                    final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(Block.GENESIS_BLOCK_HASH);
                    foundBlockId = genesisBlockId;
                    blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                }
                else {
                    foundBlockId = null;
                    blockchainSegmentId = null;
                }
            }

            startingBlockId = foundBlockId;
        }

        if ( (blockchainSegmentId == null) || (startingBlockId == null) ) {
            Logger.log("QueryBlocksHandler._getStartingBlock: " + blockchainSegmentId + " " + startingBlockId);
            return null;
        }

        return new StartingBlock(blockchainSegmentId, startingBlockId);
    }
}

package com.softwareverde.bitcoin.server.module.node.handler.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

public abstract class AbstractRequestBlocksHandler {
    protected static class StartingBlock {
        public final BlockchainSegmentId selectedBlockchainSegmentId;
        public final BlockId startingBlockId;
        public final Boolean matchWasFound;

        public StartingBlock(final BlockchainSegmentId blockchainSegmentId, final BlockId startingBlockId, final Boolean matchWasFound) {
            this.selectedBlockchainSegmentId = blockchainSegmentId;
            this.startingBlockId = startingBlockId;
            this.matchWasFound = matchWasFound;
        }
    }

    /**
     * Returns the children BlockIds of the provided blockId, until either maxCount is reached or desiredBlockHash is reached.
     *  The returned list of BlockIds does not include blockId.
     */
    protected List<BlockId> _findBlockChildrenIds(final BlockId blockId, final Sha256Hash desiredBlockHash, final BlockchainSegmentId blockchainSegmentId, final Integer maxCount, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        final MutableList<BlockId> returnedBlockIds = new MutableList<BlockId>();

        BlockId nextBlockId = blockId;
        while (true) {
            final Sha256Hash addedBlockHash = blockDatabaseManager.getBlockHash(nextBlockId);
            if (addedBlockHash == null) { break; }

            if (! Util.areEqual(blockId, nextBlockId)) {
                returnedBlockIds.add(nextBlockId);
            }

            if (addedBlockHash.equals(desiredBlockHash)) { break; }
            if (returnedBlockIds.getCount() >= maxCount) { break; }

            nextBlockId = blockDatabaseManager.getChildBlockId(blockchainSegmentId, nextBlockId);
            if (nextBlockId == null) { break; }
        }

        return returnedBlockIds;
    }

    protected StartingBlock _getStartingBlock(final List<Sha256Hash> blockHashes, final Boolean matchedHeaderMustHaveTransactions, final Sha256Hash desiredBlockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();

        final boolean matchWasFound;
        final BlockchainSegmentId blockchainSegmentId;
        final BlockId startingBlockId;
        {
            BlockId foundBlockId = null;
            for (final Sha256Hash blockHash : blockHashes) {
                if (matchedHeaderMustHaveTransactions) {
                    final Boolean blockExists = blockDatabaseManager.hasTransactions(blockHash);
                    if (blockExists) {
                        foundBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                        break;
                    }
                }
                else {
                    foundBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    if (foundBlockId != null) {
                        break;
                    }
                }
            }

            matchWasFound = (foundBlockId != null);

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
            Logger.debug("QueryBlocksHandler.getStartingBlock: " + blockchainSegmentId + " " + startingBlockId);
            return null;
        }

        return new StartingBlock(blockchainSegmentId, startingBlockId, matchWasFound);
    }
}

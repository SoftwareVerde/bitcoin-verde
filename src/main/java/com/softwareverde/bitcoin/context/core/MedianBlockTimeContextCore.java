package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class MedianBlockTimeContextCore implements MedianBlockTimeContext {
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;

    protected BlockId _getBlockId(final Long blockHeight) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        return blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
    }

    public MedianBlockTimeContextCore(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getMedianBlockTime(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}

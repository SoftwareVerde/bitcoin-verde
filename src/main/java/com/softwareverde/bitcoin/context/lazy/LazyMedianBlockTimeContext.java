package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class LazyMedianBlockTimeContext implements MedianBlockTimeContext {
    protected final DatabaseManager _databaseManager;

    protected BlockchainSegmentId _getHeadBlockchainSegmentId() throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        return blockchainDatabaseManager.getHeadBlockchainSegmentId();
    }

    protected BlockId _getBlockId(final Long blockHeight) throws DatabaseException {
        final BlockchainSegmentId blockchainSegmentId = _getHeadBlockchainSegmentId();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        return blockHeaderDatabaseManager.getBlockIdAtHeight(blockchainSegmentId, blockHeight);
    }

    public LazyMedianBlockTimeContext(final DatabaseManager databaseManager) {
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
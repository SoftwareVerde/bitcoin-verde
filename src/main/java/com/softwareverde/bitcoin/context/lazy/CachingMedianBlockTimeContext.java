package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

import java.util.HashMap;

public class CachingMedianBlockTimeContext implements MedianBlockTimeContext {
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;

    protected final HashMap<Long, BlockId> _blockIds = new HashMap<>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<>();

    protected BlockId _getBlockId(final Long blockHeight) throws DatabaseException {
        { // Check for a cached BlockId...
            final BlockId blockId = _blockIds.get(blockHeight);
            if (blockId != null) {
                return blockId;
            }
        }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
        if (blockId != null) {
            _blockIds.put(blockHeight, blockId);
        }

        return blockId;
    }

    public CachingMedianBlockTimeContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        { // Check for a cached value...
            final MedianBlockTime medianBlockTime = _medianBlockTimes.get(blockHeight);
            if (medianBlockTime != null) {
                return medianBlockTime;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(blockId);
            _medianBlockTimes.put(blockHeight, medianBlockTime);
            return medianBlockTime;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}
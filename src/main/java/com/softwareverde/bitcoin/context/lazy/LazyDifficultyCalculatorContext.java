package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.BlockHeaderContext;
import com.softwareverde.bitcoin.context.ChainWorkContext;
import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class LazyDifficultyCalculatorContext implements BlockHeaderContext, ChainWorkContext, MedianBlockTimeContext {
    final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;

    public LazyDifficultyCalculatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getBlockHeader(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public ChainWork getChainWork(final Long blockHeight) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getChainWork(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public MedianBlockTime getMedianBlockTime(final Long blockHeight) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }
}

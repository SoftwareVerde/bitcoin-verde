package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class LazyDifficultyCalculatorContext implements DifficultyCalculatorContext {
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;
    protected final AsertReferenceBlockLoader _asertReferenceBlockLoader;

    public LazyDifficultyCalculatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;

        final LazyReferenceBlockLoaderContext referenceBlockLoaderContext = new LazyReferenceBlockLoaderContext(databaseManager);
        _asertReferenceBlockLoader = new AsertReferenceBlockLoader(referenceBlockLoaderContext);
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

            return blockHeaderDatabaseManager.getMedianBlockTime(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public AsertReferenceBlock getAsertReferenceBlock() {
        return BitcoinConstants.getAsertReferenceBlock();
    }
}

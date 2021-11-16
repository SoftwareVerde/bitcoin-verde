package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;

public class LazyDifficultyCalculatorContext implements DifficultyCalculatorContext {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;
    protected final AsertReferenceBlockLoader _asertReferenceBlockLoader;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;


    public LazyDifficultyCalculatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager, final DifficultyCalculatorFactory difficultyCalculatorFactory, final UpgradeSchedule upgradeSchedule) {
        _blockchainSegmentId = blockchainSegmentId;
        _databaseManager = databaseManager;
        _difficultyCalculatorFactory = difficultyCalculatorFactory;
        _upgradeSchedule = upgradeSchedule;

        final LazyReferenceBlockLoaderContext referenceBlockLoaderContext = new LazyReferenceBlockLoaderContext(databaseManager, _upgradeSchedule);
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

    @Override
    public DifficultyCalculator newDifficultyCalculator() {
        return _difficultyCalculatorFactory.newDifficultyCalculator(this);
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}

package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.lazy.LazyReferenceBlockLoaderContext;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;

public class BlockHeaderValidatorContext extends MedianBlockTimeContextCore implements BlockHeaderValidator.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;

    protected final AsertReferenceBlockLoader _asertReferenceBlockLoader;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;

    public BlockHeaderValidatorContext(final BlockchainSegmentId blockchainSegmentId, final DatabaseManager databaseManager, final VolatileNetworkTime networkTime, final DifficultyCalculatorFactory difficultyCalculatorFactory, final UpgradeSchedule upgradeSchedule) {
        super(blockchainSegmentId, databaseManager);

        _upgradeSchedule = upgradeSchedule;
        _networkTime = networkTime;

        final LazyReferenceBlockLoaderContext referenceBlockLoaderContext = new LazyReferenceBlockLoaderContext(databaseManager, _upgradeSchedule);
        _asertReferenceBlockLoader = new AsertReferenceBlockLoader(referenceBlockLoaderContext);

        _difficultyCalculatorFactory = difficultyCalculatorFactory;
    }

    @Override
    public BlockHeader getBlockHeader(final Long blockHeight) {
        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
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
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            return blockHeaderDatabaseManager.getChainWork(blockId);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
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
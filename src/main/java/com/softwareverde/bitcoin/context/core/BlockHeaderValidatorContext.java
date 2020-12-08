package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.lazy.CachingMedianBlockTimeContext;
import com.softwareverde.bitcoin.context.lazy.LazyReferenceBlockLoaderContext;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class BlockHeaderValidatorContext extends CachingMedianBlockTimeContext implements BlockHeaderValidator.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final VolatileNetworkTime _networkTime;

    protected final HashMap<Long, BlockHeader> _blockHeaders = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();

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
        { // Check for a cached value...
            final BlockHeader blockHeader = _blockHeaders.get(blockHeight);
            if (blockHeader != null) {
                return blockHeader;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final BlockHeader blockHeader = blockHeaderDatabaseManager.getBlockHeader(blockId);
            _blockHeaders.put(blockHeight, blockHeader);
            return blockHeader;
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return null;
        }
    }

    @Override
    public ChainWork getChainWork(final Long blockHeight) {
        { // Check for a cached value...
            final ChainWork chainWork = _chainWorks.get(blockHeight);
            if (chainWork != null) {
                return chainWork;
            }
        }

        try {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
            final BlockId blockId = _getBlockId(blockHeight);
            if (blockId == null) { return null; }

            final ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(blockId);
            _chainWorks.put(blockHeight, chainWork);
            return chainWork;
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

    public void loadBlock(final Long blockHeight, final BlockId blockId, final BlockHeader blockHeader) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(blockId);
        final ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(blockId);

        _blockIds.put(blockHeight, blockId);
        _blockHeaders.put(blockHeight, blockHeader);
        _medianBlockTimes.put(blockHeight, medianBlockTime.asConst());
        _chainWorks.put(blockHeight, chainWork);
    }

    public void loadBlocks(final Long firstBlockHeight, final List<BlockId> blockIds, final List<BlockHeader> blockHeaders) throws DatabaseException {
        if (blockIds.isEmpty()) { return; }
        if (! Util.areEqual(blockIds.getCount(), blockHeaders.getCount())) {
            Logger.debug("BlockValidatorContext::loadBlocks parameter mismatch.");
            return;
        }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();

        final BlockId firstBlockId = blockIds.get(0);
        final MutableMedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(firstBlockId);

        ChainWork chainWork = blockHeaderDatabaseManager.getChainWork(firstBlockId);
        for (int i = 0; i < blockIds.getCount(); ++i) {
            final BlockId blockId = blockIds.get(i);
            final BlockHeader blockHeader = blockHeaders.get(i);
            final Long blockHeight = (firstBlockHeight + i);


            if (i > 0) {
                medianBlockTime.addBlock(blockHeader);

                final Difficulty difficulty = blockHeader.getDifficulty();
                final BlockWork blockWork = difficulty.calculateWork();
                chainWork = ChainWork.add(chainWork, blockWork);
            }

            _blockIds.put(blockHeight, blockId);
            _blockHeaders.put(blockHeight, blockHeader);
            _medianBlockTimes.put(blockHeight, medianBlockTime.asConst());
            _chainWorks.put(blockHeight, chainWork);
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
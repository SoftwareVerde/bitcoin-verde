package com.softwareverde.bitcoin.context.lazy;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.Difficulty;
import com.softwareverde.bitcoin.block.header.difficulty.work.BlockWork;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.block.validator.difficulty.AsertReferenceBlock;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.core.AsertReferenceBlockLoader;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.main.BitcoinConstants;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class LazyBlockValidatorContext implements BlockValidator.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final DatabaseManager _databaseManager;
    protected final VolatileNetworkTime _networkTime;
    protected final UnspentTransactionOutputContext _unspentTransactionOutputContext;
    protected final TransactionValidatorFactory _transactionValidatorFactory;
    protected final TransactionInflaters _transactionInflaters;
    protected final AsertReferenceBlockLoader _asertReferenceBlockLoader;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;

    protected final HashMap<Long, BlockId> _blockIds = new HashMap<Long, BlockId>();
    protected final HashMap<Long, BlockHeader> _blockHeaders = new HashMap<Long, BlockHeader>();
    protected final HashMap<Long, ChainWork> _chainWorks = new HashMap<Long, ChainWork>();
    protected final HashMap<Long, MedianBlockTime> _medianBlockTimes = new HashMap<Long, MedianBlockTime>();

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

    public LazyBlockValidatorContext(final TransactionInflaters transactionInflaters, final BlockchainSegmentId blockchainSegmentId, final UnspentTransactionOutputContext unspentTransactionOutputContext, final DifficultyCalculatorFactory difficultyCalculatorFactory, final TransactionValidatorFactory transactionValidatorFactory, final DatabaseManager databaseManager, final VolatileNetworkTime networkTime, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _transactionInflaters = transactionInflaters;
        _blockchainSegmentId = blockchainSegmentId;
        _unspentTransactionOutputContext = unspentTransactionOutputContext;
        _transactionValidatorFactory = transactionValidatorFactory;
        _databaseManager = databaseManager;
        _networkTime = networkTime;
        _difficultyCalculatorFactory = difficultyCalculatorFactory;

        final LazyReferenceBlockLoaderContext referenceBlockLoaderContext = new LazyReferenceBlockLoaderContext(databaseManager, _upgradeSchedule);
        _asertReferenceBlockLoader = new AsertReferenceBlockLoader(referenceBlockLoaderContext);
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
    public TransactionOutput getTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public Long getBlockHeight(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getBlockHeight(transactionOutputIdentifier);
    }

    @Override
    public Sha256Hash getBlockHash(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.getBlockHash(transactionOutputIdentifier);
    }

    @Override
    public Boolean isCoinbaseTransactionOutput(final TransactionOutputIdentifier transactionOutputIdentifier) {
        return _unspentTransactionOutputContext.isCoinbaseTransactionOutput(transactionOutputIdentifier);
    }

    @Override
    public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
        return _transactionValidatorFactory.getTransactionValidator(blockOutputs, transactionValidatorContext);
    }

    @Override
    public TransactionInflater getTransactionInflater() {
        return _transactionInflaters.getTransactionInflater();
    }

    @Override
    public TransactionDeflater getTransactionDeflater() {
        return _transactionInflaters.getTransactionDeflater();
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
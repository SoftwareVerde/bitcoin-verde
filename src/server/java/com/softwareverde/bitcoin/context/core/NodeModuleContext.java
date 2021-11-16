package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class NodeModuleContext implements BlockchainBuilder.Context, BlockHeaderDownloader.Context, BlockProcessor.Context, TransactionProcessor.Context, UpgradeScheduleContext {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final BlockInflaters _blockInflaters;
    protected final TransactionInflaters _transactionInflaters;
    protected final PendingBlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
    protected final BitcoinNodeManager _nodeManager;
    protected final SynchronizationStatus _synchronizationStatus;
    protected final MedianBlockTime _medianBlockTime;
    protected final SystemTime _systemTime;
    protected final ThreadPool _threadPool;
    protected final VolatileNetworkTime _networkTime;

    public NodeModuleContext(final BlockInflaters blockInflaters, final TransactionInflaters transactionInflaters, final PendingBlockStore pendingBlockStore, final FullNodeDatabaseManagerFactory databaseManagerFactory, final DifficultyCalculatorFactory difficultyCalculatorFactory, final BitcoinNodeManager nodeManager, final SynchronizationStatus synchronizationStatus, final MedianBlockTime medianBlockTime, final SystemTime systemTime, final ThreadPool threadPool, final VolatileNetworkTime networkTime, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _blockInflaters = blockInflaters;
        _transactionInflaters = transactionInflaters;
        _blockStore = pendingBlockStore;
        _databaseManagerFactory = databaseManagerFactory;
        _difficultyCalculatorFactory = difficultyCalculatorFactory;
        _nodeManager = nodeManager;
        _synchronizationStatus = synchronizationStatus;
        _medianBlockTime = medianBlockTime;
        _systemTime = systemTime;
        _threadPool = threadPool;
        _networkTime = networkTime;
    }

    @Override
    public BlockStore getBlockStore() {
        return _blockStore;
    }

    @Override
    public FullNodeDatabaseManagerFactory getDatabaseManagerFactory() {
        return _databaseManagerFactory;
    }

    @Override
    public VolatileNetworkTime getNetworkTime() {
        return _networkTime;
    }

    @Override
    public BitcoinNodeManager getBitcoinNodeManager() {
        return _nodeManager;
    }

    @Override
    public SynchronizationStatus getSynchronizationStatus() {
        return _synchronizationStatus;
    }

    @Override
    public SystemTime getSystemTime() {
        return _systemTime;
    }

    @Override
    public ThreadPool getThreadPool() {
        return _threadPool;
    }

    @Override
    public BlockInflater getBlockInflater() {
        return _blockInflaters.getBlockInflater();
    }

    @Override
    public BlockDeflater getBlockDeflater() {
        return _blockInflaters.getBlockDeflater();
    }

    @Override
    public TransactionValidator getTransactionValidator(final BlockOutputs blockOutputs, final TransactionValidator.Context transactionValidatorContext) {
        return new TransactionValidatorCore(blockOutputs, transactionValidatorContext);
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
    public DifficultyCalculator newDifficultyCalculator(final DifficultyCalculatorContext context) {
        return _difficultyCalculatorFactory.newDifficultyCalculator(context);
    }

    @Override
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}

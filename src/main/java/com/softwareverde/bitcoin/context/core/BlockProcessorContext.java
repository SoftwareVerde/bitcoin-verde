package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.network.time.VolatileNetworkTime;

public class BlockProcessorContext implements BlockProcessor.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final BlockInflaters _blockInflaters;
    protected final TransactionInflaters _transactionInflaters;
    protected final BlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final SynchronizationStatus _synchronizationStatus;
    protected final TransactionValidatorFactory _transactionValidatorFactory;

    public BlockProcessorContext(final BlockInflaters blockInflaters, final TransactionInflaters transactionInflaters, final BlockStore blockStore, final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileNetworkTime networkTime, final SynchronizationStatus synchronizationStatus, final TransactionValidatorFactory transactionValidatorFactory, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _blockInflaters = blockInflaters;
        _transactionInflaters = transactionInflaters;
        _blockStore = blockStore;
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _synchronizationStatus = synchronizationStatus;
        _transactionValidatorFactory = transactionValidatorFactory;
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
    public SynchronizationStatus getSynchronizationStatus() {
        return _synchronizationStatus;
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
    public UpgradeSchedule getUpgradeSchedule() {
        return _upgradeSchedule;
    }
}

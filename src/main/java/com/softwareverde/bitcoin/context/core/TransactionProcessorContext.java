package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.inflater.TransactionInflaters;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.transaction.TransactionDeflater;
import com.softwareverde.bitcoin.transaction.TransactionInflater;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class TransactionProcessorContext implements TransactionProcessor.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final TransactionInflaters _transactionInflaters;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final SystemTime _systemTime;
    protected final TransactionValidatorFactory _transactionValidatorFactory;

    public TransactionProcessorContext(final TransactionInflaters transactionInflaters, final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileNetworkTime networkTime, final SystemTime systemTime, final TransactionValidatorFactory transactionValidatorFactory, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _transactionInflaters = transactionInflaters;
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _systemTime = systemTime;
        _transactionValidatorFactory = transactionValidatorFactory;
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
    public SystemTime getSystemTime() {
        return _systemTime;
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

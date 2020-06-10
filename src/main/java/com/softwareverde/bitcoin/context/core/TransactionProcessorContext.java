package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.chain.time.VolatileMedianBlockTime;
import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class TransactionProcessorContext implements TransactionProcessor.Context {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final VolatileMedianBlockTime _medianBlockTime;
    protected final SystemTime _systemTime;
    protected final TransactionValidatorFactory _transactionValidatorFactory;

    public TransactionProcessorContext(final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileMedianBlockTime medianBlockTime, final VolatileNetworkTime networkTime, final SystemTime systemTime, final TransactionValidatorFactory transactionValidatorFactory) {
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _systemTime = systemTime;
        _transactionValidatorFactory = transactionValidatorFactory;
    }

    @Override
    public VolatileMedianBlockTime getHeadMedianBlockTime() {
        return _medianBlockTime;
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
}

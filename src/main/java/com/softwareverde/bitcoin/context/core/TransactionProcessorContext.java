package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.chain.time.VolatileMedianBlockTime;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.TransactionProcessor;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class TransactionProcessorContext implements TransactionProcessor.Context {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final VolatileMedianBlockTime _medianBlockTime;
    protected final SystemTime _systemTime;

    public TransactionProcessorContext(final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileMedianBlockTime medianBlockTime, final VolatileNetworkTime networkTime, final SystemTime systemTime) {
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _systemTime = systemTime;
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
}

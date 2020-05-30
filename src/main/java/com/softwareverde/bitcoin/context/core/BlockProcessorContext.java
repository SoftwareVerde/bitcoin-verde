package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.network.time.VolatileNetworkTime;

public class BlockProcessorContext implements BlockProcessor.Context {
    protected final BlockInflaters _blockInflaters;
    protected final BlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final SynchronizationStatus _synchronizationStatus;

    public BlockProcessorContext(final BlockInflaters blockInflaters, final BlockStore blockStore, final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileNetworkTime networkTime, final SynchronizationStatus synchronizationStatus) {
        _blockInflaters = blockInflaters;
        _blockStore = blockStore;
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _synchronizationStatus = synchronizationStatus;
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
}

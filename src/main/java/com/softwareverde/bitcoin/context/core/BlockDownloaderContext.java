package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class BlockDownloaderContext extends BlockHeaderDownloaderContext implements BlockDownloader.Context {
    protected final BlockInflaters _blockInflaters;
    protected final PendingBlockStore _pendingBlockStore;
    protected final SynchronizationStatus _synchronizationStatus;

    public BlockDownloaderContext(final BitcoinNodeManager nodeManager, final BlockInflaters blockInflaters, final FullNodeDatabaseManagerFactory databaseManagerFactory, final VolatileNetworkTime networkTime, final PendingBlockStore pendingBlockStore, final SynchronizationStatus synchronizationStatus, final SystemTime systemTime, final ThreadPool threadPool, final UpgradeSchedule upgradeSchedule) {
        super(nodeManager, databaseManagerFactory, networkTime, systemTime, threadPool, upgradeSchedule);

        _blockInflaters = blockInflaters;
        _pendingBlockStore = pendingBlockStore;
        _synchronizationStatus = synchronizationStatus;
    }

    @Override
    public FullNodeDatabaseManagerFactory getDatabaseManagerFactory() {
        return (FullNodeDatabaseManagerFactory) _databaseManagerFactory;
    }

    @Override
    public PendingBlockStore getPendingBlockStore() {
        return _pendingBlockStore;
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

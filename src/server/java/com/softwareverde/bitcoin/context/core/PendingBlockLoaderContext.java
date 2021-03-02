package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.concurrent.threadpool.ThreadPool;

public class PendingBlockLoaderContext implements PendingBlockLoader.Context {
    protected final BlockInflaters _blockInflaters;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final ThreadPool _threadPool;

    public PendingBlockLoaderContext(final BlockInflaters blockInflaters, final FullNodeDatabaseManagerFactory databaseManagerFactory, final ThreadPool threadPool) {
        _blockInflaters = blockInflaters;
        _databaseManagerFactory = databaseManagerFactory;
        _threadPool = threadPool;
    }

    @Override
    public FullNodeDatabaseManagerFactory getDatabaseManagerFactory() {
        return _databaseManagerFactory;
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
}

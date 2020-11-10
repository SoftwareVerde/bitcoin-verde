package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder;
import com.softwareverde.concurrent.pool.ThreadPool;

public class BlockchainBuilderContext implements BlockchainBuilder.Context {
    protected final BlockInflaters _blockInflaters;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _nodeManager;
    protected final ThreadPool _threadPool;

    public BlockchainBuilderContext(final BlockInflaters blockInflaters, final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final ThreadPool threadPool) {
        _blockInflaters = blockInflaters;
        _databaseManagerFactory = databaseManagerFactory;
        _nodeManager = bitcoinNodeManager;
        _threadPool = threadPool;
    }

    @Override
    public FullNodeDatabaseManagerFactory getDatabaseManagerFactory() {
        return _databaseManagerFactory;
    }

    @Override
    public BitcoinNodeManager getBitcoinNodeManager() {
        return _nodeManager;
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

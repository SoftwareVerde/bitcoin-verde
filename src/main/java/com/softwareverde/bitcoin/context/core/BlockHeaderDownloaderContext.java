package com.softwareverde.bitcoin.context.core;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.validator.difficulty.DifficultyCalculator;
import com.softwareverde.bitcoin.context.DifficultyCalculatorContext;
import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockHeaderDownloader;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.network.time.VolatileNetworkTime;
import com.softwareverde.util.type.time.SystemTime;

public class BlockHeaderDownloaderContext implements BlockHeaderDownloader.Context {
    protected final UpgradeSchedule _upgradeSchedule;
    protected final BitcoinNodeManager _nodeManager;
    protected final DatabaseManagerFactory _databaseManagerFactory;
    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
    protected final VolatileNetworkTime _networkTime;
    protected final SystemTime _systemTime;
    protected final ThreadPool _threadPool;

    public BlockHeaderDownloaderContext(final BitcoinNodeManager nodeManager, final DatabaseManagerFactory databaseManagerFactory, final DifficultyCalculatorFactory difficultyCalculatorFactory, final VolatileNetworkTime networkTime, final SystemTime systemTime, final ThreadPool threadPool, final UpgradeSchedule upgradeSchedule) {
        _upgradeSchedule = upgradeSchedule;
        _nodeManager = nodeManager;
        _databaseManagerFactory = databaseManagerFactory;
        _difficultyCalculatorFactory = difficultyCalculatorFactory;
        _networkTime = networkTime;
        _systemTime = systemTime;
        _threadPool = threadPool;
    }

    @Override
    public DatabaseManagerFactory getDatabaseManagerFactory() {
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
    public SystemTime getSystemTime() {
        return _systemTime;
    }

    @Override
    public ThreadPool getThreadPool() {
        return _threadPool;
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

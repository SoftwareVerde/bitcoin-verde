package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.debug.LoggingConnectionWrapper;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.timer.Timer;

public class BlockProcessor {
    protected final Object _statisticsMutex = new Object();
    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<Long>(100);
    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<Integer>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);
    protected final Container<Float> _averageTransactionsPerSecond = new Container<Float>(0F);

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final BitcoinNodeManager _nodeManager;
    protected final MutableMedianBlockTime _medianBlockTime;
    protected final ReadUncommittedDatabaseConnectionPool _readUncommittedDatabaseConnectionPool;

    protected Integer _maxThreadCount = 4;
    protected Integer _trustedBlockHeight = 0;

    public BlockProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final BitcoinNodeManager nodeManager, final MutableMedianBlockTime medianBlockTime, final ReadUncommittedDatabaseConnectionPool readUncommittedDatabaseConnectionPool) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _nodeManager = nodeManager;
        _readUncommittedDatabaseConnectionPool = readUncommittedDatabaseConnectionPool;

        _medianBlockTime = medianBlockTime;
    }

    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Integer trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    public Boolean processBlock(final Block block) {
        final NetworkTime networkTime = _nodeManager.getNetworkTime();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            synchronized (BlockDatabaseManager.MUTEX) {
                TransactionUtil.startTransaction(databaseConnection);

                final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
                final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

                final Timer storeBlockTimer = new Timer();
                final Timer updateBlockChainsTimer = new Timer();

                LoggingConnectionWrapper.reset();

                storeBlockTimer.start();
                final BlockId blockId = blockDatabaseManager.storeBlock(block); // blockDatabaseManager.insertBlock(block);
                storeBlockTimer.stop();

                updateBlockChainsTimer.start();
                blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
                updateBlockChainsTimer.stop();

                {
                    final int transactionCount = block.getTransactions().getSize();
                    Logger.log("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps).");
                    // Logger.log("Updated Chains " + updateBlockChainsTimer.getMillisecondsElapsed() + " ms");
                }

                final BlockValidator blockValidator = new BlockValidator(_readUncommittedDatabaseConnectionPool, networkTime, _medianBlockTime);
                blockValidator.setMaxThreadCount(_maxThreadCount);
                blockValidator.setTrustedBlockHeight(_trustedBlockHeight);
                final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

                final Timer blockValidationTimer = new Timer();
                blockValidationTimer.start();
                final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);
                blockValidationTimer.stop();

                LoggingConnectionWrapper.printLogs();
                BlockDatabaseManager.BLOCK_CHAIN_SEGMENT_CACHE.debug();
                BlockDatabaseManager.BLOCK_CHAIN_SEGMENT_CACHE.clearDebug();

                if (blockIsValid) {
                    _medianBlockTime.addBlock(block);
                    TransactionUtil.commitTransaction(databaseConnection);

                    final Integer blockTransactionCount = block.getTransactions().getSize();

                    final Float averageBlocksPerSecond;
                    final Float averageTransactionsPerSecond;
                    synchronized (_statisticsMutex) {
                        _blocksPerSecond.add(Math.round(blockValidationTimer.getMillisecondsElapsed() + storeBlockTimer.getMillisecondsElapsed() + updateBlockChainsTimer.getMillisecondsElapsed()));
                        _transactionsPerBlock.add(blockTransactionCount);

                        final Integer blockCount = _blocksPerSecond.size();
                        final Long validationTimeElapsed;
                        {
                            long value = 0L;
                            for (final Long elapsed : _blocksPerSecond) {
                                value += elapsed;
                            }
                            validationTimeElapsed = value;
                        }

                        final Integer totalTransactionCount;
                        {
                            int value = 0;
                            for (final Integer transactionCount : _transactionsPerBlock) {
                                value += transactionCount;
                            }
                            totalTransactionCount = value;
                        }

                        averageBlocksPerSecond = ( (blockCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
                        averageTransactionsPerSecond = ( (totalTransactionCount.floatValue() / validationTimeElapsed.floatValue()) * 1000F );
                    }

                    _averageBlocksPerSecond.value = averageBlocksPerSecond;
                    _averageTransactionsPerSecond.value = averageTransactionsPerSecond;

                    return true;
                }
                else {
                    TransactionUtil.rollbackTransaction(databaseConnection);
                }
            }
        }
        catch (final Exception exception) {
            exception.printStackTrace();
        }

        Logger.log("Invalid block: "+ block.getHash());
        return false;
    }

    public JsonRpcSocketServerHandler.StatisticsContainer getStatisticsContainer() {
        final JsonRpcSocketServerHandler.StatisticsContainer statisticsContainer = new JsonRpcSocketServerHandler.StatisticsContainer();
        statisticsContainer.averageBlocksPerSecond = _averageBlocksPerSecond;
        statisticsContainer.averageTransactionsPerSecond = _averageTransactionsPerSecond;
        return statisticsContainer;
    }
}

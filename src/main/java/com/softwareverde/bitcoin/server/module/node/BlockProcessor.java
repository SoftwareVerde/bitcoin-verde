package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;

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

    public BlockProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final BitcoinNodeManager nodeManager, final MutableMedianBlockTime medianBlockTime, final ReadUncommittedDatabaseConnectionPool readUncommittedDatabaseConnectionPool) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _nodeManager = nodeManager;
        _medianBlockTime = medianBlockTime;
        _readUncommittedDatabaseConnectionPool = readUncommittedDatabaseConnectionPool;
    }

    public Boolean processBlock(final Block block) {
        final NetworkTime networkTime = _nodeManager.getNetworkTime();

        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            TransactionUtil.startTransaction(databaseConnection);

            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection);

            long storeBlockStartTime = System.currentTimeMillis();
            final BlockId blockId = blockDatabaseManager.insertBlock(block);
            long storeBlockEndTime = System.currentTimeMillis();
            final Long storeBlockDuration = (storeBlockEndTime - storeBlockStartTime);

            blockChainDatabaseManager.updateBlockChainsForNewBlock(block);
            final BlockChainSegmentId blockChainSegmentId = blockChainDatabaseManager.getBlockChainSegmentId(blockId);

            final BlockValidator blockValidator = new BlockValidator(_readUncommittedDatabaseConnectionPool, networkTime, _medianBlockTime);
            final long blockValidationStartTime = System.currentTimeMillis();
            final Boolean blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);
            final long blockValidationEndTime = System.currentTimeMillis();
            final long blockValidationMsElapsed = (blockValidationEndTime - blockValidationStartTime);

            if (blockIsValid) {
                _medianBlockTime.addBlock(block);
                TransactionUtil.commitTransaction(databaseConnection);

                final Integer blockTransactionCount = block.getTransactions().getSize();

                final Float averageBlocksPerSecond;
                final Float averageTransactionsPerSecond;
                synchronized (_statisticsMutex) {
                    _blocksPerSecond.add(blockValidationMsElapsed + storeBlockDuration);
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

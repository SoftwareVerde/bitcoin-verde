package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.BlockChainDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.database.BlockRelationship;
import com.softwareverde.bitcoin.server.database.TransactionDatabaseManager;
import com.softwareverde.bitcoin.server.database.cache.LocalDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.cache.MasterDatabaseManagerCache;
import com.softwareverde.bitcoin.server.database.pool.MysqlDatabaseConnectionPool;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.mysql.MysqlDatabaseConnection;
import com.softwareverde.database.mysql.MysqlDatabaseConnectionFactory;
import com.softwareverde.database.mysql.debug.LoggingConnectionWrapper;
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

public class BlockProcessor {
    protected final Object _statisticsMutex = new Object();
    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<Long>(100);
    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<Integer>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);
    protected final Container<Float> _averageTransactionsPerSecond = new Container<Float>(0F);
    protected final NetworkTime _networkTime;

    protected final MysqlDatabaseConnectionFactory _databaseConnectionFactory;
    protected final MutableMedianBlockTime _medianBlockTime;
    protected final MasterDatabaseManagerCache _masterDatabaseManagerCache;

    protected Integer _maxThreadCount = 4;
    protected Integer _trustedBlockHeight = 0;

    public BlockProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final MasterDatabaseManagerCache masterDatabaseManagerCache, final NetworkTime networkTime, final MutableMedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;

        _medianBlockTime = medianBlockTime;
        _networkTime = networkTime;
    }

    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Integer trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    protected Boolean _processBlock(final Block block, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(_masterDatabaseManagerCache);

        final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, localDatabaseManagerCache);
        final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, localDatabaseManagerCache);
        final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, localDatabaseManagerCache);

        final Sha256Hash blockHash = block.getHash();
        final Boolean blockIsSynchronized = blockDatabaseManager.blockExists(blockHash);
        if (blockIsSynchronized) {
            Logger.log("Skipping known block: " + blockHash);
            return true;
        }

        TransactionUtil.startTransaction(databaseConnection);

        final BlockChainSegmentId originalHeadBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

        final Timer storeBlockTimer = new Timer();

        LoggingConnectionWrapper.reset();

        storeBlockTimer.start();
        final BlockId blockId = blockDatabaseManager.storeBlock(block);
        storeBlockTimer.stop();

        {
            final int transactionCount = block.getTransactions().getSize();
            Logger.log("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());
            // Logger.log("Updated Chains " + updateBlockChainsTimer.getMillisecondsElapsed() + " ms");
        }

        final Timer blockValidationTimer = new Timer();
        final Boolean blockIsValid;

        final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(_databaseConnectionFactory);
        try (final MysqlDatabaseConnectionPool readUncommittedDatabaseConnectionPool = new MysqlDatabaseConnectionPool(readUncommittedDatabaseConnectionFactory, _maxThreadCount)) {
            final BlockValidator blockValidator = new BlockValidator(readUncommittedDatabaseConnectionPool, localDatabaseManagerCache, _networkTime, _medianBlockTime);
            blockValidator.setMaxThreadCount(_maxThreadCount);
            blockValidator.setTrustedBlockHeight(_trustedBlockHeight);
            final BlockChainSegmentId blockChainSegmentId = blockDatabaseManager.getBlockChainSegmentId(blockId);

            blockValidationTimer.start();
            blockIsValid = blockValidator.validateBlock(blockChainSegmentId, block);
            blockValidationTimer.stop();

            localDatabaseManagerCache.log();
            localDatabaseManagerCache.resetLog();

        }

        if (! blockIsValid) {
            TransactionUtil.rollbackTransaction(databaseConnection);
            return false;
        }

        final BlockDeflater blockDeflater = new BlockDeflater();
        final Integer byteCount = blockDeflater.getByteCount(block);
        blockDatabaseManager.setBlockByteCount(blockId, byteCount);

        _medianBlockTime.addBlock(block);

        final BlockChainSegmentId newHeadBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();
        final Boolean bestBlockChainHasChanged = (! Util.areEqual(newHeadBlockChainSegmentId, originalHeadBlockChainSegmentId));

        if (bestBlockChainHasChanged) {
            // Rebuild the memory pool to include (valid) transactions that were broadcast/mined on the old chain but were excluded from the new chain...
            // 1. Take the block at the head of the old chain and add its transactions back into the pool... (Ignoring the coinbases...)
            BlockId nextBlockId = blockChainDatabaseManager.getHeadBlockIdOfBlockChainSegment(originalHeadBlockChainSegmentId);

            while (nextBlockId != null) {
                final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(nextBlockId);
                for (int i = 1; i < transactionIds.getSize(); ++i) {
                    final TransactionId transactionId = transactionIds.get(i);
                    transactionDatabaseManager.addTransactionToMemoryPool(transactionId);
                }

                // 2. Continue to traverse up the chain until the block is connected to the new headBlockChain...
                nextBlockId = blockDatabaseManager.getAncestorBlockId(nextBlockId, 1);
                final Boolean nextBlockIsConnectedToNewHeadBlockChain = blockDatabaseManager.isBlockConnectedToChain(nextBlockId, newHeadBlockChainSegmentId, BlockRelationship.ANCESTOR);
                if (nextBlockIsConnectedToNewHeadBlockChain) { break; }
            }

            // 3. Traverse down the chain to the new head of the chain and remove the transactions from those blocks from the memory pool...
            while (nextBlockId != null) {
                final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(nextBlockId);
                for (int i = 1; i < transactionIds.getSize(); ++i) {
                    final TransactionId transactionId = transactionIds.get(i);
                    transactionDatabaseManager.removeTransactionFromMemoryPool(transactionId);
                }

                nextBlockId = blockDatabaseManager.getChildBlockId(newHeadBlockChainSegmentId, nextBlockId);
            }

            // 4. Validate that the transactions are still valid on the new chain...
            final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, localDatabaseManagerCache, _networkTime, _medianBlockTime);
            transactionValidator.setLoggingEnabled(false);
            final Long blockHeight = blockDatabaseManager.getBlockHeightForBlockId(blockId);

            final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIdsFromMemoryPool();
            for (final TransactionId transactionId : transactionIds) {
                final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                final Boolean transactionIsValid = transactionValidator.validateTransaction(newHeadBlockChainSegmentId, blockHeight, transaction);
                if (! transactionIsValid) {
                    transactionDatabaseManager.removeTransactionFromMemoryPool(transactionId);
                }
            }
        }
        else {
            // Remove any transactions in the memory pool that were included in this block...
            final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(blockId);
            for (int i = 1; i < transactionIds.getSize(); ++i) {
                final TransactionId transactionId = transactionIds.get(i);
                transactionDatabaseManager.removeTransactionFromMemoryPool(transactionId);
            }
        }

        TransactionUtil.commitTransaction(databaseConnection);

        final Integer blockTransactionCount = block.getTransactions().getSize();

        final Float averageBlocksPerSecond;
        final Float averageTransactionsPerSecond;
        synchronized (_statisticsMutex) {
            _blocksPerSecond.add(Math.round(blockValidationTimer.getMillisecondsElapsed() + storeBlockTimer.getMillisecondsElapsed()));
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

        _masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseManagerCache);

        return true;
    }

    public Boolean processBlock(final Block block) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            synchronized (BlockDatabaseManager.MUTEX) {
                return _processBlock(block, databaseConnection);
            }
        }
        catch (final Exception exception) {
            Logger.log("ERROR VALIDATING BLOCK: " + block.getHash());
            Logger.log(exception);
        }

        return false;
    }

    public Container<Float> getAverageBlocksPerSecondContainer() {
        return _averageBlocksPerSecond;
    }

    public Container<Float> getAverageTransactionsPerSecondContainer() {
        return _averageTransactionsPerSecond;
    }
}

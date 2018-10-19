package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.chain.time.MutableMedianBlockTime;
import com.softwareverde.bitcoin.server.database.*;
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
import com.softwareverde.database.mysql.embedded.factory.ReadUncommittedDatabaseConnectionFactory;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

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

    protected Integer _processedBlockCount = 0;
    protected final Long _startTime;

    public BlockProcessor(final MysqlDatabaseConnectionFactory databaseConnectionFactory, final MasterDatabaseManagerCache masterDatabaseManagerCache, final NetworkTime networkTime, final MutableMedianBlockTime medianBlockTime) {
        _databaseConnectionFactory = databaseConnectionFactory;
        _masterDatabaseManagerCache = masterDatabaseManagerCache;

        _medianBlockTime = medianBlockTime;
        _networkTime = networkTime;

        _startTime = System.currentTimeMillis();
    }

    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setTrustedBlockHeight(final Integer trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    protected Long _processBlock(final Block block, final MysqlDatabaseConnection databaseConnection) throws DatabaseException {
        try (final LocalDatabaseManagerCache localDatabaseManagerCache = new LocalDatabaseManagerCache(_masterDatabaseManagerCache)) {
            final Sha256Hash blockHash = block.getHash();
            _processedBlockCount += 1;

            final BlockChainDatabaseManager blockChainDatabaseManager = new BlockChainDatabaseManager(databaseConnection, localDatabaseManagerCache);
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = new BlockHeaderDatabaseManager(databaseConnection, localDatabaseManagerCache);
            final BlockDatabaseManager blockDatabaseManager = new BlockDatabaseManager(databaseConnection, localDatabaseManagerCache);
            final TransactionDatabaseManager transactionDatabaseManager = new TransactionDatabaseManager(databaseConnection, localDatabaseManagerCache);

            final BlockChainSegmentId originalHeadBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();

            final BlockId blockId;
            final Boolean blockHeaderExists = blockHeaderDatabaseManager.blockHeaderExists(blockHash);
            if (blockHeaderExists) {
                final Boolean blockHasTransactions = blockDatabaseManager.blockHeaderHasTransactions(blockHash);
                if (blockHasTransactions) {
                    Logger.log("Skipping known block: " + blockHash);
                    final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                    return blockHeaderDatabaseManager.getBlockHeight(existingBlockId);
                }

                blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
            }
            else {
                // Store the BlockHeader...
                synchronized (BlockHeaderDatabaseManager.MUTEX) {
                    final NanoTimer storeBlockHeaderTimer = new NanoTimer();

                    TransactionUtil.startTransaction(databaseConnection);
                    {
                        Logger.log("Processing Block: " + blockHash);
                        final Boolean blockHasTransactions = blockDatabaseManager.blockHeaderHasTransactions(blockHash);
                        if (blockHasTransactions) {
                            Logger.log("Skipping known block: " + blockHash);
                            final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                            return blockHeaderDatabaseManager.getBlockHeight(existingBlockId);
                        }

                        storeBlockHeaderTimer.start();
                        blockId = blockHeaderDatabaseManager.storeBlockHeader(block);

                        if (blockId == null) {
                            Logger.log("Error storing BlockHeader: " + blockHash);
                            TransactionUtil.rollbackTransaction(databaseConnection);
                            return null;
                        }

                        final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(databaseConnection, localDatabaseManagerCache, _networkTime, _medianBlockTime);
                        final Boolean blockHeaderIsValid = blockHeaderValidator.validateBlockHeader(block);
                        if (!blockHeaderIsValid) {
                            Logger.log("Invalid BlockHeader: " + blockHash);
                            TransactionUtil.rollbackTransaction(databaseConnection);
                            return null;
                        }

                        storeBlockHeaderTimer.stop();
                    }
                    TransactionUtil.commitTransaction(databaseConnection);
                }
            }

            final NanoTimer storeBlockTimer = new NanoTimer();
            final NanoTimer blockValidationTimer = new NanoTimer();
            TransactionUtil.startTransaction(databaseConnection);
            {
                storeBlockTimer.start();
                final Boolean transactionsStoredSuccessfully = blockDatabaseManager.storeBlockTransactions(block); // Store the Block's transactions (the BlockHeader should have already been stored above)...
                storeBlockTimer.stop();

                if (!transactionsStoredSuccessfully) {
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    Logger.log("Invalid block. Unable to store transactions for block: " + blockHash);
                    return null;
                }

                final int transactionCount = block.getTransactions().getSize();
                Logger.log("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());

                final Boolean blockIsValid;

                final ReadUncommittedDatabaseConnectionFactory readUncommittedDatabaseConnectionFactory = new ReadUncommittedDatabaseConnectionFactory(_databaseConnectionFactory);
                try (final MysqlDatabaseConnectionPool readUncommittedDatabaseConnectionPool = new MysqlDatabaseConnectionPool(readUncommittedDatabaseConnectionFactory, _maxThreadCount)) {
                    final BlockValidator blockValidator = new BlockValidator(readUncommittedDatabaseConnectionPool, localDatabaseManagerCache, _networkTime, _medianBlockTime);
                    blockValidator.setMaxThreadCount(_maxThreadCount);
                    blockValidator.setTrustedBlockHeight(_trustedBlockHeight);

                    blockValidationTimer.start();
                    blockIsValid = blockValidator.validateBlockTransactions(blockId, block); // NOTE: Only validates the transactions since the blockHeader is validated separately above...
                    blockValidationTimer.stop();

                    // localDatabaseManagerCache.log();
                    localDatabaseManagerCache.resetLog();

                }

                if (! blockIsValid) {
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    Logger.log("Invalid block. Transactions did not validate for block: " + blockHash);
                    return null;
                }
            }
            TransactionUtil.commitTransaction(databaseConnection);

            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

            final BlockDeflater blockDeflater = new BlockDeflater();
            final Integer byteCount = blockDeflater.getByteCount(block);
            blockHeaderDatabaseManager.setBlockByteCount(blockId, byteCount);

            _medianBlockTime.addBlock(block);

            final BlockChainSegmentId newHeadBlockChainSegmentId = blockChainDatabaseManager.getHeadBlockChainSegmentId();
            final Boolean bestBlockChainHasChanged = (! Util.areEqual(newHeadBlockChainSegmentId, originalHeadBlockChainSegmentId));

            { // Maintain memory-pool correctness...
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
                        nextBlockId = blockHeaderDatabaseManager.getAncestorBlockId(nextBlockId, 1);
                        final Boolean nextBlockIsConnectedToNewHeadBlockChain = blockHeaderDatabaseManager.isBlockConnectedToChain(nextBlockId, newHeadBlockChainSegmentId, BlockRelationship.ANCESTOR);
                        if (nextBlockIsConnectedToNewHeadBlockChain) { break; }
                    }

                    // 3. Traverse down the chain to the new head of the chain and remove the transactions from those blocks from the memory pool...
                    while (nextBlockId != null) {
                        final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIds(nextBlockId);
                        for (int i = 1; i < transactionIds.getSize(); ++i) {
                            final TransactionId transactionId = transactionIds.get(i);
                            transactionDatabaseManager.removeTransactionFromMemoryPool(transactionId);
                        }

                        nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockChainSegmentId, nextBlockId);
                    }

                    // 4. Validate that the transactions are still valid on the new chain...
                    final TransactionValidator transactionValidator = new TransactionValidator(databaseConnection, localDatabaseManagerCache, _networkTime, _medianBlockTime);
                    transactionValidator.setLoggingEnabled(false);

                    final List<TransactionId> transactionIds = transactionDatabaseManager.getTransactionIdsFromMemoryPool();
                    for (final TransactionId transactionId : transactionIds) {
                        final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                        final Boolean transactionIsValid = transactionValidator.validateTransaction(newHeadBlockChainSegmentId, blockHeight, transaction, false);
                        if (!transactionIsValid) {
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
            }

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

            // _averageBlocksPerSecond.value = averageBlocksPerSecond;
            final Long now = System.currentTimeMillis();
            _averageBlocksPerSecond.value = ((_processedBlockCount.floatValue() / (now - _startTime)) * 1000.0F);
            _averageTransactionsPerSecond.value = averageTransactionsPerSecond;

            _masterDatabaseManagerCache.commitLocalDatabaseManagerCache(localDatabaseManagerCache);

            return blockHeight;
        }
    }

    public Long processBlock(final Block block) {
        try (final MysqlDatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            return _processBlock(block, databaseConnection);
        }
        catch (final Exception exception) {
            Logger.log("ERROR VALIDATING BLOCK: " + block.getHash());
            Logger.log(exception);
        }

        return null;
    }

    public Container<Float> getAverageBlocksPerSecondContainer() {
        return _averageBlocksPerSecond;
    }

    public Container<Float> getAverageTransactionsPerSecondContainer() {
        return _averageTransactionsPerSecond;
    }
}

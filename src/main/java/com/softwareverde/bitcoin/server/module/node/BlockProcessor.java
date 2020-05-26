package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockDeflater;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
import com.softwareverde.bitcoin.block.validator.BlockValidator;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.context.core.BlockHeaderValidatorContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.lazy.LazyBlockValidatorContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.indexer.TransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.server.module.node.handler.transaction.OrphanedTransactionsCache;
import com.softwareverde.bitcoin.server.module.node.store.BlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.BlockLoader;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidatorCore;
import com.softwareverde.concurrent.pool.SimpleThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.time.MutableNetworkTime;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Container;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

public class BlockProcessor {
    protected final Object _statisticsMutex = new Object();
    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<Long>(100);
    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<Integer>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);
    protected final Container<Float> _averageTransactionsPerSecond = new Container<Float>(0F);

    protected final SynchronizationStatus _synchronizationStatus;
    protected final BlockStore _blockStore;

    protected final BlockInflaters _blockInflaters;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final OrphanedTransactionsCache _orphanedTransactionsCache;
    protected final MutableNetworkTime _networkTime;

    protected Long _utxoCommitFrequency = 2016L;
    protected Integer _maxThreadCount = 4;
    protected Long _trustedBlockHeight = 0L;

    protected final Long _startTime;

    public BlockProcessor(
        final FullNodeDatabaseManagerFactory databaseManagerFactory,
        final BlockInflaters blockInflaters,
        final OrphanedTransactionsCache orphanedTransactionsCache,
        final BlockStore blockStore,
        final SynchronizationStatus synchronizationStatus,
        final MutableNetworkTime networkTime
    ) {
        _databaseManagerFactory = databaseManagerFactory;
        _blockInflaters = blockInflaters;

        _startTime = System.currentTimeMillis();

        _orphanedTransactionsCache = orphanedTransactionsCache;
        _blockStore = blockStore;
        _synchronizationStatus = synchronizationStatus;

        _networkTime = networkTime;
    }

    public void setMaxThreadCount(final Integer maxThreadCount) {
        _maxThreadCount = maxThreadCount;
    }

    public void setUtxoCommitFrequency(final Long utxoCommitFrequency) {
        _utxoCommitFrequency = utxoCommitFrequency;
    }

    public void setTrustedBlockHeight(final Long trustedBlockHeight) {
        _trustedBlockHeight = trustedBlockHeight;
    }

    protected static class ProcessBlockHeaderResult {
        protected final BlockId _blockId;
        protected final Long _blockHeight;
        protected final Boolean _blockPreviouslyExisted;

        public ProcessBlockHeaderResult(final BlockId blockId, final Long blockHeight, final Boolean blockWasAlreadyProcessed) {
            _blockId = blockId;
            _blockHeight = blockHeight;
            _blockPreviouslyExisted = blockWasAlreadyProcessed;
        }

        public BlockId getBlockId() {
            return _blockId;
        }

        public Boolean wasBlockAlreadyProcessed() {
            return _blockPreviouslyExisted;
        }

        public Long getBlockHeight() {
            return _blockHeight;
        }
    }

    protected ProcessBlockHeaderResult _processBlockHeader(final BlockHeader blockHeader, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final Sha256Hash blockHash = blockHeader.getHash();
        final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);

        final boolean blockHeaderExists = (existingBlockId != null);
        if (blockHeaderExists) {
            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(existingBlockId);

            final Boolean blockHasTransactions = blockDatabaseManager.hasTransactions(blockHash);
            if (blockHasTransactions) {
                Logger.debug("Skipping known block: " + blockHash);
                return new ProcessBlockHeaderResult(existingBlockId, blockHeight, true);
            }

            return new ProcessBlockHeaderResult(existingBlockId, blockHeight, false);
        }

        // Store the BlockHeader...
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

            final BlockId blockId;
            final Long blockHeight;
            TransactionUtil.startTransaction(databaseConnection);
            {
                Logger.debug("Processing Block: " + blockHash);
                blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);

                if (blockId == null) {
                    Logger.debug("Error storing BlockHeader: " + blockHash);
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    return null;
                }

                blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                final BlockHeaderValidatorContext blockHeaderValidatorContext = new BlockHeaderValidatorContext(blockchainSegmentId, databaseManager, _networkTime);
                final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(blockHeaderValidatorContext);
                final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight);
                if (! blockHeaderValidationResult.isValid) {
                    Logger.debug("Invalid BlockHeader: " + blockHeaderValidationResult.errorMessage + " (" + blockHash + ")");
                    TransactionUtil.rollbackTransaction(databaseConnection);
                    return null;
                }
            }
            TransactionUtil.commitTransaction(databaseConnection);
            return new ProcessBlockHeaderResult(blockId, blockHeight, false);
        }
    }

    protected Long _processBlock(final Block block, final UnspentTransactionOutputContext preLoadedUnspentTransactionOutputContext, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final NanoTimer processBlockTimer = new NanoTimer();
        final NanoTimer storeBlockTimer = new NanoTimer();
        final NanoTimer blockValidationTimer = new NanoTimer();
        processBlockTimer.start();

        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
        final TransactionOutputDatabaseManager transactionOutputDatabaseManager = databaseManager.getTransactionOutputDatabaseManager();

        // Keep a copy of the original head Block before the block is processed...
        final BlockId originalHeadBlockId = blockDatabaseManager.getHeadBlockId(); // NOTE: Head Block is not the same as head BlockHeader.

        final Sha256Hash blockHash = block.getHash();
        final List<Transaction> blockTransactions = block.getTransactions();
        final Integer transactionCount = blockTransactions.getCount();

        final BlockId blockId;
        final Long blockHeight;
        { // Process the BlockHeader.
            final ProcessBlockHeaderResult blockHeaderResult = _processBlockHeader(block, databaseManager);
            if (blockHeaderResult == null ) { return null; }

            // if the full Block has already been processed then abort processing it...
            if (blockHeaderResult.wasBlockAlreadyProcessed()) {
                return blockHeaderResult.getBlockHeight();
            }

            blockId = blockHeaderResult.getBlockId();
            blockHeight = blockHeaderResult.getBlockHeight();
        }

        final Boolean blockIsConnectedToUtxoSet;
        { // Determine if the Block should use the head Block UTXO set...
            final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
            final BlockchainSegmentId headBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(originalHeadBlockId);
            if (headBlockchainSegmentId == null) {
                blockIsConnectedToUtxoSet = true;
            }
            else {
                blockIsConnectedToUtxoSet = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);
            }
        }
        Logger.trace("blockIsConnectedToUtxoSet=" + blockIsConnectedToUtxoSet);

        TransactionUtil.startTransaction(databaseConnection);
        {
            final List<TransactionId> transactionIds;
            { // Store the Block's Transactions...
                storeBlockTimer.start();

                transactionIds = blockDatabaseManager.storeBlockTransactions(block);
                final boolean transactionsStoredSuccessfully = (transactionIds != null);

                if (transactionsStoredSuccessfully) {
                    if (_blockStore != null) {
                        _blockStore.storeBlock(block, blockHeight);
                    }
                }
                else {
                    if (_blockStore != null) {
                        _blockStore.removeBlock(blockHash, blockHeight);
                    }

                    TransactionUtil.rollbackTransaction(databaseConnection);
                    Logger.debug("Invalid block. Unable to store transactions for block: " + blockHash);
                    return null;
                }

                storeBlockTimer.stop();
                Logger.info("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000D)) + " tps). " + blockHash);
            }

            final UnspentTransactionOutputContext unspentTransactionOutputContext;
            {
                if ( blockIsConnectedToUtxoSet && (preLoadedUnspentTransactionOutputContext != null) ) {
                    unspentTransactionOutputContext = preLoadedUnspentTransactionOutputContext;
                }
                else {
                    final MutableUnspentTransactionOutputSet mutableUnspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                    final Boolean unspentTransactionOutputsExistForBlock = mutableUnspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight); // Ensure the the UTXOs for this block are pre-loaded into the cache...
                    if (!unspentTransactionOutputsExistForBlock) {
                        TransactionUtil.rollbackTransaction(databaseConnection);
                        Logger.debug("Invalid block. Could not find UTXOs for block: " + blockHash);
                        return null;
                    }
                    unspentTransactionOutputContext = mutableUnspentTransactionOutputSet;
                }
            }

            final Boolean blockIsValid;
            {
                final BlockValidator blockValidator;
                {
                    final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                    final LazyBlockValidatorContext blockValidatorContext = new LazyBlockValidatorContext(blockchainSegmentId, unspentTransactionOutputContext, databaseManager, _networkTime);
                    blockValidatorContext.loadBlock(blockHeight, blockId, block);
                    blockValidator = new BlockValidator(blockValidatorContext);
                }

                blockValidator.setMaxThreadCount(_maxThreadCount);
                blockValidator.setTrustedBlockHeight(_trustedBlockHeight);
                blockValidator.setShouldLogValidBlocks(true);

                blockValidationTimer.start();
                final BlockValidationResult blockValidationResult = blockValidator.validateBlockTransactions(block, blockHeight); // NOTE: Only validates the transactions since the blockHeader is validated separately above...
                if (! blockValidationResult.isValid) {
                    Logger.info(blockValidationResult.errorMessage);
                }
                blockIsValid = blockValidationResult.isValid;
                blockValidationTimer.stop();
            }

            if (! blockIsValid) {
                TransactionUtil.rollbackTransaction(databaseConnection);
                Logger.debug("Invalid block. Transactions did not validate for block: " + blockHash);
                return null;
            }

            { // Queue the transactions for processing...
                transactionOutputDatabaseManager.queueTransactionsForProcessing(transactionIds);
            }
        }

        final BlockDeflater blockDeflater = _blockInflaters.getBlockDeflater();
        final Integer byteCount = blockDeflater.getByteCount(block);
        blockHeaderDatabaseManager.setBlockByteCount(blockId, byteCount);

        final BlockchainSegmentId newHeadBlockchainSegmentId;
        {
            final BlockId newHeadBlockId = blockDatabaseManager.getHeadBlockId();
            newHeadBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newHeadBlockId);
        }

        final boolean bestBlockchainHasChanged;
        {
            if (blockIsConnectedToUtxoSet) {
                bestBlockchainHasChanged = false;
            }
            else {
                final BlockchainSegmentId blockchainSegmentIdOfOriginalHead = blockHeaderDatabaseManager.getBlockchainSegmentId(originalHeadBlockId);
                bestBlockchainHasChanged = (!blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentIdOfOriginalHead, newHeadBlockchainSegmentId, BlockRelationship.ANY));
            }
        }
        Logger.trace("bestBlockchainHasChanged=" + bestBlockchainHasChanged);

        { // Maintain utxo and mempool correctness...
            if (blockIsConnectedToUtxoSet) {
                if (blockHeight > 0L) { // Maintain the UTXO (Unspent Transaction Output) set (and exclude UTXOs from the genesis block)...
                    final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
                    final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, databaseConnectionFactory, _utxoCommitFrequency);
                    unspentTransactionOutputManager.applyBlockToUtxoSet(block, blockHeight);
                }

                { // Update mempool transactions...
                    final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(blockId);
                    final MutableList<TransactionId> mutableTransactionIds = new MutableList<TransactionId>(transactionIds);
                    mutableTransactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...

                    { // Remove any transactions in the memory pool that were included in this block...
                        transactionDatabaseManager.removeFromUnconfirmedTransactions(mutableTransactionIds);
                    }

                    { // Remove any transactions in the memory pool that are now considered double-spends...
                        final List<TransactionId> dependentUnconfirmedTransaction = transactionDatabaseManager.getUnconfirmedTransactionsDependingOnSpentInputsOf(blockTransactions);
                        final MutableList<TransactionId> transactionsToRemove = new MutableList<TransactionId>(dependentUnconfirmedTransaction);
                        while (! transactionsToRemove.isEmpty()) {
                            transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
                            final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
                            transactionsToRemove.clear();
                            transactionsToRemove.addAll(chainedInvalidTransactions);
                        }
                    }
                }
            }
            else if (bestBlockchainHasChanged) {
                // TODO: Mempool Reorgs should write/read-lock the mempool until complete...
                final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
                final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, databaseConnectionFactory, _utxoCommitFrequency);
                final MilliTimer timer = new MilliTimer();
                Logger.trace("Starting Unspent Transactions Reorganization: " + originalHeadBlockId + " -> " + blockId);
                timer.start();
                // Rebuild the memory pool to include (valid) transactions that were broadcast/mined on the old chain but were excluded from the new chain...
                // 1. Take the block at the head of the old chain and add its transactions back into the pool... (Ignoring the coinbases...)
                final BlockchainSegmentId oldHeadBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(originalHeadBlockId); // The original BlockchainSegmentId was most likely invalidated during reordering, so reacquire the new BlockchainSegmentIds via BlockId...
                BlockId nextBlockId = blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(oldHeadBlockchainSegmentId);
                long undoBlockHeight = blockHeaderDatabaseManager.getBlockHeight(nextBlockId);
                Logger.trace("Utxo Reorg - 1/6 complete.");

                while (nextBlockId != null) {
                    final Block nextBlock = blockDatabaseManager.getBlock(nextBlockId);
                    if (nextBlock != null) { // If the head block was just a processed header, then continue backwards without processing its transactions...
                        final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(nextBlockId);

                        { // Remove UTXOs from the UTXO set, and re-add spent UTXOs...
                            unspentTransactionOutputManager.removeBlockFromUtxoSet(nextBlock, undoBlockHeight);
                        }

                        { // Add non-coinbase transactions to the mempool...
                            final MutableList<TransactionId> nextBlockTransactionIds = new MutableList<TransactionId>(transactionIds);
                            nextBlockTransactionIds.remove(0); // Exclude the coinbase...
                            transactionDatabaseManager.addToUnconfirmedTransactions(nextBlockTransactionIds);
                        }
                    }

                    // 2. Continue to traverse up the chain until the block is connected to the new headBlockchain...
                    nextBlockId = blockHeaderDatabaseManager.getAncestorBlockId(nextBlockId, 1);
                    if (nextBlockId == null) { break; }

                    final Boolean nextBlockIsConnectedToNewHeadBlockchain = blockHeaderDatabaseManager.isBlockConnectedToChain(nextBlockId, newHeadBlockchainSegmentId, BlockRelationship.ANCESTOR);
                    if (nextBlockIsConnectedToNewHeadBlockchain) { break; }

                    undoBlockHeight -= 1L;
                }
                Logger.trace("Utxo Reorg - 2/6 complete.");

                // 2.5 Skip the shared block between the two segments (not strictly necessary, but more performant)...
                nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);

                // 3. Traverse down the chain to the new head of the chain and remove the transactions from those blocks from the memory pool...
                while (nextBlockId != null) {
                    final MutableList<TransactionId> nextBlockTransactionIds = new MutableList<TransactionId>(blockDatabaseManager.getTransactionIds(nextBlockId));
                    nextBlockTransactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...
                    transactionDatabaseManager.removeFromUnconfirmedTransactions(nextBlockTransactionIds);

                    nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);
                }
                Logger.trace("Utxo Reorg - 3/6 complete.");

                // 4. Validate that the transactions are still valid on the new chain...
                final BlockOutputs reorgBlockOutputs;
                final UnspentTransactionOutputContext reorgUnspentTransactionOutputContext;
                {
                    final MutableUnspentTransactionOutputSet mutableUnspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                    mutableUnspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight);
                    reorgUnspentTransactionOutputContext = mutableUnspentTransactionOutputSet;
                    reorgBlockOutputs = BlockOutputs.fromBlock(block);
                }

                final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.calculateMedianBlockTime(blockId);
                final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(_networkTime, medianBlockTime, reorgUnspentTransactionOutputContext);
                final TransactionValidator transactionValidator = new TransactionValidatorCore(reorgBlockOutputs, transactionValidatorContext);
                transactionValidator.setLoggingEnabled(false);

                final List<TransactionId> transactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
                final MutableList<TransactionId> transactionsToRemove = new MutableList<TransactionId>();
                for (final TransactionId transactionId : transactionIds) {
                    final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
                    final Boolean transactionIsValid = transactionValidator.validateTransaction(blockHeight, transaction, true);
                    if (! transactionIsValid) {
                        transactionsToRemove.add(transactionId);
                    }
                }
                Logger.trace("Utxo Reorg - 4/6 complete.");

                // 5. Remove transactions in UnconfirmedTransactions that depend on the removed transactions...
                while (! transactionsToRemove.isEmpty()) {
                    transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
                    final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
                    transactionsToRemove.clear();
                    transactionsToRemove.addAll(chainedInvalidTransactions);
                }
                Logger.trace("Utxo Reorg - 5/6 complete.");

                // 6. Commit the UTXO set to ensure UTXOs removed by a now-undone commit are re-added...
                final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(_databaseManagerFactory.getDatabaseConnectionFactory());

                timer.stop();
                Logger.info("Unspent Transactions Reorganization: " + originalHeadBlockId + " -> " + blockId + " (" + timer.getMillisecondsElapsed() + "ms)");
            }
        }

        TransactionUtil.commitTransaction(databaseConnection);

        final float averageBlocksPerSecond;
        final float averageTransactionsPerSecond;
        synchronized (_statisticsMutex) {
            _blocksPerSecond.add(Math.round(blockValidationTimer.getMillisecondsElapsed() + storeBlockTimer.getMillisecondsElapsed()));
            _transactionsPerBlock.add(transactionCount);

            final int blockCount = _blocksPerSecond.size();
            final long validationTimeElapsed;
            {
                long value = 0L;
                for (final Long elapsed : _blocksPerSecond) {
                    value += elapsed;
                }
                validationTimeElapsed = value;
            }

            final int totalTransactionCount;
            {
                int value = 0;
                for (final Integer transactionCountPerBlock : _transactionsPerBlock) {
                    value += transactionCountPerBlock;
                }
                totalTransactionCount = value;
            }

            averageBlocksPerSecond = ( (((float) blockCount) / ((float) validationTimeElapsed)) * 1000F );
            averageTransactionsPerSecond = ( (((float) totalTransactionCount) / ((float) validationTimeElapsed)) * 1000F );
        }

        _averageBlocksPerSecond.value = averageBlocksPerSecond;
        _averageTransactionsPerSecond.value = averageTransactionsPerSecond;

        processBlockTimer.stop();
        Logger.info("Processed Block with " + transactionCount + " transactions in " + (String.format("%.2f", processBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / processBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());
        Logger.debug("Block Height: " + blockHeight);
        return blockHeight;
    }

    /**
     * Stores and validates the provided Block.
     * If the block fails to validates, the block and its transactions are not stored.
     * If provided, the UnspentTransactionOutputSet must include every output spent by the block.
     * If not provided, the UnspentTransactionOutputSet is loaded from the database at validation time.
     * Returns the block height of the block if validation was successful, otherwise returns null.
     */
    public Long processBlock(final Block block, final UnspentTransactionOutputContext preLoadedUnspentTransactionOutputContext) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final Long newBlockHeight = _processBlock(block, preLoadedUnspentTransactionOutputContext, databaseManager);
            final boolean blockWasValid = (newBlockHeight != null);
            if ((blockWasValid) && (_orphanedTransactionsCache != null)) {
                for (final Transaction transaction : block.getTransactions()) {
                    _orphanedTransactionsCache.onTransactionAdded(transaction);
                }
            }

            return newBlockHeight;
        }
        catch (final Exception exception) {
            Logger.info("ERROR VALIDATING BLOCK: " + block.getHash(), exception);

            if (! _synchronizationStatus.isShuttingDown()) {
                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                    final BlockchainSegmentId headBlockchainSegmentId;
                    {
                        final BlockId newHeadBlockId = blockDatabaseManager.getHeadBlockId();
                        headBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newHeadBlockId);
                    }

                    final DatabaseConnectionFactory databaseConnectionFactory = _databaseManagerFactory.getDatabaseConnectionFactory();
                    final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, databaseConnectionFactory, _utxoCommitFrequency);

                    final SimpleThreadPool threadPool = new SimpleThreadPool();
                    threadPool.start();

                    try {
                        final BlockLoader blockLoader = new BlockLoader(headBlockchainSegmentId, 128, _databaseManagerFactory, threadPool);
                        unspentTransactionOutputManager.buildUtxoSet(blockLoader);
                    }
                    finally {
                        threadPool.stop();
                    }
                }
                catch (final Exception rebuildUtxoSetException) {
                    Logger.debug("Error rebuilding UTXO set.", rebuildUtxoSetException);
                }
            }
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

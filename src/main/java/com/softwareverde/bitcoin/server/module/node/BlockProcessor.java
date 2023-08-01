//package com.softwareverde.bitcoin.server.module.node;
//
//import com.softwareverde.bitcoin.bip.UpgradeSchedule;
//import com.softwareverde.bitcoin.block.Block;
//import com.softwareverde.bitcoin.block.BlockId;
//import com.softwareverde.bitcoin.block.header.BlockHeader;
//import com.softwareverde.bitcoin.block.validator.BlockHeaderValidator;
//import com.softwareverde.bitcoin.block.validator.BlockValidationResult;
//import com.softwareverde.bitcoin.block.validator.BlockValidator;
//import com.softwareverde.bitcoin.block.validator.ValidationResult;
//import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
//import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
//import com.softwareverde.bitcoin.context.BlockStoreContext;
//import com.softwareverde.bitcoin.context.DifficultyCalculatorFactory;
//import com.softwareverde.bitcoin.context.MedianBlockTimeContext;
//import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
//import com.softwareverde.bitcoin.context.NetworkTimeContext;
//import com.softwareverde.bitcoin.context.SynchronizationStatusContext;
//import com.softwareverde.bitcoin.context.TransactionValidatorFactory;
//import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
//import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
//import com.softwareverde.bitcoin.context.core.BlockHeaderValidatorContext;
//import com.softwareverde.bitcoin.context.core.MedianBlockTimeContextCore;
//import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
//import com.softwareverde.bitcoin.context.core.TransactionValidatorContext;
//import com.softwareverde.bitcoin.context.lazy.HeadBlockchainMedianBlockTimeContext;
//import com.softwareverde.bitcoin.context.lazy.LazyBlockValidatorContext;
//import com.softwareverde.bitcoin.context.lazy.LazyUnconfirmedTransactionUtxoSet;
//import com.softwareverde.bitcoin.inflater.BlockInflaters;
//import com.softwareverde.bitcoin.inflater.TransactionInflaters;
//import com.softwareverde.bitcoin.server.database.DatabaseConnectionFactory;
//import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
//import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
//import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
//import com.softwareverde.bitcoin.server.module.node.database.indexer.BlockchainIndexerDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.transaction.TransactionDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UndoLogDatabaseManager;
//import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
//import com.softwareverde.bitcoin.transaction.Transaction;
//import com.softwareverde.bitcoin.transaction.TransactionId;
//import com.softwareverde.bitcoin.transaction.validator.BlockOutputs;
//import com.softwareverde.bitcoin.transaction.validator.TransactionValidationResult;
//import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
//import com.softwareverde.constable.list.List;
//import com.softwareverde.constable.list.mutable.MutableArrayList;
//import com.softwareverde.constable.list.mutable.MutableList;
//import com.softwareverde.constable.map.Map;
//import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
//import com.softwareverde.database.DatabaseException;
//import com.softwareverde.logging.Logger;
//import com.softwareverde.network.time.VolatileNetworkTime;
//import com.softwareverde.util.RotatingQueue;
//import com.softwareverde.util.timer.MilliTimer;
//import com.softwareverde.util.timer.MultiTimer;
//import com.softwareverde.util.timer.NanoTimer;
//
//public class BlockProcessor {
//    public interface Context extends BlockInflaters, TransactionInflaters, BlockStoreContext, MultiConnectionFullDatabaseContext, NetworkTimeContext, SynchronizationStatusContext, TransactionValidatorFactory, DifficultyCalculatorFactory, UpgradeScheduleContext { }
//
//    protected final Context _context;
//    protected final TransactionValidatorFactory _transactionValidatorFactory;
//    protected final DifficultyCalculatorFactory _difficultyCalculatorFactory;
//
//    protected final Object _statisticsMutex = new Object();
//    protected final RotatingQueue<Long> _blocksPerSecond = new RotatingQueue<>(100);
//    protected final RotatingQueue<Integer> _transactionsPerBlock = new RotatingQueue<>(100);
//
//    protected Boolean _undoLogIsEnabled = false;
//    protected Long _headBlockHeight = 0L;
//
//    protected Long _utxoCommitFrequency = 2016L;
//    protected Integer _maxThreadCount = 4;
//    protected Long _trustedBlockHeight = 0L;
//
//    protected final Long _startTime;
//    protected Float _averageTransactionsPerSecond = 0F;
//
//    public BlockProcessor(final Context context) {
//        _context = context;
//        _transactionValidatorFactory = context;
//        _difficultyCalculatorFactory = context;
//
//        _startTime = System.currentTimeMillis();
//    }
//
//    public void setMaxThreadCount(final Integer maxThreadCount) {
//        _maxThreadCount = maxThreadCount;
//    }
//
//    public void setUtxoCommitFrequency(final Long utxoCommitFrequency) {
//        _utxoCommitFrequency = utxoCommitFrequency;
//    }
//
//    public Long getUtxoCommitFrequency() {
//        return _utxoCommitFrequency;
//    }
//
//    public void setTrustedBlockHeight(final Long trustedBlockHeight) {
//        _trustedBlockHeight = trustedBlockHeight;
//    }
//
//    protected static class ProcessBlockHeaderResult {
//        protected final BlockId _blockId;
//        protected final Long _blockHeight;
//        protected final Boolean _blockPreviouslyExisted;
//
//        public ProcessBlockHeaderResult(final BlockId blockId, final Long blockHeight, final Boolean blockWasAlreadyProcessed) {
//            _blockId = blockId;
//            _blockHeight = blockHeight;
//            _blockPreviouslyExisted = blockWasAlreadyProcessed;
//        }
//
//        public BlockId getBlockId() {
//            return _blockId;
//        }
//
//        public Boolean wasBlockAlreadyProcessed() {
//            return _blockPreviouslyExisted;
//        }
//
//        public Long getBlockHeight() {
//            return _blockHeight;
//        }
//    }
//
//    protected ProcessBlockHeaderResult _processBlockHeader(final BlockHeader blockHeader, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
//        final VolatileNetworkTime networkTime = _context.getNetworkTime();
//
//        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//
//        final Sha256Hash blockHash = blockHeader.getHash();
//        final BlockId existingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
//
//        final boolean blockHeaderExists = (existingBlockId != null);
//        if (blockHeaderExists) {
//            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(existingBlockId);
//
//            final Boolean blockHasTransactions = blockDatabaseManager.hasTransactions(blockHash);
//            if (blockHasTransactions) {
//                Logger.debug("Skipping known block: " + blockHash);
//                return new ProcessBlockHeaderResult(existingBlockId, blockHeight, true);
//            }
//
//            return new ProcessBlockHeaderResult(existingBlockId, blockHeight, false);
//        }
//
//        // Store the BlockHeader...
//        final BlockId blockId;
//        final Long blockHeight;
//        databaseManager.startTransaction();
//        {
//            Logger.debug("Processing Block: " + blockHash);
//            blockId = blockHeaderDatabaseManager.storeBlockHeader(blockHeader);
//
//            if (blockId == null) {
//                Logger.debug("Error storing BlockHeader: " + blockHash);
//                databaseManager.rollbackTransaction();
//                return null;
//            }
//
//            blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
//
//            final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//            final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
//
//            final BlockHeaderValidatorContext blockHeaderValidatorContext = new BlockHeaderValidatorContext(blockchainSegmentId, databaseManager, networkTime, _difficultyCalculatorFactory, upgradeSchedule);
//
//            final BlockHeaderValidator blockHeaderValidator = new BlockHeaderValidator(blockHeaderValidatorContext);
//            final BlockHeaderValidator.BlockHeaderValidationResult blockHeaderValidationResult = blockHeaderValidator.validateBlockHeader(blockHeader, blockHeight);
//            if (! blockHeaderValidationResult.isValid) {
//                Logger.debug("Invalid BlockHeader: " + blockHeaderValidationResult.errorMessage + " (" + blockHash + ")");
//                databaseManager.rollbackTransaction();
//                return null;
//            }
//        }
//        databaseManager.commitTransaction();
//        return new ProcessBlockHeaderResult(blockId, blockHeight, false);
//    }
//
//    protected void _switchHeadBlock(final DatabaseManagerFactory databaseManagerFactory, final FullNodeDatabaseManager databaseManager, final Long blockHeight, final BlockId blockId, final Block block, final BlockId originalHeadBlockId, final BlockchainSegmentId newHeadBlockchainSegmentId, final VolatileNetworkTime networkTime) throws DatabaseException {
//        // final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
//        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
//        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
//
//        BlockId nextBlockId;
//        final MilliTimer timer = new MilliTimer();
//        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
//        try {
//            Logger.debug("Starting Unspent Transactions Reorganization: " + originalHeadBlockId + " -> " + blockId);
//            timer.start();
//            // Rebuild the memory pool to include (valid) transactions that were broadcast/mined on the old chain but were excluded from the new chain...
//            // 1. Take the block at the head of the old chain and add its transactions back into the pool... (Ignoring the coinbases...)
//            nextBlockId = originalHeadBlockId;
//            Logger.trace("Utxo Reorg - 1/6 complete.");
//
//            while (nextBlockId != null) {
//                // final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(nextBlockId);
//                final Long undoBlockHeight = blockHeaderDatabaseManager.getBlockHeight(nextBlockId);
//                final Block nextBlock = blockDatabaseManager.getBlock(nextBlockId);
//                final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(nextBlockId);
//
//                { // Remove UTXOs from the UTXO set, and re-add spent UTXOs...
//                    Logger.trace("Removing Block from UTXO Set: " + nextBlock.getHash() + " @ " + undoBlockHeight);
//
//                    unspentTransactionOutputDatabaseManager.undoBlock(nextBlock, undoBlockHeight);
//                    // final BlockUtxoDiff blockUtxoDiff = BlockUtil.getBlockUtxoDiff(nextBlock);
//                    // unspentTransactionOutputDatabaseManager.undoSpendingOfTransactionOutputs(blockUtxoDiff.spentTransactionOutputIdentifiers, blockchainSegmentId);
//                    // unspentTransactionOutputDatabaseManager.undoCreationOfTransactionOutputs(blockUtxoDiff.unspentTransactionOutputIdentifiers);
//                }
//
//                { // Add non-coinbase transactions to the mempool...
//                    final MutableList<TransactionId> nextBlockTransactionIds = new MutableArrayList<>(transactionIds);
//                    nextBlockTransactionIds.remove(0); // Exclude the coinbase...
//                    transactionDatabaseManager.addToUnconfirmedTransactions(nextBlockTransactionIds);
//                }
//
//                // 2. Continue to traverse up the chain until the block is connected to the new headBlockchain...
//                nextBlockId = blockHeaderDatabaseManager.getAncestorBlockId(nextBlockId, 1);
//                if (nextBlockId == null) { break; }
//
//                final Boolean nextBlockIsConnectedToNewHeadBlockchain = blockHeaderDatabaseManager.isBlockConnectedToChain(nextBlockId, newHeadBlockchainSegmentId, BlockRelationship.ANCESTOR);
//                if (nextBlockIsConnectedToNewHeadBlockchain) { break; }
//            }
//        }
//        finally {
//            TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
//        }
//        Logger.trace("Utxo Reorg - 2/6 complete.");
//
//        // 2.5 Skip the shared block between the two segments (not strictly necessary, but more performant)...
//        nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);
//
//        // 3. Traverse down the chain to the new head of the chain and remove the transactions from those blocks from the memory pool...
//        while (nextBlockId != null) {
//            if (! blockDatabaseManager.hasTransactions(nextBlockId)) { break; }
//
//            final Long nextBlockHeight = blockHeaderDatabaseManager.getBlockHeight(nextBlockId);
//            final Block nextBlock = blockDatabaseManager.getBlock(nextBlockId);
//            final List<TransactionId> transactionIds = blockDatabaseManager.getTransactionIds(nextBlockId);
//
//            { // Add UTXOs to the UTXO set, and remove spent UTXOs...
//                Logger.trace("Applying Block to UTXO Set: " + nextBlock.getHash() + " @ " + nextBlockHeight);
//                unspentTransactionOutputDatabaseManager.applyBlock(nextBlock, blockHeight);
//            }
//
//            { // Remove non-coinbase transactions from the mempool...
//                final MutableList<TransactionId> nextBlockTransactionIds = new MutableArrayList<>(transactionIds);
//                nextBlockTransactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...
//                transactionDatabaseManager.removeFromUnconfirmedTransactions(nextBlockTransactionIds);
//            }
//
//            nextBlockId = blockHeaderDatabaseManager.getChildBlockId(newHeadBlockchainSegmentId, nextBlockId);
//        }
//        Logger.trace("Utxo Reorg - 3/6 complete.");
//
//        // 4. Validate that the transactions are still valid on the new chain...
//        final BlockOutputs reorgBlockOutputs;
//        final UnspentTransactionOutputContext reorgUnspentTransactionOutputContext;
//        {
//            final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//            final MutableUnspentTransactionOutputSet mutableUnspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
//            mutableUnspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight, upgradeSchedule);
//            reorgUnspentTransactionOutputContext = mutableUnspentTransactionOutputSet;
//            reorgBlockOutputs = BlockOutputs.fromBlock(block);
//        }
//
//        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//        final TransactionInflaters transactionInflaters = _context;
//        final MedianBlockTimeContext medianBlockTimeContext = new MedianBlockTimeContextCore(newHeadBlockchainSegmentId, databaseManager);
//        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(transactionInflaters, networkTime, medianBlockTimeContext, reorgUnspentTransactionOutputContext, upgradeSchedule);
//        final TransactionValidator transactionValidator = _context.getTransactionValidator(reorgBlockOutputs, transactionValidatorContext);
//
//        final List<TransactionId> transactionIds = transactionDatabaseManager.getUnconfirmedTransactionIds();
//        final MutableList<TransactionId> transactionsToRemove = new MutableArrayList<>();
//        for (final TransactionId transactionId : transactionIds) {
//            final Transaction transaction = transactionDatabaseManager.getTransaction(transactionId);
//            final TransactionValidationResult transactionValidationResult = transactionValidator.validateTransaction((blockHeight + 1L), transaction);
//            if (! transactionValidationResult.isValid) {
//                transactionsToRemove.add(transactionId);
//            }
//        }
//        Logger.trace("Utxo Reorg - 4/6 complete.");
//
//        // 5. Remove transactions in UnconfirmedTransactions that depend on the removed transactions...
//        while (! transactionsToRemove.isEmpty()) {
//            transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
//            final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
//            transactionsToRemove.clear();
//            transactionsToRemove.addAll(chainedInvalidTransactions);
//        }
//        Logger.trace("Utxo Reorg - 5/6 complete.");
//
//        timer.stop();
//        Logger.info("Unspent Transactions Reorganization: " + originalHeadBlockId + " -> " + blockId + " (" + timer.getMillisecondsElapsed() + "ms)");
//    }
//
//    protected ProcessBlockResult _processBlock(final Block block, final UnspentTransactionOutputContext preLoadedUnspentTransactionOutputContext, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
//        final MultiTimer multiTimer = new MultiTimer();
//        multiTimer.start();
//
//        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
//        final VolatileNetworkTime networkTime = _context.getNetworkTime();
//
//        final NanoTimer processBlockTimer = new NanoTimer();
//        final NanoTimer storeBlockTimer = new NanoTimer();
//        final NanoTimer blockValidationTimer = new NanoTimer();
//        processBlockTimer.start();
//
//        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
//        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
//        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
//        final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
//        final BlockchainIndexerDatabaseManager blockchainIndexerDatabaseManager = databaseManager.getBlockchainIndexerDatabaseManager();
//        final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
//
//        // Keep a copy of the original head Block before the block is processed...
//        final BlockId originalHeadBlockId = blockDatabaseManager.getHeadBlockId(); // NOTE: Head Block is not the same as head BlockHeader.
//
//        final Sha256Hash blockHash = block.getHash();
//        final List<Transaction> blockTransactions = block.getTransactions();
//        final Integer transactionCount = blockTransactions.getCount();
//
//        final BlockId blockId;
//        final Long blockHeight;
//        { // Process the BlockHeader.
//            final ProcessBlockHeaderResult blockHeaderResult = _processBlockHeader(block, databaseManager);
//            if (blockHeaderResult == null ) { return ProcessBlockResult.invalid(block, null, "Unable to process block header."); }
//
//            // If the full Block has already been processed then abort processing it...
//            if (blockHeaderResult.wasBlockAlreadyProcessed()) {
//                blockHeight = blockHeaderResult.getBlockHeight();
//                return ProcessBlockResult.valid(block, blockHeight, false, true);
//            }
//
//            blockId = blockHeaderResult.getBlockId();
//            blockHeight = blockHeaderResult.getBlockHeight();
//        }
//        multiTimer.mark("metadata");
//
//        final Boolean blockIsConnectedToUtxoSet;
//        { // Determine if the Block should use the head Block UTXO set...
//            final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
//            final BlockchainSegmentId headBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(originalHeadBlockId);
//            if (headBlockchainSegmentId == null) {
//                blockIsConnectedToUtxoSet = true;
//            }
//            else {
//                blockIsConnectedToUtxoSet = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, headBlockchainSegmentId, BlockRelationship.ANY);
//            }
//        }
//        multiTimer.mark("blockchain");
//
//        databaseManager.startTransaction();
//
//        final UnspentTransactionOutputContext unspentTransactionOutputContext;
//        {
//            if ( blockIsConnectedToUtxoSet && (preLoadedUnspentTransactionOutputContext != null) ) {
//                unspentTransactionOutputContext = preLoadedUnspentTransactionOutputContext;
//                Logger.debug("Using preLoadedUnspentTransactionOutputs for blockHeight: " + blockHeight);
//            }
//            else {
//                final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//                final MutableUnspentTransactionOutputSet mutableUnspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
//                final Boolean unspentTransactionOutputsExistForBlock = mutableUnspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight, upgradeSchedule); // Ensure the the UTXOs for this block are pre-loaded into the cache...
//                if (! unspentTransactionOutputsExistForBlock) {
//                    databaseManager.rollbackTransaction();
//                    Logger.info("Invalid block. Could not find UTXOs for block: " + blockHash);
//                    return ProcessBlockResult.invalid(block, blockHeight, "Could not find UTXOs for block.");
//                }
//                unspentTransactionOutputContext = mutableUnspentTransactionOutputSet;
//                Logger.debug("Using liveLoadedUnspentTransactionOutputs for blockHeight: " + blockHeight + "; blockIsConnectedToUtxoSet=" + blockIsConnectedToUtxoSet);
//            }
//        }
//        multiTimer.mark("utxoContext");
//
//        final BlockValidationResult blockValidationResult;
//        {
//            final BlockValidator blockValidator;
//            {
//                final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//                final TransactionInflaters transactionInflaters = _context;
//                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
//
//                final LazyBlockValidatorContext blockValidatorContext = new LazyBlockValidatorContext(blockchainSegmentId, databaseManager, networkTime, _difficultyCalculatorFactory, upgradeSchedule, unspentTransactionOutputContext, _transactionValidatorFactory, transactionInflaters);
//                blockValidator = new BlockValidator(blockValidatorContext);
//            }
//
//            blockValidator.setMaxThreadCount(_maxThreadCount);
//            blockValidator.setTrustedBlockHeight(_trustedBlockHeight);
//            blockValidator.setShouldLogValidBlocks(true);
//
//            blockValidationTimer.start();
//            blockValidationResult = blockValidator.validateBlockTransactions(block, blockHeight); // NOTE: Only validates the transactions since the blockHeader is validated separately above...
//
//            if (! blockValidationResult.isValid) {
//                Logger.info(blockValidationResult.errorMessage);
//            }
//
//            blockValidationTimer.stop();
//        }
//        multiTimer.mark("validation");
//
//        if (! blockValidationResult.isValid) {
//            databaseManager.rollbackTransaction();
//            Logger.info("Invalid block. " + blockHash);
//            return ProcessBlockResult.invalid(block, blockHeight, blockValidationResult.errorMessage);
//        }
//
//        final DatabaseConnectionFactory databaseConnectionFactory = databaseManagerFactory.getDatabaseConnectionFactory();
//        if ( blockIsConnectedToUtxoSet && (blockHeight > 0L) ) { // Maintain the UTXO (Unspent Transaction Output) set (and exclude UTXOs from the genesis block)...
//            final NanoTimer nanoTimer = new NanoTimer();
//            nanoTimer.start();
//
//            unspentTransactionOutputDatabaseManager.applyBlock(block, blockHeight);
//
//            nanoTimer.stop();
//            Logger.debug("Applied " + blockHash + " @ " + blockHeight + " to UTXO set in " + nanoTimer.getMillisecondsElapsed() + "ms.");
//        }
//        multiTimer.mark("utxoUpdate");
//
//        final List<TransactionId> transactionIds;
//        { // Store the Block's Transactions...
//            storeBlockTimer.start();
//
//            transactionIds = blockDatabaseManager.storeBlockTransactions(block, databaseConnectionFactory, _maxThreadCount);
//            final boolean transactionsStoredSuccessfully = (transactionIds != null);
//
//            if (! transactionsStoredSuccessfully) {
//                databaseManager.rollbackTransaction();
//                Logger.info("Invalid block. Unable to store transactions for block: " + blockHash);
//                return ProcessBlockResult.invalid(block, blockHeight, "Unable to store transactions for block.");
//            }
//
//            storeBlockTimer.stop();
//            Logger.info("Stored " + transactionCount + " transactions in " + (String.format("%.2f", storeBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / storeBlockTimer.getMillisecondsElapsed()) * 1000D)) + " tps). " + blockHash);
//        }
//        multiTimer.mark("txStore");
//
//        { // Queue the transactions for processing...
//            blockchainIndexerDatabaseManager.queueTransactionsForProcessing(transactionIds);
//        }
//        multiTimer.mark("txIndexing");
//
//        final Integer byteCount = block.getByteCount();
//        blockHeaderDatabaseManager.setBlockByteCount(blockId, byteCount);
//        multiTimer.mark("blockSize");
//
//        final BlockchainSegmentId newHeadBlockchainSegmentId;
//        {
//            final BlockId newHeadBlockId = blockDatabaseManager.getHeadBlockId();
//            newHeadBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(newHeadBlockId);
//        }
//        multiTimer.mark("blockchainSegment");
//
//        final boolean bestBlockchainHasChanged;
//        {
//            if (blockIsConnectedToUtxoSet) {
//                bestBlockchainHasChanged = false;
//            }
//            else {
//                final BlockchainSegmentId blockchainSegmentIdOfOriginalHead = blockHeaderDatabaseManager.getBlockchainSegmentId(originalHeadBlockId);
//                bestBlockchainHasChanged = (! blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentIdOfOriginalHead, newHeadBlockchainSegmentId, BlockRelationship.ANY));
//            }
//        }
//        Logger.trace("bestBlockchainHasChanged=" + bestBlockchainHasChanged);
//        multiTimer.mark("blockchainChange");
//
//        { // Maintain utxo and mempool correctness...
//            if (bestBlockchainHasChanged) {
//                _switchHeadBlock(databaseManagerFactory, databaseManager, blockHeight, blockId, block, originalHeadBlockId, newHeadBlockchainSegmentId, networkTime);
//            }
//            else if (blockIsConnectedToUtxoSet) {
//                final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
//                final Long headBlockHeaderHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
//                final long blockCountBehindHeadBlockHeader = (headBlockHeaderHeight - blockHeight);
//                final boolean shouldMaintainMempool = (blockCountBehindHeadBlockHeader < UndoLogDatabaseManager.MAX_REORG_DEPTH);
//                if (shouldMaintainMempool) {
//                    // Update mempool transactions...
//                    final List<TransactionId> blockTransactionIds = blockDatabaseManager.getTransactionIds(blockId);
//                    final MutableList<TransactionId> mutableTransactionIds = new MutableArrayList<>(blockTransactionIds);
//                    mutableTransactionIds.remove(0); // Exclude the coinbase (not strictly necessary, but performs slightly better)...
//
//                    // Remove any transactions in the memory pool that were included in this block...
//                    transactionDatabaseManager.removeFromUnconfirmedTransactions(mutableTransactionIds);
//
//                    { // Remove any transactions in the memory pool that are now considered double-spends...
//                        final List<TransactionId> dependentUnconfirmedTransaction = transactionDatabaseManager.getUnconfirmedTransactionsDependingOnSpentInputsOf(blockTransactions);
//                        final MutableList<TransactionId> transactionsToRemove = new MutableArrayList<>(dependentUnconfirmedTransaction);
//                        while (! transactionsToRemove.isEmpty()) {
//                            transactionDatabaseManager.removeFromUnconfirmedTransactions(transactionsToRemove);
//                            final List<TransactionId> chainedInvalidTransactions = transactionDatabaseManager.getUnconfirmedTransactionsDependingOn(transactionsToRemove);
//                            transactionsToRemove.clear();
//                            transactionsToRemove.addAll(chainedInvalidTransactions);
//                        }
//                    }
//                }
//                else {
//                    // The node is still synchronizing or is substantially behind, so don't bother maintaining the mempool...
//                    transactionDatabaseManager.removeAllUnconfirmedTransactions();
//                }
//            }
//
//            if ( blockIsConnectedToUtxoSet && (blockHeight > 0L) ) {
//                final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
//                final Sha256Hash previousBlockHash = block.getPreviousBlockHash();
//                final BlockId previousBlockId = blockHeaderDatabaseManager.getBlockHeaderId(previousBlockHash);
//                final Long previousBlockHeight = blockHeaderDatabaseManager.getBlockHeight(previousBlockId);
//                final MedianBlockTime previousMedianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(previousBlockId);
//                final MedianBlockTime medianBlockTime = blockHeaderDatabaseManager.getMedianBlockTime(blockId);
//
//                final Boolean upgradeActivated = upgradeSchedule.didUpgradeActivate(previousBlockHeight, previousMedianBlockTime, blockHeight, medianBlockTime);
//                if (upgradeActivated) {
//                    TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.lock();
//                    try {
//                        // Revalidate all transactions within the Unconfirmed Transaction set...
//                        final NanoTimer nanoTimer = new NanoTimer();
//                        nanoTimer.start();
//                        int removedTransactionCount = 0;
//
//                        final UnspentTransactionOutputContext unconfirmedTransactionUtxoSet = new LazyUnconfirmedTransactionUtxoSet(databaseManager, upgradeSchedule, true);
//                        final MedianBlockTimeContext medianBlockTimeContext = new HeadBlockchainMedianBlockTimeContext(databaseManager);
//                        final TransactionValidatorContext transactionValidatorContext = new TransactionValidatorContext(_context, networkTime, medianBlockTimeContext, unconfirmedTransactionUtxoSet, upgradeSchedule);
//                        final TransactionValidator unconfirmedTransactionValidator = _transactionValidatorFactory.getUnconfirmedTransactionValidator(transactionValidatorContext);
//
//                        final List<Sha256Hash> unconfirmedTransactions = transactionDatabaseManager.getUnconfirmedTransactionsInHierarchicalOrder();
//                        final Map<Sha256Hash, Transaction> transactions = transactionDatabaseManager.getTransactions(unconfirmedTransactions);
//                        transactionDatabaseManager.removeAllUnconfirmedTransactions();
//
//                        for (final Sha256Hash transactionHash : unconfirmedTransactions) {
//                            final Transaction transaction = transactions.get(transactionHash);
//                            transactionDatabaseManager.storeTransactionHash(transaction);
//
//                            final ValidationResult validationResult = unconfirmedTransactionValidator.validateTransaction((blockHeight + 1L), transaction);
//                            if (!validationResult.isValid) {
//                                removedTransactionCount += 1;
//                                continue;
//                            }
//
//                            final TransactionId transactionId = transactionDatabaseManager.storeUnconfirmedTransaction(transaction);
//                            transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
//                        }
//
//                        nanoTimer.stop();
//                        Logger.debug("Removed " + removedTransactionCount + " newly invalidated transactions in " + nanoTimer.getMillisecondsElapsed() + "ms.");
//                    }
//                    finally {
//                        TransactionDatabaseManager.UNCONFIRMED_TRANSACTIONS_WRITE_LOCK.unlock();
//                    }
//                }
//            }
//        }
//        multiTimer.mark("mempool");
//
//        if (_undoLogIsEnabled) {
//            // Disable UndoLog if 288+ blocks behind head.
//            final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
//            final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
//            final long currentBlockHeightLag = (headBlockHeight - blockHeight);
//            final boolean skipUndoLog = (currentBlockHeightLag > UndoLogDatabaseManager.MAX_REORG_DEPTH);
//
//            if (! skipUndoLog) {
//                // NOTE: UndoLog generation creates records within the pruned_previous_transaction_outputs table within a DB transaction, which causes a DB lock
//                //  until the transaction is committed.  Applying a block to the UTXO can trigger a UTXO commit, which will require a scan of the same table,
//                //  causing a deadlock.  Therefore, while UndoLog creation is now a background task, that task will be blocked until this transaction is committed,
//                //  so it is best that the UndoLog is created after the UTXO update.
//                final NanoTimer undoLogTimer = new NanoTimer();
//                undoLogTimer.start();
//
//                final UndoLogDatabaseManager undoLogDatabaseManager = new UndoLogDatabaseManager(databaseManager);
//                undoLogDatabaseManager.createUndoLog(blockHeight, block, unspentTransactionOutputContext);
//
//                undoLogTimer.stop();
//                Logger.debug("Created UndoLog in " + (String.format("%.2f", undoLogTimer.getMillisecondsElapsed())) + "ms.");
//            }
//        }
//        multiTimer.mark("undoLog");
//
//        databaseManager.commitTransaction();
//
//        final float averageTransactionsPerSecond;
//        synchronized (_statisticsMutex) {
//            _blocksPerSecond.add(Math.round(blockValidationTimer.getMillisecondsElapsed() + storeBlockTimer.getMillisecondsElapsed()));
//            _transactionsPerBlock.add(transactionCount);
//
//            final long validationTimeElapsed;
//            {
//                long value = 0L;
//                for (final Long elapsed : _blocksPerSecond) {
//                    value += elapsed;
//                }
//                validationTimeElapsed = value;
//            }
//
//            final int totalTransactionCount;
//            {
//                int value = 0;
//                for (final Integer transactionCountPerBlock : _transactionsPerBlock) {
//                    value += transactionCountPerBlock;
//                }
//                totalTransactionCount = value;
//            }
//
//            averageTransactionsPerSecond = ( (((float) totalTransactionCount) / ((float) validationTimeElapsed)) * 1000F );
//        }
//
//        _averageTransactionsPerSecond = averageTransactionsPerSecond;
//
//        processBlockTimer.stop();
//        multiTimer.stop("commit");
//
//        if (Logger.isTraceEnabled()) {
//            Logger.trace("ProcessBlock MultiTimer: " + multiTimer);
//        }
//        Logger.debug("Block Height: " + blockHeight);
//        Logger.info("Processed Block with " + transactionCount + " transactions in " + (String.format("%.2f", processBlockTimer.getMillisecondsElapsed())) + "ms (" + String.format("%.2f", ((((double) transactionCount) / processBlockTimer.getMillisecondsElapsed()) * 1000)) + " tps). " + block.getHash());
//
//        final boolean blockIsOnMainChain = blockIsConnectedToUtxoSet;
//        if (blockIsOnMainChain) {
//            _headBlockHeight = Math.max(_headBlockHeight, blockHeight);
//        }
//
//        return ProcessBlockResult.valid(block, blockHeight, bestBlockchainHasChanged, false);
//    }
//
//    /**
//     * Stores and validates the provided Block.
//     * If the block fails to validates, the block and its transactions are not stored.
//     * If provided, the UnspentTransactionOutputSet must include every output spent by the block.
//     * If not provided, the UnspentTransactionOutputSet is loaded from the database at validation time.
//     */
//    public ProcessBlockResult processBlock(final Block block, final FullNodeDatabaseManager databaseManager) {
//        return this.processBlock(block, databaseManager, null);
//    }
//
//    public ProcessBlockResult processBlock(final Block block, final FullNodeDatabaseManager databaseManager, final UnspentTransactionOutputContext preLoadedUnspentTransactionOutputContext) {
//        try {
//            return _processBlock(block, preLoadedUnspentTransactionOutputContext, databaseManager);
//        }
//        catch (final Exception exception) {
//            final Sha256Hash blockHash = block.getHash();
//            Logger.info("Error validating Block: " + blockHash, exception);
//            return ProcessBlockResult.invalid(block, null, "Exception encountered validating block.");
//        }
//    }
//
//    public Float getAverageTransactionsPerSecond() {
//        return _averageTransactionsPerSecond;
//    }
//
//    public Boolean isUndoLogEnabled() {
//        return _undoLogIsEnabled;
//    }
//
//    public void enableUndoLog(final Boolean undoLogIsEnabled) {
//        _undoLogIsEnabled = undoLogIsEnabled;
//    }
//
//    public Long getCurrentBlockHeight() {
//        return _headBlockHeight;
//    }
//}

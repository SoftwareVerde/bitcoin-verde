package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.ProcessBlockResult;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.CommitAsyncMode;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.BlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PreloadedPendingBlock;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.GracefulSleepyService;
import com.softwareverde.concurrent.threadpool.SimpleThreadPool;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

public class BlockchainBuilder extends GracefulSleepyService {
    public interface Context extends MultiConnectionFullDatabaseContext, ThreadPoolContext, BlockInflaters, NodeManagerContext { }

    public interface NewBlockProcessedCallback {
        void onNewBlock(ProcessBlockResult processBlockResult);
    }

    protected final Context _context;
    protected final BlockProcessor _blockProcessor;
    protected final BlockDownloader.StatusMonitor _blockDownloaderStatusMonitor;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected final PendingBlockLoader _pendingBlockLoader;
    protected Boolean _hasGenesisBlock;
    protected NewBlockProcessedCallback _asynchronousNewBlockProcessedCallback = null;
    protected NewBlockProcessedCallback _synchronousNewBlockProcessedCallback = null;

    protected final CircleBuffer<Long> _blockProcessingTimes = new CircleBuffer<Long>(100);
    protected final Container<Float> _averageBlocksPerSecond = new Container<Float>(0F);

    protected final Object _pendingBlockIdDeleteQueueMutex = new Object();
    protected MutableList<PendingBlockId> _pendingBlockIdDeleteQueue = new MutableList<>(1024);

    protected final Thread _deletePendingBlockThread = new Thread(new Runnable() {
        @Override
        public void run() {
            final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

            final Thread thread = Thread.currentThread();
            while (! thread.isInterrupted()) {
                try {
                    final List<PendingBlockId> pendingBlockIds;
                    synchronized (_pendingBlockIdDeleteQueueMutex) {
                        try {
                            _pendingBlockIdDeleteQueueMutex.wait();
                        }
                        catch (final InterruptedException exception) {
                            Logger.debug("Interrupt received, finishing iteration before exiting.");
                            thread.interrupt(); // Maintain the interrupted state in order to exit the loop after this round...
                        }

                        pendingBlockIds = _pendingBlockIdDeleteQueue;
                        _pendingBlockIdDeleteQueue = new MutableList<>(1024);
                    }

                    if (! pendingBlockIds.isEmpty()) {
                        final NanoTimer nanoTimer = new NanoTimer();
                        nanoTimer.start();

                        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
                            TransactionUtil.startTransaction(databaseConnection);
                            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
                            pendingBlockDatabaseManager.deletePendingBlocks(pendingBlockIds);
                            TransactionUtil.commitTransaction(databaseConnection);
                        }
                        catch (final Exception exception) {
                            Logger.debug(exception);
                            _pendingBlockIdDeleteQueue.addAll(pendingBlockIds);
                        }

                        nanoTimer.stop();
                        Logger.debug("Deleted " + pendingBlockIds.getCount() + " pending blocks in " + nanoTimer.getMillisecondsElapsed() + "ms.");
                    }
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
            }
        }
    });

    protected void _deletePendingBlock(final PendingBlockId pendingBlockId) {
        synchronized (_pendingBlockIdDeleteQueueMutex) {
            _pendingBlockIdDeleteQueue.add(pendingBlockId);

            if (_pendingBlockIdDeleteQueue.getCount() >= 512) {
                _pendingBlockIdDeleteQueueMutex.notifyAll();
            }
        }
    }

    /**
     * Stores and validates the pending Block.
     *  If not provided, the transactionOutputSet is loaded from the database.
     *  Returns true if the pending block was valid and stored.
     */
    protected Boolean _processPendingBlock(final PendingBlock pendingBlock) { return _processPendingBlock(pendingBlock, null); }
    protected Boolean _processPendingBlock(final PendingBlock pendingBlock, final UnspentTransactionOutputContext transactionOutputSet) {
        if (pendingBlock == null) {
            // NOTE: Can happen due to race condition...
            Logger.trace("Unable to process pending block, pendingBlock was null.");
            return false;
        }

        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();
        final BlockInflater blockInflater = _context.getBlockInflater();
        final Block block = pendingBlock.inflateBlock(blockInflater);
        nanoTimer.stop();
        Logger.trace("Block inflated in " + nanoTimer.getMillisecondsElapsed() + "ms.");

        if (block == null) {
            Logger.debug("Pending Block Corrupted: " + pendingBlock.getBlockHash() + " " + pendingBlock.getData());
            return false;
        }

        final ProcessBlockResult processBlockResult;
        { // Maximize the Thread priority and process the block...
            final Thread currentThread = Thread.currentThread();
            final int originalThreadPriority = currentThread.getPriority();
            try {
                currentThread.setPriority(Thread.MAX_PRIORITY);
                processBlockResult = _blockProcessor.processBlock(block, transactionOutputSet);
            }
            finally {
                currentThread.setPriority(originalThreadPriority);
            }
        }

        // Execute callbacks...
        if ( processBlockResult.isValid && (! processBlockResult.wasAlreadyProcessed) ) {
            final NewBlockProcessedCallback synchronousNewBlockProcessedCallback = _synchronousNewBlockProcessedCallback;
            if (synchronousNewBlockProcessedCallback != null) {
                synchronousNewBlockProcessedCallback.onNewBlock(processBlockResult);
            }

            final NewBlockProcessedCallback asynchronousNewBlockProcessedCallback = _asynchronousNewBlockProcessedCallback;
            if (asynchronousNewBlockProcessedCallback != null) {
                final ThreadPool threadPool = _context.getThreadPool();
                threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        asynchronousNewBlockProcessedCallback.onNewBlock(processBlockResult);
                    }
                });
            }
        }

        if (processBlockResult.bestBlockchainHasChanged) {
            _pendingBlockLoader.invalidateUtxoSets();
        }

        return processBlockResult.isValid;
    }

    protected Boolean _processGenesisBlock(final PendingBlockId pendingBlockId, final FullNodeDatabaseManager databaseManager, final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);

        final BlockInflater blockInflater = _context.getBlockInflater();
        final Block block = pendingBlock.inflateBlock(blockInflater);
        if (block == null) { return false; }

        final BlockId blockId;
        final BlockHasher blockHasher = new BlockHasher();
        final Sha256Hash blockHash = blockHasher.calculateBlockHash(block);
        if (Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, blockHash)) {
            synchronized (BlockHeaderDatabaseManager.MUTEX) {
                blockId = blockDatabaseManager.storeBlock(block);
            }
        }
        else {
            blockId = null;
        }

        TransactionUtil.startTransaction(databaseConnection);
        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
        TransactionUtil.commitTransaction(databaseConnection);

        return (blockId != null);
    }

    protected void _updateAverageBlockProcessingTime() {
        Long totalTimeInMilliseconds = 0L;
        int blockCount = 0;
        for (final Long averageBlockProcessingTime : _blockProcessingTimes) {
            totalTimeInMilliseconds += averageBlockProcessingTime;
            blockCount += 1;
        }

        if (blockCount == 0) {
            _averageBlocksPerSecond.value = 0F;
            return;
        }

        _averageBlocksPerSecond.value = (blockCount / (totalTimeInMilliseconds / 1000F));
    }

    protected void _assembleBlockchain(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

        pendingBlockDatabaseManager.purgeFailedPendingBlocks(BlockDownloader.MAX_DOWNLOAD_FAILURE_COUNT);

        { // Special case for storing the Genesis block...
            if (! _hasGenesisBlock) {
                final PendingBlockId genesisPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(BlockHeader.GENESIS_BLOCK_HASH);
                final Boolean hasBlockDataAvailable = pendingBlockDatabaseManager.hasBlockData(genesisPendingBlockId);

                final Boolean genesisBlockWasLoaded;
                if (hasBlockDataAvailable) {
                    genesisBlockWasLoaded = _processGenesisBlock(genesisPendingBlockId, databaseManager, pendingBlockDatabaseManager);
                }
                else {
                    genesisBlockWasLoaded = false;
                }

                if (genesisBlockWasLoaded) {
                    _hasGenesisBlock = true;
                }
                else {
                    _blockDownloadRequester.requestBlock(BlockHeader.GENESIS_BLOCK_HASH, null);
                }
            }
        }

        while (! _shouldAbort()) {
            final List<BlockchainSegmentId> blockchainSegmentIds = pendingBlockDatabaseManager.getLeafBlockchainSegmentsByChainWork();
            if (blockchainSegmentIds.isEmpty()) {
                // no blockchain segments to sync
                return;
            }

            int completedBlockchainSegmentCount = 0;
            for (final BlockchainSegmentId blockchainSegmentIdToSync : blockchainSegmentIds) {
                final Boolean shouldContinueToNextSegment = _assembleBlockchainSegment(databaseManager, blockchainSegmentIdToSync);
                if (! shouldContinueToNextSegment) { return; }

                completedBlockchainSegmentCount += 1;
            }

            // All available work is completed.
            if (completedBlockchainSegmentCount >= blockchainSegmentIds.getCount()) { break; }
        }
    }

    /**
     * Returns true to indicate that work on this blockchain segment is complete.  This may be due to reaching the head
     * or finding an invalid block.
     */
    protected Boolean _assembleBlockchainSegment(final FullNodeDatabaseManager databaseManager, final BlockchainSegmentId blockchainSegmentId) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
        final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

        Logger.trace("Assembling blocks leading to blockchain segment " + blockchainSegmentId);

        final MilliTimer milliTimer = new MilliTimer();
        milliTimer.start();

        // Find the next head block be downloaded... (depends on BlockHeaders)
        //  Traverse from the head blockHeader to the first processed block, then select its child blockHeader along the head blockchainSegment path.
        //  Since the head block and head blockHeader may have diverged, traversing backwards along the head blockHeader blockchainSegments is necessary.
        BlockId headBlockId = null;
        BlockchainSegmentId currentBlockchainSegmentId = blockchainSegmentId;
        while (headBlockId == null) {
            final BlockId firstBlockIdOfHeadBlockchainSegment = blockchainDatabaseManager.getFirstBlockIdOfBlockchainSegment(currentBlockchainSegmentId);
            final Boolean firstBlockOfHeadBlockchainHasTransactions = blockDatabaseManager.hasTransactions(firstBlockIdOfHeadBlockchainSegment);
            if (! firstBlockOfHeadBlockchainHasTransactions) {
                currentBlockchainSegmentId = blockchainDatabaseManager.getPreviousBlockchainSegmentId(currentBlockchainSegmentId);
                if (currentBlockchainSegmentId == null) { break; }
                continue;
            }

            final BlockId lastBlockWithTransactionsOfBlockchainSegment = blockDatabaseManager.getHeadBlockIdWithinBlockchainSegment(currentBlockchainSegmentId);
            headBlockId = lastBlockWithTransactionsOfBlockchainSegment;
        }
        if (headBlockId == null) { return false; }

        Sha256Hash lastSuccessfullyProcessedBlockHash = null;
        while (! _shouldAbort()) {
            final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, headBlockId);
            if (nextBlockId == null) {
                // If the nextBlockId is null then the end of the chain has been reached or the pending block's header hasn't been processed.
                // Check if there is a pending block with the head block hash before quitting.
                final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getBlockHash(headBlockId);
                final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(headBlockHash);
                if (pendingBlockIds.isEmpty()) { // The the end of the chain was reached.

                    { // Commit the UTXO set once the blockchain has synchronized, if a commit isn't already in progress.
                        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                        final Boolean wasSyncingHeadBlockchainSegment = (Util.areEqual(headBlockchainSegmentId, blockchainSegmentId));
                        Logger.trace("headBlockHeaderHash=" + headBlockHash + ", lastSuccessfullyProcessedBlockHash=" + lastSuccessfullyProcessedBlockHash + ", wasSyncingHeadBlockchainSegment=" + wasSyncingHeadBlockchainSegment);
                        if ( wasSyncingHeadBlockchainSegment && Util.areEqual(headBlockHash, lastSuccessfullyProcessedBlockHash) ) {
                            final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
                            final UnspentTransactionOutputDatabaseManager unspentTransactionOutputDatabaseManager = databaseManager.getUnspentTransactionOutputDatabaseManager();
                            final Boolean didExecute = unspentTransactionOutputDatabaseManager.commitUnspentTransactionOutputs(databaseManagerFactory, CommitAsyncMode.SKIP_IF_BUSY);
                            if (didExecute) {
                                Logger.info("Committing UTXO set.");
                            }
                        }
                    }

                    return true;
                }

                // Store the pending block(s) header(s) and try again.
                for (final PendingBlockId pendingBlockId : pendingBlockIds) {
                    synchronized (_pendingBlockIdDeleteQueueMutex) {
                        final boolean pendingBlockIsMarkedForDeletion = _pendingBlockIdDeleteQueue.contains(pendingBlockId);
                        if (pendingBlockIsMarkedForDeletion) { continue; }
                    }

                    final Sha256Hash pendingBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                    Logger.info("Pending Block still available without header: " + pendingBlockHash);
                }
                return true;
            }
            final Sha256Hash nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
            final Boolean isInvalid = blockHeaderDatabaseManager.isBlockInvalid(nextBlockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
            Logger.debug("NextBlockHash: " + nextBlockHash);
            if (isInvalid) { // Do not request blocks that have failed to process multiple times...
                return true;
            }

            final Sha256Hash pendingBlockHash = nextBlockHash;
            final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(nextBlockHash);

            synchronized (_pendingBlockIdDeleteQueueMutex) {
                if (_pendingBlockIdDeleteQueue.contains(pendingBlockId)) {
                    return true;
                }
            }

            if ( (pendingBlockId == null) || (! pendingBlockDatabaseManager.hasBlockData(pendingBlockId)) ) {
                Logger.debug("Requesting Block: " + nextBlockHash);
                final Sha256Hash headBlockHash = blockHeaderDatabaseManager.getBlockHash(headBlockId);
                _blockDownloadRequester.requestBlock(nextBlockHash, headBlockHash);
                return false;
            }

            final PendingBlock pendingBlock;
            final UnspentTransactionOutputContext unspentTransactionOutputContext;
            {
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();
                final PreloadedPendingBlock preloadedPendingBlock = _pendingBlockLoader.getBlock(pendingBlockHash, pendingBlockId);
                if (preloadedPendingBlock == null) {
                    pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
                    unspentTransactionOutputContext = null;

                    Logger.debug("Pending block failed to load: " + pendingBlockHash);
                }
                else {
                    pendingBlock = preloadedPendingBlock.getPendingBlock();
                    unspentTransactionOutputContext = preloadedPendingBlock.getUnspentTransactionOutputSet();
                }
                nanoTimer.stop();
                Logger.trace("Pending block " + pendingBlockHash + " loaded in " + nanoTimer.getMillisecondsElapsed() + "ms.");
            }

            if (! UnspentTransactionOutputManager.isUtxoCacheReady()) { // Ensure UTXO Set is valid (may happen if db connection was unavailable during UTXO rebuild).
                Logger.info("Rebuilding invalidated UTXO set before block processing.");

                final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
                final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

                final Long utxoCommitFrequency = _blockProcessor.getUtxoCommitFrequency();
                final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, utxoCommitFrequency);

                final SimpleThreadPool threadPool = new SimpleThreadPool();
                threadPool.start();

                try {
                    final BlockLoader blockLoader = new BlockLoader(headBlockchainSegmentId, 8, databaseManagerFactory, threadPool);
                    unspentTransactionOutputManager.buildUtxoSet(blockLoader, databaseManagerFactory);
                }
                finally {
                    threadPool.stop();
                }
            }

            final Boolean processBlockWasSuccessful = _processPendingBlock(pendingBlock, unspentTransactionOutputContext); // pendingBlock may be null; _processPendingBlock allows for this.

            { // Queue the pending block for deletion...
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                _deletePendingBlock(pendingBlockId);

                nanoTimer.stop();
                Logger.trace("Pending block queued for deletion in " + nanoTimer.getMillisecondsElapsed() + "ms.");
            }

            if (! processBlockWasSuccessful) {
                blockHeaderDatabaseManager.markBlockAsInvalid(pendingBlockHash, 1);
                Logger.debug("Pending block failed during processing: " + pendingBlockHash);
                return false;
            }

            lastSuccessfullyProcessedBlockHash = pendingBlockHash;
            headBlockId = nextBlockId;

            milliTimer.stop();
            _blockProcessingTimes.push(milliTimer.getMillisecondsElapsed());
            milliTimer.start();

            _updateAverageBlockProcessingTime();
        }

        return false;
    }

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        if (_shouldAbort()) { return false; }

        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            _assembleBlockchain(databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() {
        // Complete any queued deletions upon service sleep...
        synchronized (_pendingBlockIdDeleteQueueMutex) {
            if (! _pendingBlockIdDeleteQueue.isEmpty()) {
                _pendingBlockIdDeleteQueueMutex.notifyAll();
            }
        }

        if (_shouldAbort()) { return; }

        final Status blockDownloaderStatus = _blockDownloaderStatusMonitor.getStatus();
        if (blockDownloaderStatus != Status.ACTIVE) {
            final BitcoinNodeManager bitcoinNodeManager = _context.getBitcoinNodeManager();
            final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
            try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
                final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                for (final BitcoinNode bitcoinNode : bitcoinNodeManager.getNodes()) {
                    bitcoinNode.transmitBlockFinder(blockFinderHashes);
                }
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }
        }
    }

    public BlockchainBuilder(final Context context, final BlockProcessor blockProcessor, final PendingBlockLoader pendingBlockLoader, final BlockDownloader.StatusMonitor downloadStatusMonitor, final BlockDownloadRequester blockDownloadRequester) {
        _context = context;
        _blockProcessor = blockProcessor;
        _pendingBlockLoader = pendingBlockLoader;
        _blockDownloaderStatusMonitor = downloadStatusMonitor;
        _blockDownloadRequester = blockDownloadRequester;

        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            _hasGenesisBlock = blockDatabaseManager.hasTransactions(BlockHeader.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            _hasGenesisBlock = false;
        }

        _deletePendingBlockThread.start();
    }

    /**
     * Sets a callback to be executed at some point after a new valid Block has been processed.
     *  This callback, if set, is scheduled for execution via the ThreadPool.
     */
    public void setAsynchronousNewBlockProcessedCallback(final NewBlockProcessedCallback newBlockProcessedCallback) {
        _asynchronousNewBlockProcessedCallback = newBlockProcessedCallback;
    }

    /**
     * Sets a callback to be executed immediately after a new valid Block has been processed.
     */
    public void setSynchronousNewBlockProcessedCallback(final NewBlockProcessedCallback newBlockProcessedCallback) {
        _synchronousNewBlockProcessedCallback = newBlockProcessedCallback;
    }

    public Container<Float> getAverageBlocksPerSecondContainer() {
        return _averageBlocksPerSecond;
    }

    @Override
    public void stop() {
        super.stop();

        _deletePendingBlockThread.interrupt(); // Finishes any queued items before exiting...
        try {
            _deletePendingBlockThread.join(_stopTimeoutMs);
        }
        catch (final Exception exception) { }
    }
}

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
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PendingBlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PreloadedPendingBlock;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class BlockchainBuilder extends SleepyService {
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

    /**
     * Stores and validates the pending Block.
     *  If not provided, the transactionOutputSet is loaded from the database.
     *  Returns true if the pending block was valid and stored.
     */
    protected Boolean _processPendingBlock(final PendingBlock pendingBlock) { return _processPendingBlock(pendingBlock, null); }
    protected Boolean _processPendingBlock(final PendingBlock pendingBlock, final UnspentTransactionOutputContext transactionOutputSet) {
        if (pendingBlock == null) { return false; } // NOTE: Can happen due to race condition...

        final BlockInflater blockInflater = _context.getBlockInflater();
        final Block block = pendingBlock.inflateBlock(blockInflater);

        if (block == null) {
            Logger.warn("Pending Block Corrupted: " + pendingBlock.getBlockHash() + " " + pendingBlock.getData());
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

        if (processBlockResult.isValid) {
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

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
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
                        return false;
                    }
                }
            }

            while (! thread.isInterrupted()) {
                final MilliTimer milliTimer = new MilliTimer();
                milliTimer.start();

                // Select the first pending block to process; if none are found, request one to be downloaded...
                final PendingBlockId candidatePendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                if (candidatePendingBlockId == null) {
                    // Request the next head block be downloaded... (depends on BlockHeaders)
                    final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                    final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, headBlockId);
                    if (nextBlockId != null) {
                        final Sha256Hash previousBlockHash = blockHeaderDatabaseManager.getBlockHash(headBlockId);
                        final Sha256Hash nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
                        final Boolean isInvalid = blockHeaderDatabaseManager.isBlockInvalid(nextBlockHash);
                        if (! isInvalid) { // Do not request blocks that have failed to process multiple times...
                            Logger.debug("Requesting Block: " + nextBlockHash);
                            _blockDownloadRequester.requestBlock(nextBlockHash, previousBlockHash);
                        }
                    }
                    break;
                }

                // Process the first available candidate block...
                final PendingBlock candidatePendingBlock = pendingBlockDatabaseManager.getPendingBlock(candidatePendingBlockId);
                final Boolean processCandidateBlockWasSuccessful = _processPendingBlock(candidatePendingBlock);
                if (! processCandidateBlockWasSuccessful) {
                    TransactionUtil.startTransaction(databaseConnection);
                    pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                    TransactionUtil.commitTransaction(databaseConnection);
                    Logger.debug("Deleted failed pending block.");

                    blockHeaderDatabaseManager.markBlockAsInvalid(candidatePendingBlock.getBlockHash());

                    continue;
                }

                TransactionUtil.startTransaction(databaseConnection);
                pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                TransactionUtil.commitTransaction(databaseConnection);

                milliTimer.stop();
                _blockProcessingTimes.push(milliTimer.getMillisecondsElapsed());
                milliTimer.start();
                _updateAverageBlockProcessingTime();

                // Process the any viable descendant blocks of the candidate block...
                PendingBlock previousPendingBlock = candidatePendingBlock;
                while (! thread.isInterrupted()) {
                    final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousPendingBlock.getBlockHash());
                    if (pendingBlockIds.isEmpty()) { break; }

                    for (int i = 0; i < pendingBlockIds.getCount(); ++i) {
                        final PendingBlockId pendingBlockId = pendingBlockIds.get(i);
                        final Sha256Hash pendingBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);

                        final PreloadedPendingBlock preloadedPendingBlock = _pendingBlockLoader.getBlock(pendingBlockHash, pendingBlockId);
                        final PendingBlock pendingBlock = preloadedPendingBlock.getPendingBlock();
                        final UnspentTransactionOutputContext unspentTransactionOutputContext = preloadedPendingBlock.getUnspentTransactionOutputSet();

                        final Boolean processBlockWasSuccessful = _processPendingBlock(pendingBlock, unspentTransactionOutputContext); // pendingBlock may be null; _processPendingBlock allows for this.

                        TransactionUtil.startTransaction(databaseConnection);
                        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
                        TransactionUtil.commitTransaction(databaseConnection);

                        if (! processBlockWasSuccessful) {
                            blockHeaderDatabaseManager.markBlockAsInvalid(pendingBlockHash);
                            Logger.debug("Pending block failed during processing: " + pendingBlockHash);
                            break;
                        }

                        previousPendingBlock = pendingBlock;

                        milliTimer.stop();
                        _blockProcessingTimes.push(milliTimer.getMillisecondsElapsed());
                        milliTimer.start();
                    }

                    _updateAverageBlockProcessingTime();
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() {
        final Status blockDownloaderStatus = _blockDownloaderStatusMonitor.getStatus();
        if (blockDownloaderStatus != Status.ACTIVE) {
            final BitcoinNodeManager bitcoinNodeManager = _context.getNodeManager();
            final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
            try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
                final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
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
            Logger.warn(exception);
            _hasGenesisBlock = false;
        }
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
}

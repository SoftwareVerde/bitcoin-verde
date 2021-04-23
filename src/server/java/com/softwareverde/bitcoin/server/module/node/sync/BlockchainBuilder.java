package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.ProcessBlockResult;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.BlockDownloader;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.GracefulSleepyService;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Container;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

public class BlockchainBuilder extends GracefulSleepyService {
    public interface Context extends MultiConnectionFullDatabaseContext, ThreadPoolContext, BlockInflaters, NodeManagerContext, SystemTimeContext { }

    public interface NewBlockProcessedCallback {
        void onNewBlock(ProcessBlockResult processBlockResult);
    }

    protected final Context _context;
    protected final BlockProcessor _blockProcessor;
    protected final BlockDownloader.StatusMonitor _blockDownloaderStatusMonitor;
    protected final PendingBlockStore _blockStore;
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
    protected Boolean _processPendingBlock(final Block block) {
        final ProcessBlockResult processBlockResult;
        { // Maximize the Thread priority and process the block...
            final Thread currentThread = Thread.currentThread();
            final int originalThreadPriority = currentThread.getPriority();
            try {
                currentThread.setPriority(Thread.MAX_PRIORITY);
                processBlockResult = _blockProcessor.processBlock(block);
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

        return processBlockResult.isValid;
    }

    protected Boolean _processGenesisBlock(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final ByteArray pendingBlockData = _blockStore.getPendingBlockData(BlockHeader.GENESIS_BLOCK_HASH);

        final BlockInflater blockInflater = _context.getBlockInflater();
        final Block block = blockInflater.fromBytes(pendingBlockData);
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

        _blockStore.removePendingBlock(BlockHeader.GENESIS_BLOCK_HASH);

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

    protected List<BlockchainSegmentId> _getLeafBlockchainSegmentsByChainWork(final DatabaseManager databaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT blockchain_segments.id FROM blockchain_segments INNER JOIN (SELECT blockchain_segment_id, MAX(chain_work) AS chain_work FROM blocks GROUP BY blockchain_segment_id) AS segment_head_block WHERE nested_set_right = nested_set_left + 1 AND segment_head_block.blockchain_segment_id = blockchain_segments.id ORDER BY segment_head_block.chain_work DESC")
        );

        final MutableList<BlockchainSegmentId> orderedSegments = new MutableList<>();
        for (final Row row : rows) {
            final BlockchainSegmentId blockchainSegmentId = BlockchainSegmentId.wrap(row.getLong("id"));
            orderedSegments.add(blockchainSegmentId);
        }
        return orderedSegments;
    }

    protected void _assembleBlockchain(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        { // Special case for storing the Genesis block...
            if (! _hasGenesisBlock) {
                final Boolean hasBlockDataAvailable = _blockStore.pendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH);

                final Boolean genesisBlockWasLoaded;
                if (hasBlockDataAvailable) {
                    genesisBlockWasLoaded = _processGenesisBlock(databaseManager);
                }
                else {
                    genesisBlockWasLoaded = false;
                }

                if (genesisBlockWasLoaded) {
                    _hasGenesisBlock = true;
                }
            }
        }

        while (! _shouldAbort()) {
            final List<BlockchainSegmentId> blockchainSegmentIds = _getLeafBlockchainSegmentsByChainWork(databaseManager);
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

        while (! _shouldAbort()) {
            final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, headBlockId);
            if (nextBlockId == null) { return true; }

            final Sha256Hash nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
            Logger.debug("NextBlockHash: " + nextBlockHash);

            final Boolean isInvalid = blockHeaderDatabaseManager.isBlockInvalid(nextBlockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
            if (isInvalid) { // Do not request blocks that have failed to process multiple times...
                Logger.info("Skipping invalid Block: " + nextBlockHash);
                return true;
            }

            final Sha256Hash pendingBlockHash = nextBlockHash;
            final Boolean blockDataExists = _blockStore.pendingBlockExists(pendingBlockHash);
            final ByteArray pendingBlockData = (blockDataExists ? _blockStore.getPendingBlockData(pendingBlockHash) : null);

            if (pendingBlockData == null) {
                Logger.debug("Waiting for unavailable block: " + pendingBlockHash);
                return false;
            }

            final BlockInflater blockInflater = _context.getBlockInflater();
            final Block block = blockInflater.fromBytes(pendingBlockData);
            if (block == null) {
                Logger.info("Unable to inflate block: " + pendingBlockHash);
                blockHeaderDatabaseManager.markBlockAsInvalid(pendingBlockHash, 1);
                return false;
            }

            final Boolean processBlockWasSuccessful = _processPendingBlock(block);

            { // Delete the pending block...
                final NanoTimer nanoTimer = new NanoTimer();
                nanoTimer.start();

                _blockStore.removePendingBlock(pendingBlockHash);

                nanoTimer.stop();
                Logger.trace("Pending block deleted in " + nanoTimer.getMillisecondsElapsed() + "ms.");
            }

            if (! processBlockWasSuccessful) {
                blockHeaderDatabaseManager.markBlockAsInvalid(pendingBlockHash, 1);
                Logger.debug("Pending block failed during processing: " + pendingBlockHash);
                return false;
            }

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

    public BlockchainBuilder(final Context context, final BlockProcessor blockProcessor, final PendingBlockStore blockStore, final BlockDownloader.StatusMonitor downloadStatusMonitor) {
        _context = context;
        _blockProcessor = blockProcessor;
        _blockStore = blockStore;
        _blockDownloaderStatusMonitor = downloadStatusMonitor;

        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            _hasGenesisBlock = blockDatabaseManager.hasTransactions(BlockHeader.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
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

    @Override
    public void stop() {
        super.stop();
    }
}

package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.bip.UpgradeSchedule;
import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.context.UnspentTransactionOutputContext;
import com.softwareverde.bitcoin.context.UpgradeScheduleContext;
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
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager;
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
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.timer.NanoTimer;

public class BlockchainBuilder extends GracefulSleepyService {
    public interface Context extends MultiConnectionFullDatabaseContext, ThreadPoolContext, BlockInflaters, NodeManagerContext, SystemTimeContext, UpgradeScheduleContext { }

    public interface NewBlockProcessedCallback {
        void onNewBlock(ProcessBlockResult processBlockResult);
    }

    public interface UnavailableBlockCallback {
        void onRequiredBlockUnavailable(Sha256Hash blockHash, Long blockHeight);
    }

    protected final Context _context;
    protected final BlockProcessor _blockProcessor;
    protected final BlockDownloader.StatusMonitor _blockDownloaderStatusMonitor;
    protected final PendingBlockStore _blockStore;
    protected Boolean _hasGenesisBlock;
    protected NewBlockProcessedCallback _asynchronousNewBlockProcessedCallback;
    protected NewBlockProcessedCallback _synchronousNewBlockProcessedCallback;
    protected UnavailableBlockCallback _unavailableBlockCallback;
    protected final Long _minPreloadBlockHeight = 100_000L;

    protected final CircleBuffer<Long> _blockProcessingTimes = new CircleBuffer<>(100);
    protected Float _averageBlocksPerSecond = 0F;

    protected void _executeCallbacks(final ProcessBlockResult processBlockResult) {
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

    protected void _checkUtxoSet(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        if (! UnspentTransactionOutputDatabaseManager.isUtxoCacheReady()) {
            final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

            Logger.info("Rebuilding UTXO set.");
            final NanoTimer nanoTimer = new NanoTimer();
            nanoTimer.start();

            final Long utxoCommitFrequency = _blockProcessor.getUtxoCommitFrequency();
            final UnspentTransactionOutputManager unspentTransactionOutputManager = new UnspentTransactionOutputManager(databaseManager, utxoCommitFrequency);
            unspentTransactionOutputManager.buildUtxoSet(databaseManagerFactory);

            nanoTimer.stop();
            Logger.trace("Rebuilt UTXO set in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    /**
     * Stores and validates the pending Block.
     *  If not provided, the transactionOutputSet is loaded from the database.
     *  Returns true if the pending block was valid and stored.
     */
    protected Boolean _processPendingBlock(final Block block, final FullNodeDatabaseManager databaseManager, final UnspentTransactionOutputContext unspentTransactionOutputContext) {
        final ProcessBlockResult processBlockResult;
        { // Maximize the Thread priority and process the block...
            final Thread currentThread = Thread.currentThread();
            final int originalThreadPriority = currentThread.getPriority();
            try {
                currentThread.setPriority(Thread.MAX_PRIORITY);
                processBlockResult = _blockProcessor.processBlock(block, databaseManager, unspentTransactionOutputContext);
            }
            finally {
                currentThread.setPriority(originalThreadPriority);
            }
        }

        if ( processBlockResult.isValid && (! processBlockResult.wasAlreadyProcessed) ) {
            _executeCallbacks(processBlockResult);
        }

        return processBlockResult.isValid;
    }

    protected void _updateAverageBlockProcessingTime() {
        Long totalTimeInMilliseconds = 0L;
        int blockCount = 0;
        for (final Long averageBlockProcessingTime : _blockProcessingTimes) {
            totalTimeInMilliseconds += averageBlockProcessingTime;
            blockCount += 1;
        }

        if (blockCount == 0) {
            _averageBlocksPerSecond = 0F;
            return;
        }

        _averageBlocksPerSecond = (blockCount / (totalTimeInMilliseconds / 1000F));
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

                final boolean genesisBlockWasLoaded;
                if (! hasBlockDataAvailable) {
                    genesisBlockWasLoaded = false;
                }
                else {
                    final ByteArray pendingBlockData = _blockStore.getPendingBlockData(BlockHeader.GENESIS_BLOCK_HASH);

                    final BlockInflater blockInflater = _context.getBlockInflater();
                    final Block genesisBlock = blockInflater.fromBytes(pendingBlockData);
                    if (genesisBlock == null) {
                        genesisBlockWasLoaded = false;
                    }
                    else {
                        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                        // Manually store the genesis block, bypassing the BlockProcessor since the Genesis Block should not be validated or added to the UTXO set.
                        databaseManager.startTransaction();
                        blockDatabaseManager.storeBlock(genesisBlock);
                        databaseManager.commitTransaction();

                        final ProcessBlockResult processBlockResult = ProcessBlockResult.valid(genesisBlock, 0L, true, false);
                        genesisBlockWasLoaded = true;

                        if (processBlockResult.isValid) {
                            _executeCallbacks(processBlockResult);
                        }
                    }

                    _blockStore.removePendingBlock(BlockHeader.GENESIS_BLOCK_HASH);
                }

                if (! genesisBlockWasLoaded) {
                    Logger.debug("Waiting for genesis block.");
                    final UnavailableBlockCallback unavailableBlockCallback = _unavailableBlockCallback;
                    if (unavailableBlockCallback != null) {
                        final ThreadPool threadPool = _context.getThreadPool();
                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                unavailableBlockCallback.onRequiredBlockUnavailable(BlockHeader.GENESIS_BLOCK_HASH, 0L);
                            }
                        });
                    }
                    return;
                }

                _hasGenesisBlock = true;
            }
        }

        while (! _shouldAbort()) {
            final List<BlockchainSegmentId> blockchainSegmentIds = _getLeafBlockchainSegmentsByChainWork(databaseManager);
            if (blockchainSegmentIds.isEmpty()) {
                // No blockchain segments left to sync...
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

        // Find the next head block be processed... (depends on BlockHeaders)
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

            final BlockId lastBlockWithTransactionsOfBlockchainSegment = blockDatabaseManager.getHeadBlockIdOfBlockchainSegment(currentBlockchainSegmentId);
            headBlockId = lastBlockWithTransactionsOfBlockchainSegment;
        }
        if (headBlockId == null) { return false; }

        final UpgradeSchedule upgradeSchedule = _context.getUpgradeSchedule();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final BlockchainBuilderContextPreLoader preLoader = new BlockchainBuilderContextPreLoader(databaseManagerFactory, upgradeSchedule)) {
            Block previousBlock = null;
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

                final Boolean blockDataExists = _blockStore.pendingBlockExists(nextBlockHash);
                final ByteArray pendingBlockData = (blockDataExists ? _blockStore.getPendingBlockData(nextBlockHash) : null);

                if (pendingBlockData == null) {
                    Logger.debug("Waiting for unavailable block: " + nextBlockHash);

                    final UnavailableBlockCallback unavailableBlockCallback = _unavailableBlockCallback;
                    if (unavailableBlockCallback != null) {
                        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(nextBlockId);

                        final ThreadPool threadPool = _context.getThreadPool();
                        threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                unavailableBlockCallback.onRequiredBlockUnavailable(nextBlockHash, blockHeight);
                            }
                        });
                    }

                    return false;
                }

                final BlockInflater blockInflater = _context.getBlockInflater();
                final Block block = blockInflater.fromBytes(pendingBlockData);
                if (block == null) {
                    Logger.info("Unable to inflate block: " + nextBlockHash);

                    databaseManager.startTransaction();
                    blockHeaderDatabaseManager.markBlockAsInvalid(nextBlockHash, 1);
                    _blockStore.removePendingBlock(nextBlockHash);
                    databaseManager.commitTransaction();

                    return false;
                }

                _checkUtxoSet(databaseManager);

                final Block nextBlock;
                final Long nextBlockHeight;
                {
                    final BlockId nextNextBlockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, nextBlockId);
                    final Sha256Hash nextNextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextNextBlockId);
                    final Boolean nextBlockDataExists = _blockStore.pendingBlockExists(nextNextBlockHash);
                    final ByteArray nextPendingBlockData = (nextBlockDataExists ? _blockStore.getPendingBlockData(nextNextBlockHash) : null);
                    nextBlock = (nextPendingBlockData != null ? blockInflater.fromBytes(nextPendingBlockData) : null);
                    nextBlockHeight = blockHeaderDatabaseManager.getBlockHeight(nextNextBlockId);
                }

                UnspentTransactionOutputContext unspentTransactionOutputContext = null;
                try {
                    final NanoTimer nanoTimer = new NanoTimer();
                    nanoTimer.start();

                    if ( (nextBlock != null) && (Util.coalesce(nextBlockHeight) > _minPreloadBlockHeight)) {
                        unspentTransactionOutputContext = preLoader.getContext(previousBlock, block, nextBlock, nextBlockHeight);
                    }

                    nanoTimer.stop();

                    if (Logger.isTraceEnabled()) {
                        Logger.trace("Waited " + nanoTimer.getMillisecondsElapsed() + "ms for block context.");
                    }
                }
                catch (final InterruptedException exception) {
                    preLoader.close();
                }

                final Boolean processBlockWasSuccessful = _processPendingBlock(block, databaseManager, unspentTransactionOutputContext);

                if (! processBlockWasSuccessful) {
                    Logger.debug("Pending block failed during processing: " + nextBlockHash);

                    databaseManager.startTransaction();
                    blockHeaderDatabaseManager.markBlockAsInvalid(nextBlockHash, 1);

                    final Boolean blockIsOfficiallyInvalid = blockHeaderDatabaseManager.isBlockInvalid(nextBlockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
                    if (blockIsOfficiallyInvalid) {
                        _blockStore.removePendingBlock(nextBlockHash);
                    }
                    databaseManager.commitTransaction();

                    return false;
                }

                _blockStore.removePendingBlock(nextBlockHash);

                headBlockId = nextBlockId;

                milliTimer.stop();
                _blockProcessingTimes.push(milliTimer.getMillisecondsElapsed());
                milliTimer.start();

                _updateAverageBlockProcessingTime();

                previousBlock = block;
            }
        }

        return false;
    }

    @Override
    protected void _onStart() {
        if (_hasGenesisBlock == null) {
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
    }

    @Override
    protected Boolean _run() {
        if (_shouldAbort()) { return false; }

        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        synchronized (BlockHeaderDatabaseManager.MUTEX) {
            try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                _assembleBlockchain(databaseManager);
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }
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

        _hasGenesisBlock = null;
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

    public Float getAverageBlocksPerSecond() {
        return _averageBlocksPerSecond;
    }

    /**
     * Sets a callback to be executed when the BlockchainBuilder requires a block to continue that is currently unavailable.
     */
    public void setUnavailableBlockCallback(final UnavailableBlockCallback unavailableBlockCallback) {
        _unavailableBlockCallback = unavailableBlockCallback;
    }

    @Override
    public void stop() {
        super.stop();
    }
}

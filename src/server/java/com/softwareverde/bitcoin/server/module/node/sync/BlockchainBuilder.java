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
    public interface Context extends MultiConnectionFullDatabaseContext, ThreadPoolContext, BlockInflaters, NodeManagerContext, SystemTimeContext { }

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

    protected final CircleBuffer<Long> _blockProcessingTimes = new CircleBuffer<>(100);
    protected Float _averageBlocksPerSecond = 0F;

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
                else {
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

        // Find the next head block be processed... (depends on BlockHeaders)
        //  Traverse from the head blockHeader to the first processed block, then select its child blockHeader along the head blockchainSegment path.
        //  Since the head block and head blockHeader may have diverged, traversing backwards along the head blockHeader blockchainSegments is necessary.
        BlockId headBlockId = null;
        BlockchainSegmentId currentBlockchainSegmentId = blockchainSegmentId;
        while (headBlockId == null) {
            final BlockId firstBlockIdOfHeadBlockchainSegment = blockchainDatabaseManager.getFirstBlockIdOfBlockchainSegment(currentBlockchainSegmentId);
            Logger.trace("firstBlockIdOfHeadBlockchainSegment=" + firstBlockIdOfHeadBlockchainSegment);
            final Boolean firstBlockOfHeadBlockchainHasTransactions = blockDatabaseManager.hasTransactions(firstBlockIdOfHeadBlockchainSegment);
            Logger.trace("firstBlockOfHeadBlockchainHasTransactions=" + firstBlockOfHeadBlockchainHasTransactions);
            if (! firstBlockOfHeadBlockchainHasTransactions) {
                currentBlockchainSegmentId = blockchainDatabaseManager.getPreviousBlockchainSegmentId(currentBlockchainSegmentId);
                Logger.trace("currentBlockchainSegmentId=" + currentBlockchainSegmentId);
                if (currentBlockchainSegmentId == null) { break; }
                continue;
            }

            final BlockId lastBlockWithTransactionsOfBlockchainSegment = blockDatabaseManager.getHeadBlockIdOfBlockchainSegment(currentBlockchainSegmentId);
            Logger.trace("lastBlockWithTransactionsOfBlockchainSegment=" + lastBlockWithTransactionsOfBlockchainSegment);
            headBlockId = lastBlockWithTransactionsOfBlockchainSegment;
        }
        if (headBlockId == null) { return false; }
        Logger.trace("headBlockId=" + headBlockId);

        while (! _shouldAbort()) {
            final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, headBlockId);
            Logger.trace("nextBlockId=" + nextBlockId);
            if (nextBlockId == null) { return true; }

            // TODO: RESUME: nextBlockHash is stuck as the first block processed after restart (is it not getting updated after a block process?)
            //[2022-09-26 13:46:49.201] [DEBUG] [com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder.java:275] NextBlockHash: 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D
            //[2022-09-26 13:46:49.216] [TRACE] [com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet.java:268] Load UTXOs MultiTimer: blockHeights=0.807182ms, blockTxns=0.019152ms, blockchainSegmentId=0.021347ms, loadOutputs=0.020109ms, headerId=0.231257ms, excludeOutputs=0.019389ms
            //[2022-09-26 13:46:49.217] [DEBUG] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:376] Using liveLoadedUnspentTransactionOutputs for blockHeight: 2069
            //[2022-09-26 13:46:49.219] [DEBUG] [com.softwareverde.bitcoin.block.validator.BlockValidator.java:330] Trusting Block Height: 2069
            //[2022-09-26 13:46:49.220] [TRACE] [com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager.java:176] UTXO Block Height: 2069
            //[2022-09-26 13:46:49.229] [DEBUG] [com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo.UnspentTransactionOutputManager.java:178] BlockHeight: 2069 1 unspent, 0 spent, 0 unspendable. 1 transactions in 0 ms (1000 tps), 0ms for UTXOs. 1000 tps.
            //[2022-09-26 13:46:49.230] [DEBUG] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:423] Applied 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D @ 2069 to UTXO set in 10.542673ms.
            //[2022-09-26 13:46:49.231] [TRACE] [com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager.java:108] Flagged cache as mutated: 0
            //[2022-09-26 13:46:49.240] [DEBUG] [com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager.java:120] AssociateTransactions: 2ms
            //[2022-09-26 13:46:49.241] [DEBUG] [com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager.java:123] StoreBlockDuration: 10ms
            //[2022-09-26 13:46:49.258] [INFO] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:441] Stored 1 transactions in 16.34ms (61.20 tps). 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D
            //[2022-09-26 13:46:49.259] [TRACE] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:471] bestBlockchainHasChanged=false
            //[2022-09-26 13:46:49.260] [TRACE] [com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager.java:251] Committing mutated cache: 0
            //[2022-09-26 13:46:49.260] [TRACE] [com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager.java:253] Committed mutated cache: 0
            //[2022-09-26 13:46:49.260] [TRACE] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:576] ProcessBlock MultiTimer: metadata=0.73706ms, undoLog=0.003325ms, blockchainSegment=0.010052ms, mempool=0.590081ms, utxoUpdate=11.075807ms, commit=0.560353ms, utxoContext=3.206631ms, blockSize=0.473685ms, blockchainChange=0.323907ms, blockchain=0.678644ms, txStore=27.965277ms, validation=2.459287ms, txIndexing=0.011195ms
            //[2022-09-26 13:46:49.260] [DEBUG] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:578] Block Height: 2069
            //[2022-09-26 13:46:49.261] [INFO] [com.softwareverde.bitcoin.server.module.node.BlockProcessor.java:579] Processed Block with 1 transactions in 48.14ms (20.77 tps). 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D
            // ...
            //[2022-09-26 14:09:59.548] [TRACE] [com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder.java:246] Assembling blocks leading to blockchain segment 1
            //[2022-09-26 14:09:59.548] [DEBUG] [com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder.java:275] NextBlockHash: 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D
            //[2022-09-26 14:09:59.548] [DEBUG] [com.softwareverde.bitcoin.server.module.node.sync.BlockchainBuilder.java:287] Waiting for unavailable block: 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D
            //[2022-09-26 14:09:59.548] [DEBUG] [com.softwareverde.bitcoin.server.module.node.NodeModule.java:791] Requesting Block: 00000000EA79BC139EA90DB3446DAA3CBDCD1A4BAC1FDC191743DBAE3633368D:2069 (paused=false)
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
                blockHeaderDatabaseManager.markBlockAsInvalid(nextBlockHash, 1);
                return false;
            }

            _checkUtxoSet(databaseManager);

            final Boolean processBlockWasSuccessful = _processPendingBlock(block);

            if (! processBlockWasSuccessful) {
                blockHeaderDatabaseManager.markBlockAsInvalid(nextBlockHash, 1);
                Logger.debug("Pending block failed during processing: " + nextBlockHash);

                final Boolean blockIsOfficiallyInvalid = blockHeaderDatabaseManager.isBlockInvalid(nextBlockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
                if (blockIsOfficiallyInvalid) {
                    _blockStore.removePendingBlock(nextBlockHash);
                }

                return false;
            }

            _blockStore.removePendingBlock(nextBlockHash);

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
    protected Boolean _run() {
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

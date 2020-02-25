package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockHasher;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.BlockProcessor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
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
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.BlockLoader;
import com.softwareverde.bitcoin.server.module.node.sync.blockloader.PreloadedPendingBlock;
import com.softwareverde.bitcoin.transaction.validator.UnspentTransactionOutputSet;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

public class BlockchainBuilder extends SleepyService {
    public interface NewBlockProcessedCallback {
        void onNewBlock(Long blockHeight, Block block);
    }

    protected final ThreadPool _threadPool;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockInflaters _blockInflaters;
    protected final BlockProcessor _blockProcessor;
    protected final BlockDownloader.StatusMonitor _downloadStatusMonitor;
    protected final BlockDownloadRequester _blockDownloadRequester;
    protected final BlockLoader _blockLoader;
    protected Boolean _hasGenesisBlock;
    protected NewBlockProcessedCallback _newBlockProcessedCallback = null;

    /**
     * Stores and validates the pending Block.
     *  If not provided, the transactionOutputSet is loaded from the database.
     *  Returns true if the pending block was valid and stored.
     */
    protected Boolean _processPendingBlock(final PendingBlock pendingBlock, final UnspentTransactionOutputSet transactionOutputSet) {
        if (pendingBlock == null) { return false; } // NOTE: Can happen due to race condition...

        final ByteArray blockData = pendingBlock.getData();
        if (blockData == null) { return false; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        final Block block = pendingBlock.inflateBlock(blockInflater);

        if (block == null) {
            Logger.warn("Pending Block Corrupted: " + pendingBlock.getBlockHash() + " " + blockData);
            return false;
        }

        final Long processedBlockHeight;
        { // Maximize the Thread priority and process the block...
            final Thread currentThread = Thread.currentThread();
            final Integer originalThreadPriority = currentThread.getPriority();
            try {
                currentThread.setPriority(Thread.MAX_PRIORITY);
                processedBlockHeight = _blockProcessor.processBlock(block, transactionOutputSet);
            }
            finally {
                currentThread.setPriority(originalThreadPriority);
            }
        }

        final Boolean blockWasValid = (processedBlockHeight != null);

        if (blockWasValid) {
            final NewBlockProcessedCallback newBlockProcessedCallback = _newBlockProcessedCallback;
            if (newBlockProcessedCallback != null) {
                _threadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        newBlockProcessedCallback.onNewBlock(processedBlockHeight, block);
                    }
                });
            }
        }

        return blockWasValid;
    }

    protected Boolean _processGenesisBlock(final PendingBlockId pendingBlockId, final FullNodeDatabaseManager databaseManager, final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager) throws DatabaseException {
        final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
        final ByteArray blockData = pendingBlock.getData();
        if (blockData == null) { return false; }

        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();
        final Block block = blockInflater.fromBytes(blockData);
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

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
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
                        _blockDownloadRequester.requestBlock(BlockHeader.GENESIS_BLOCK_HASH);
                        return false;
                    }
                }
            }

            while (! thread.isInterrupted()) {
                // Select the first pending block to process; if none are found, request one to be downloaded...
                final PendingBlockId candidatePendingBlockId = pendingBlockDatabaseManager.selectCandidatePendingBlockId();
                if (candidatePendingBlockId == null) {
                    // Request the next head block be downloaded... (depends on BlockHeaders)
                    final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
                    final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                    final BlockId nextBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, headBlockId);
                    if (nextBlockId != null) {
                        final Sha256Hash nextBlockHash = blockHeaderDatabaseManager.getBlockHash(nextBlockId);
                        Logger.debug("Requesting Block: " + nextBlockHash);
                        _blockDownloadRequester.requestBlock(nextBlockHash);
                    }
                    break;
                }

                // Process the first available candidate block...
                final PendingBlock candidatePendingBlock = pendingBlockDatabaseManager.getPendingBlock(candidatePendingBlockId);
                final Boolean processCandidateBlockWasSuccessful = _processPendingBlock(candidatePendingBlock, null);
                if (! processCandidateBlockWasSuccessful) {
                    TransactionUtil.startTransaction(databaseConnection);
                    pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                    TransactionUtil.commitTransaction(databaseConnection);
                    Logger.debug("Deleted failed pending block.");
                    continue;
                }

                TransactionUtil.startTransaction(databaseConnection);
                pendingBlockDatabaseManager.deletePendingBlock(candidatePendingBlockId);
                TransactionUtil.commitTransaction(databaseConnection);

                // Process the any viable descendant blocks of the candidate block...
                PendingBlock previousPendingBlock = candidatePendingBlock;
                while (! thread.isInterrupted()) {
                    final List<PendingBlockId> pendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(previousPendingBlock.getBlockHash());
                    if (pendingBlockIds.isEmpty()) { break; }

                    for (int i = 0; i < pendingBlockIds.getCount(); ++i) {
                        final PendingBlockId pendingBlockId = pendingBlockIds.get(i);
                        final Sha256Hash pendingBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);

                        final PreloadedPendingBlock preloadedPendingBlock = _blockLoader.getBlock(pendingBlockHash, pendingBlockId);
                        final PendingBlock pendingBlock = preloadedPendingBlock.getPendingBlock();
                        final UnspentTransactionOutputSet unspentTransactionOutputSet = preloadedPendingBlock.getUnspentTransactionOutputSet();

                        final Boolean processBlockWasSuccessful = _processPendingBlock(pendingBlock, unspentTransactionOutputSet); // pendingBlock may be null; _processPendingBlock allows for this.

                        TransactionUtil.startTransaction(databaseConnection);
                        pendingBlockDatabaseManager.deletePendingBlock(pendingBlockId);
                        TransactionUtil.commitTransaction(databaseConnection);

                        if (! processBlockWasSuccessful) {
                            Logger.debug("Pending block failed during processing: " + pendingBlockHash);
                            break;
                        }

                        previousPendingBlock = pendingBlock;
                    }
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
        final Status downloadStatus = _downloadStatusMonitor.getStatus();
        if (downloadStatus != Status.ACTIVE) {
            try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
                final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
                _bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
            }
            catch (final DatabaseException exception) {
                Logger.warn(exception);
            }
        }
    }

    public BlockchainBuilder(
        final BitcoinNodeManager bitcoinNodeManager,
        final FullNodeDatabaseManagerFactory databaseManagerFactory,
        final BlockInflaters blockInflaters,
        final BlockProcessor blockProcessor,
        final BlockLoader blockLoader,
        final BlockDownloader.StatusMonitor downloadStatusMonitor,
        final BlockDownloadRequester blockDownloadRequester,
        final ThreadPool threadPool
    ) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseManagerFactory = databaseManagerFactory;
        _blockInflaters = blockInflaters;
        _blockProcessor = blockProcessor;
        _blockLoader = blockLoader;
        _downloadStatusMonitor = downloadStatusMonitor;
        _blockDownloadRequester = blockDownloadRequester;
        _threadPool = threadPool;

        try (final DatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            _hasGenesisBlock = blockDatabaseManager.hasTransactions(BlockHeader.GENESIS_BLOCK_HASH);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            _hasGenesisBlock = false;
        }
    }

    public void setNewBlockProcessedCallback(final NewBlockProcessedCallback newBlockProcessedCallback) {
        _newBlockProcessedCallback = newBlockProcessedCallback;
    }
}

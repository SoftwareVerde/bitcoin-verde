package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.Blockchain;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.HashSet;

public class BlockDownloadPlannerCore implements BlockDownloader.BlockDownloadPlanner {
    protected final Integer _batchSize = 256;
    protected final Blockchain _blockchain;
    protected final PendingBlockStore _blockStore;
    protected final HashSet<PendingBlockInventory> _requestedInventory = new HashSet<>();

    protected Boolean _alternateChainDownloadIsEnabled = true;
    protected Integer _maxDownloadAheadDepth = 2048;
    protected PendingBlockInventory _bestCompletedInventory = null;

    protected PendingBlockInventory _recalculateBestCompletedInventory() {
        final Long bestBlockHeight = _blockchain.getHeadBlockHeight();
        if (bestBlockHeight == null || bestBlockHeight <= 0L) {
            return new PendingBlockInventory(0L, BlockHeader.GENESIS_BLOCK_HASH, 0L);
        }

        Sha256Hash lastDownloadedBlockHash = _blockchain.getHeadBlockHash();
        do {
            final BlockHeader childBlockHeader = _blockchain.getChildBlockHeader(lastDownloadedBlockHash, 1);
            if (childBlockHeader == null) { break; }

            final Sha256Hash blockHash = childBlockHeader.getHash();

            final boolean blockIsDownloaded;
            {
                final boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
                if (pendingBlockExists) {
                    blockIsDownloaded = true;
                }
                else {
                    final Long blockHeight = _blockchain.getBlockHeight(blockHash);
                     blockIsDownloaded = _blockStore.blockExists(blockHash, blockHeight);
                }
            }

            if (! blockIsDownloaded) {
                lastDownloadedBlockHash = blockHash;
                break;
            }
        } while (true);

        final Long blockHeight = _blockchain.getBlockHeight(lastDownloadedBlockHash);
        return new PendingBlockInventory(0L, lastDownloadedBlockHash, blockHeight);
    }

    protected List<PendingBlockInventory> _getPendingBlockInventoryBatchForBlockchainSegment(final Long nullableStartingBlockHeight, final Integer batchSize) {
        final MutableList<PendingBlockInventory> pendingBlockInventoryBatch = new MutableArrayList<>();

        final long startingBlockHeight;
        {
            if (nullableStartingBlockHeight != null) {
                startingBlockHeight = nullableStartingBlockHeight;
            }
            else {
                final Boolean genesisHasAlreadyBeenDownloaded = _blockStore.pendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH);
                if (! genesisHasAlreadyBeenDownloaded) {
                    final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(0L, BlockHeader.GENESIS_BLOCK_HASH, 0L);
                    pendingBlockInventoryBatch.add(pendingBlockInventory);
                }
                startingBlockHeight = 0L;
            }
        }

        int alreadyDownloadedItemCount = 0;
        Long previousBlockHeight = startingBlockHeight;
        for (int i = 0; (i - alreadyDownloadedItemCount) < batchSize; ++i) {
            final Sha256Hash previousBlockHash = _blockchain.getBlockHash(previousBlockHeight);
            final BlockHeader childBlockHeader = _blockchain.getChildBlockHeader(previousBlockHash, 1);
            if (childBlockHeader == null) { break; }

            final Sha256Hash blockHash = childBlockHeader.getHash();
            final Long blockHeight = (startingBlockHeight + i);

            final boolean isBlockInvalid = _blockchain.isBlockInvalid(blockHash);
            if (isBlockInvalid) { break; }

            final Boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
            if (! pendingBlockExists) {
                final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(blockHeight, blockHash, blockHeight);
                pendingBlockInventoryBatch.add(pendingBlockInventory);
            }
            else {
                alreadyDownloadedItemCount += 1;
            }

            previousBlockHeight = blockHeight;
        }

        return pendingBlockInventoryBatch;
    }

    protected BlockId _getStartingBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final BlockId headBlockIdWithinBlockchainSegment = blockDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
        if (headBlockIdWithinBlockchainSegment != null) {
            return headBlockIdWithinBlockchainSegment;
        }

        // Recurse up to the next BlockchainSegment since this segment has no processed blocks...
        final BlockchainSegmentId parentBlockchainSegmentId = blockchainDatabaseManager.getPreviousBlockchainSegmentId(blockchainSegmentId);
        if (parentBlockchainSegmentId == null) { return null; } // Exit if the BlockchainSegment has no parent...

        return _getStartingBlockIdOfBlockchainSegment(parentBlockchainSegmentId, databaseManager);
    }

    protected List<PendingBlockInventory> _getAlternateChainPendingBlockInventoryBatch(final ChainWork minAlternateChainWork) {
        final MutableList<PendingBlockInventory> nextInventoryBatch = new MutableArrayList<>();

        // TODO
//        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
//
//        final List<BlockchainSegmentId> leafBlockchainSegmentIds = blockchainDatabaseManager.getLeafBlockchainSegmentIds();
//        for (final BlockchainSegmentId blockchainSegmentId : leafBlockchainSegmentIds) {
//            if (Util.areEqual(headBlockchainSegmentId, blockchainSegmentId)) { continue; }
//
//            final BlockId headBlockIdOfBlockchainSegment = blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
//            final ChainWork chainWorkOfBlockchainSegment = blockHeaderDatabaseManager.getChainWork(headBlockIdOfBlockchainSegment);
//            if (chainWorkOfBlockchainSegment.compareTo(minAlternateChainWork) < 0) { continue; } // Head of alternate chain does not have enough ChainWork...
//
//            final BlockId startingBlockId = _getStartingBlockIdOfBlockchainSegment(blockchainSegmentId, databaseManager);
//
//            final int batchSizeRemaining = (_batchSize - nextInventoryBatch.getCount());
//            final List<PendingBlockInventory> blockchainSegmentInventory = _getPendingBlockInventoryBatchForBlockchainSegment(startingBlockId, batchSizeRemaining);
//            nextInventoryBatch.addAll(blockchainSegmentInventory);
//
//            if (nextInventoryBatch.getCount() >= _batchSize) {
//                return nextInventoryBatch;
//            }
//        }

        return nextInventoryBatch;
    }

    public BlockDownloadPlannerCore(final Blockchain blockchain, final PendingBlockStore blockStore) {
        _blockchain = blockchain;
        _blockStore = blockStore;
    }

    @Override
    public synchronized List<PendingBlockInventory> getNextPendingBlockInventoryBatch() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        try {
            final MutableList<PendingBlockInventory> inventoryBatch = new MutableArrayList<>(_requestedInventory);
            _requestedInventory.clear();

            if (_bestCompletedInventory != null) {
                final Long bestInventoryBlockHeight = _blockchain.getBlockHeight(_bestCompletedInventory.blockHash);
                final boolean bestInventoryIsStillConnectedToMainChain = (bestInventoryBlockHeight != null);
                if (!bestInventoryIsStillConnectedToMainChain) {
                    _bestCompletedInventory = null;
                }
            }

            if (_bestCompletedInventory == null) {
                _bestCompletedInventory = _recalculateBestCompletedInventory();
            }
            else { // Update _bestCompletedInventory with completed blocks, starting with at current best inventory...
                final NanoTimer updateBestInventoryTimer = new NanoTimer();
                updateBestInventoryTimer.start();
                int loopCount = 0;
                while (true) {
                    Long childBlockHeight = null;
                    {
                        long blockHeight = _bestCompletedInventory.blockHeight + 1L;
                        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
                        if (blockHeader != null) {
                            childBlockHeight = blockHeight;
                        }
                    }
                    if (childBlockHeight == null) { break; }

                    final Sha256Hash blockHash = _blockchain.getBlockHash(childBlockHeight);

                    final boolean blockHasBeenDownloaded = (_blockStore.pendingBlockExists(blockHash) || _blockStore.blockExists(blockHash, childBlockHeight));
                    if (!blockHasBeenDownloaded) { break; }

                    _bestCompletedInventory = new PendingBlockInventory(_bestCompletedInventory.priority, blockHash, childBlockHeight);

                    loopCount += 1;
                }
                updateBestInventoryTimer.stop();
                if (Logger.isTraceEnabled()) {
                    Logger.trace("New best inventory: " + _bestCompletedInventory.blockHash + " " + _bestCompletedInventory.blockHeight + ", " + loopCount + " iterations in " + updateBestInventoryTimer.getMillisecondsElapsed() + "ms.");
                }
            }

            { // Prioritize downloading the main chain...
                if (Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, _bestCompletedInventory.blockHash)) {
                    // Check if the GenesisBlock needs to be downloaded...
                    final Boolean genesisBlockHasBeenProcessed = _blockStore.blockExists(BlockHeader.GENESIS_BLOCK_HASH, 0L);
                    if ((!genesisBlockHasBeenProcessed) && (!_blockStore.pendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH))) {
                        inventoryBatch.add(_bestCompletedInventory);
                    }
                }

                final int batchSize;
                final Integer maxDownloadAheadDepth = _maxDownloadAheadDepth;
                if (maxDownloadAheadDepth != null) {
                    final Long headBlockHeight = _blockchain.getHeadBlockHeaderHeight();
                    final Long startingBlockHeight = _blockchain.getBlockHeight(_bestCompletedInventory.blockHash);

                    if ((headBlockHeight != null) && (startingBlockHeight != null) && (startingBlockHeight > headBlockHeight)) {
                        final int downloadAheadDepth = (int) (startingBlockHeight - headBlockHeight);
                        Logger.trace("Download Ahead Depth: " + downloadAheadDepth);
                        if (downloadAheadDepth >= maxDownloadAheadDepth) { return inventoryBatch; }

                        batchSize = Math.min(_batchSize, (maxDownloadAheadDepth - downloadAheadDepth));
                    }
                    else {
                        batchSize = _batchSize;
                    }
                }
                else {
                    batchSize = _batchSize;
                }
                Logger.trace("Batch Size: " + batchSize);

                final NanoTimer getBatchTimer = new NanoTimer();
                getBatchTimer.start();

                final Long startingBlockHeight = _blockchain.getBlockHeight(_bestCompletedInventory.blockHash) + 1L;
                final List<PendingBlockInventory> mainChainInventoryBatch = _getPendingBlockInventoryBatchForBlockchainSegment(startingBlockHeight, batchSize);

                getBatchTimer.stop();
                Logger.trace("Got Inventory Batch for next BlockchainSegment in " + getBatchTimer.getMillisecondsElapsed() + "ms.");

                if (!mainChainInventoryBatch.isEmpty()) {
                    if (inventoryBatch.isEmpty()) { // Avoid copying the list unnecessarily...
                        return mainChainInventoryBatch;
                    }
                    else {
                        inventoryBatch.addAll(mainChainInventoryBatch);
                        return inventoryBatch;
                    }
                }
            }

            if (!_alternateChainDownloadIsEnabled) {
                return inventoryBatch;
            }

            final int maxAlternateEvaluateBlockDepth = 144;
            final ChainWork minAlternateChainWork;
            {
                final Long headBlockHeight = _blockchain.getHeadBlockHeight();
                if (headBlockHeight == null || headBlockHeight < 0L) { return inventoryBatch; }

                final Long minBlockHeight = Math.max(0L, (headBlockHeight - maxAlternateEvaluateBlockDepth));
                minAlternateChainWork = _blockchain.getChainWork(minBlockHeight);
                if (minAlternateChainWork == null) { return inventoryBatch; }
            }

            final List<PendingBlockInventory> alternateChainInventory = _getAlternateChainPendingBlockInventoryBatch(minAlternateChainWork);
            if (inventoryBatch.isEmpty()) { // Avoid copying the list unnecessarily...
                return alternateChainInventory;
            }
            else {
                inventoryBatch.addAll(alternateChainInventory);
                return inventoryBatch;
            }
        }
        finally {
            nanoTimer.stop();
            Logger.trace("Determined next BlockDownloader batch in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    @Override
    public synchronized void clearQueue() {
        _requestedInventory.clear();
        _bestCompletedInventory = null;
    }

    public synchronized void requestInventory(final Sha256Hash blockHash, final Long blockHeight) {
        final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(0L, blockHash, blockHeight);
        _requestedInventory.add(pendingBlockInventory);
    }

    public void setMaxDownloadAheadDepth(final Integer maxDownloadAheadDepth) {
        _maxDownloadAheadDepth = maxDownloadAheadDepth;
    }

    public void enableAlternateChainDownload(final Boolean isEnabled) {
        _alternateChainDownloadIsEnabled = isEnabled;
    }
}

package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.difficulty.work.ChainWork;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

import java.util.HashSet;

public class BlockDownloadPlannerCore implements BlockDownloader.BlockDownloadPlanner {
    protected final Integer _batchSize = 256;
    protected final PendingBlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final HashSet<PendingBlockInventory> _requestedInventory = new HashSet<>();

    protected Boolean _alternateChainDownloadIsEnabled = true;
    protected Integer _maxDownloadAheadDepth = 2048;
    protected PendingBlockInventory _bestCompletedInventory = null;

    protected PendingBlockInventory _recalculateBestCompletedInventory(final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();
        final BlockId bestBlockId = blockDatabaseManager.getHeadBlockId();
        if (bestBlockId == null) {
            return new PendingBlockInventory(0L, BlockHeader.GENESIS_BLOCK_HASH, 0L);
        }

        final Sha256Hash bestBlockHash = blockHeaderDatabaseManager.getBlockHash(bestBlockId);
        final Tuple<BlockId, Sha256Hash> lastDownloadedBlock = new Tuple<>(bestBlockId, bestBlockHash);

        do {
            final BlockId blockId = blockHeaderDatabaseManager.getChildBlockId(blockchainSegmentId, bestBlockId);
            if (blockId == null) { break; }

            final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);

            lastDownloadedBlock.first = blockId;
            lastDownloadedBlock.second = blockHash;

            final boolean blockIsDownloaded;
            {
                final boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
                if (pendingBlockExists) {
                    blockIsDownloaded = true;
                }
                else {
                    final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
                     blockIsDownloaded = _blockStore.blockExists(blockHash, blockHeight);
                }
            }
            if (! blockIsDownloaded) { break; }
        } while (true);

        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(lastDownloadedBlock.first);
        return new PendingBlockInventory(0L, lastDownloadedBlock.second, blockHeight);
    }

    protected List<PendingBlockInventory> _getPendingBlockInventoryBatchForBlockchainSegment(final BlockId nullableStartingBlockId, final BlockchainSegmentId headBlockchainSegmentId, final FullNodeDatabaseManager databaseManager, final Integer batchSize) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        final MutableList<PendingBlockInventory> pendingBlockInventoryBatch = new MutableList<>();

        final BlockId startingBlockId;
        {
            if (nullableStartingBlockId != null) {
                startingBlockId = nullableStartingBlockId;
            }
            else {
                final Boolean genesisHasAlreadyBeenDownloaded = _blockStore.pendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH);
                if (! genesisHasAlreadyBeenDownloaded) {
                    final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(0L, BlockHeader.GENESIS_BLOCK_HASH, 0L);
                    pendingBlockInventoryBatch.add(pendingBlockInventory);
                }
                startingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(BlockHeader.GENESIS_BLOCK_HASH);
            }
        }

        if (startingBlockId != null) {
            final long startingBlockHeight = blockHeaderDatabaseManager.getBlockHeight(startingBlockId);

            int alreadyDownloadedItemCount = 0;
            BlockId previousBlockId = startingBlockId;
            for (int i = 0; (i - alreadyDownloadedItemCount) < batchSize; ++i) {
                final BlockId blockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, previousBlockId);
                if (blockId == null) { break; }

                final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(blockId);
                final Long blockHeight = (startingBlockHeight + i);

                final Boolean isBlockInvalid = blockHeaderDatabaseManager.isBlockInvalid(blockHash, BlockHeaderDatabaseManager.INVALID_PROCESS_THRESHOLD);
                if (isBlockInvalid) { break; }

                final Boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
                if (! pendingBlockExists) {
                    final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(blockHeight, blockHash, blockHeight);
                    pendingBlockInventoryBatch.add(pendingBlockInventory);
                }
                else {
                    alreadyDownloadedItemCount += 1;
                }

                previousBlockId = blockId;
            }
        }

        return pendingBlockInventoryBatch;
    }

    protected BlockId _getStartingBlockIdOfBlockchainSegment(final BlockchainSegmentId blockchainSegmentId, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

        final BlockId headBlockIdWithinBlockchainSegment = blockDatabaseManager.getHeadBlockIdWithinBlockchainSegment(blockchainSegmentId);
        if (headBlockIdWithinBlockchainSegment != null) {
            return headBlockIdWithinBlockchainSegment;
        }

        // Recurse up to the next BlockchainSegment since this segment has no processed blocks...
        final BlockchainSegmentId parentBlockchainSegmentId = blockchainDatabaseManager.getPreviousBlockchainSegmentId(blockchainSegmentId);
        if (parentBlockchainSegmentId == null) { return null; } // Exit if the BlockchainSegment has no parent...

        return _getStartingBlockIdOfBlockchainSegment(parentBlockchainSegmentId, databaseManager);
    }

    protected List<PendingBlockInventory> _getAlternateChainPendingBlockInventoryBatch(final ChainWork minAlternateChainWork, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

        final MutableList<PendingBlockInventory> nextInventoryBatch = new MutableList<>();

        final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

        final List<BlockchainSegmentId> leafBlockchainSegmentIds = blockchainDatabaseManager.getLeafBlockchainSegmentIds();
        for (final BlockchainSegmentId blockchainSegmentId : leafBlockchainSegmentIds) {
            if (Util.areEqual(headBlockchainSegmentId, blockchainSegmentId)) { continue; }

            final BlockId headBlockIdOfBlockchainSegment = blockchainDatabaseManager.getHeadBlockIdOfBlockchainSegment(blockchainSegmentId);
            final ChainWork chainWorkOfBlockchainSegment = blockHeaderDatabaseManager.getChainWork(headBlockIdOfBlockchainSegment);
            if (chainWorkOfBlockchainSegment.compareTo(minAlternateChainWork) < 0) { continue; } // Head of alternate chain does not have enough ChainWork...

            final BlockId startingBlockId = _getStartingBlockIdOfBlockchainSegment(blockchainSegmentId, databaseManager);

            final int batchSizeRemaining = (_batchSize - nextInventoryBatch.getCount());
            final List<PendingBlockInventory> blockchainSegmentInventory = _getPendingBlockInventoryBatchForBlockchainSegment(startingBlockId, blockchainSegmentId, databaseManager, batchSizeRemaining);
            nextInventoryBatch.addAll(blockchainSegmentInventory);

            if (nextInventoryBatch.getCount() >= _batchSize) {
                return nextInventoryBatch;
            }
        }

        return nextInventoryBatch;
    }

    public BlockDownloadPlannerCore(final FullNodeDatabaseManagerFactory databaseManagerFactory, final PendingBlockStore blockStore) {
        _blockStore = blockStore;
        _databaseManagerFactory = databaseManagerFactory;
    }

    @Override
    public synchronized List<PendingBlockInventory> getNextPendingBlockInventoryBatch() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        final MutableList<PendingBlockInventory> inventoryBatch = new MutableList<>(_requestedInventory);
        _requestedInventory.clear();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            if (_bestCompletedInventory != null) {
                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(_bestCompletedInventory.blockHash);
                final Boolean bestInventoryIsStillConnectedToMainChain = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANY);
                if (! bestInventoryIsStillConnectedToMainChain) {
                    _bestCompletedInventory = null;
                }
            }

            if (_bestCompletedInventory == null) {
                _bestCompletedInventory = _recalculateBestCompletedInventory(databaseManager);
            }
            else { // Update _bestCompletedInventory with completed blocks, starting with at current best inventory...
                final NanoTimer updateBestInventoryTimer = new NanoTimer();
                updateBestInventoryTimer.start();
                int loopCount = 0;
                while (true) {
                    final BlockId childBlockId;
                    {
                        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(_bestCompletedInventory.blockHash);
                        childBlockId = blockHeaderDatabaseManager.getChildBlockId(headBlockchainSegmentId, blockId);
                    }
                    if (childBlockId == null) { break; }

                    final Sha256Hash blockHash = blockHeaderDatabaseManager.getBlockHash(childBlockId);
                    final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(childBlockId);

                    final boolean blockHasBeenDownloaded = ( _blockStore.pendingBlockExists(blockHash) || _blockStore.blockExists(blockHash, blockHeight) );
                    if (! blockHasBeenDownloaded) { break; }

                    _bestCompletedInventory = new PendingBlockInventory(_bestCompletedInventory.priority, blockHash, blockHeight);

                    loopCount += 1;
                }
                updateBestInventoryTimer.stop();
                if (Logger.isTraceEnabled()) {
                    Logger.trace("New best inventory: " + _bestCompletedInventory.blockHash + " " + _bestCompletedInventory.blockHeight + ", " + loopCount + " iterations in " + updateBestInventoryTimer.getMillisecondsElapsed() + "ms.");
                }
            }

            { // Prioritize downloading the main chain...
                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();
                final BlockId startingBlockId = blockHeaderDatabaseManager.getBlockHeaderId(_bestCompletedInventory.blockHash);

                if (Util.areEqual(BlockHeader.GENESIS_BLOCK_HASH, _bestCompletedInventory.blockHash)) {
                    // Check if the GenesisBlock needs to be downloaded...
                    final Boolean genesisBlockHasBeenProcessed = blockDatabaseManager.hasTransactions(startingBlockId);
                    if ( (! genesisBlockHasBeenProcessed) && (! _blockStore.pendingBlockExists(BlockHeader.GENESIS_BLOCK_HASH)) ) {
                        inventoryBatch.add(_bestCompletedInventory);
                    }
                }

                final int batchSize;
                final Integer maxDownloadAheadDepth = _maxDownloadAheadDepth;
                if (maxDownloadAheadDepth != null) {
                    final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
                    final Long startingBlockHeight = blockHeaderDatabaseManager.getBlockHeight(startingBlockId);

                    if ( (headBlockHeight != null) && (startingBlockHeight != null) && (startingBlockHeight > headBlockHeight) ) {
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

                final List<PendingBlockInventory> mainChainInventoryBatch = _getPendingBlockInventoryBatchForBlockchainSegment(startingBlockId, headBlockchainSegmentId, databaseManager, batchSize);

                getBatchTimer.stop();
                Logger.trace("Got Inventory Batch for next BlockchainSegment in " + getBatchTimer.getMillisecondsElapsed() + "ms.");

                if (! mainChainInventoryBatch.isEmpty()) {
                    if (inventoryBatch.isEmpty()) { // Avoid copying the list unnecessarily...
                        return mainChainInventoryBatch;
                    }
                    else {
                        inventoryBatch.addAll(mainChainInventoryBatch);
                        return inventoryBatch;
                    }
                }
            }

            if (! _alternateChainDownloadIsEnabled) {
                return inventoryBatch;
            }

            final int maxAlternateEvaluateBlockDepth = 144;
            final ChainWork minAlternateChainWork;
            {
                final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                if (headBlockHeaderId == null) { return inventoryBatch; }

                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
                final Long minBlockHeight = Math.max(0L, (headBlockHeight - maxAlternateEvaluateBlockDepth));
                final BlockId blockIdAtMinBlockHeight = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, minBlockHeight);
                minAlternateChainWork = blockHeaderDatabaseManager.getChainWork(blockIdAtMinBlockHeight);
            }

            final List<PendingBlockInventory> alternateChainInventory = _getAlternateChainPendingBlockInventoryBatch(minAlternateChainWork, databaseManager);
            if (inventoryBatch.isEmpty()) { // Avoid copying the list unnecessarily...
                return alternateChainInventory;
            }
            else {
                inventoryBatch.addAll(alternateChainInventory);
                return inventoryBatch;
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return inventoryBatch;
        }
        finally {
            nanoTimer.stop();
            Logger.trace("Determined next BlockDownloader batch in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    public synchronized void requestInventory(final Sha256Hash blockHash, final Long blockHeight) {
        _requestedInventory.add(new PendingBlockInventory(0L, blockHash, blockHeight));
    }

    public void setMaxDownloadAheadDepth(final Integer maxDownloadAheadDepth) {
        _maxDownloadAheadDepth = maxDownloadAheadDepth;
    }

    public void enableAlternateChainDownload(final Boolean isEnabled) {
        _alternateChainDownloadIsEnabled = isEnabled;
    }
}

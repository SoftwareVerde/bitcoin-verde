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
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.NanoTimer;

public class BlockDownloadPlannerCore implements BlockDownloader.BlockDownloadPlanner {
    protected final Integer _batchSize = 256;
    protected final PendingBlockStore _blockStore;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;

    protected Boolean _alternateChainDownloadIsEnabled = true;
    protected Integer _maxDownloadAheadDepth = 2048;
    protected PendingBlockInventory _bestCompletedInventory = null;

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

            BlockId previousBlockId = startingBlockId;
            for (int i = 0; i < batchSize; ++i) {
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
    public List<PendingBlockInventory> getNextPendingBlockInventoryBatch() {
        final NanoTimer nanoTimer = new NanoTimer();
        nanoTimer.start();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

            final BlockchainSegmentId headBlockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            { // Prioritize downloading the main chain...
                final BlockId headBlockId = blockDatabaseManager.getHeadBlockId();

                final BlockId startingBlockId;
                final PendingBlockInventory bestCompletedInventory = _bestCompletedInventory;
                if (bestCompletedInventory != null) {
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(bestCompletedInventory.blockHash);
                    final Boolean isStillMainChainBlock = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, headBlockchainSegmentId, BlockRelationship.ANY);
                    startingBlockId = (isStillMainChainBlock ? blockId : headBlockId);
                }
                else {
                    startingBlockId = headBlockId;
                }

                final int batchSize;
                final Integer maxDownloadAheadDepth = _maxDownloadAheadDepth;
                if (maxDownloadAheadDepth != null) {
                    final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockId);
                    final Long startingBlockHeight = blockHeaderDatabaseManager.getBlockHeight(startingBlockId);

                    if ( (headBlockHeight != null) && (startingBlockHeight != null) && (startingBlockHeight > headBlockHeight) ) {
                        final int downloadAheadDepth = (int) (startingBlockHeight - headBlockHeight);
                        Logger.trace("Download Ahead Depth: " + downloadAheadDepth);
                        if (downloadAheadDepth >= maxDownloadAheadDepth) { return new ImmutableList<>(); }

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

                final List<PendingBlockInventory> mainChainInventoryBatch = _getPendingBlockInventoryBatchForBlockchainSegment(startingBlockId, headBlockchainSegmentId, databaseManager, batchSize);
                if (! mainChainInventoryBatch.isEmpty()) {
                    return mainChainInventoryBatch;
                }
            }

            if (! _alternateChainDownloadIsEnabled) {
                return new ImmutableList<>();
            }

            final int maxAlternateEvaluateBlockDepth = 144;
            final ChainWork minAlternateChainWork;
            {
                final BlockId headBlockHeaderId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                if (headBlockHeaderId == null) { return new ImmutableList<>(); }

                final Long headBlockHeight = blockHeaderDatabaseManager.getBlockHeight(headBlockHeaderId);
                final Long minBlockHeight = Math.max(0L, (headBlockHeight - maxAlternateEvaluateBlockDepth));
                final BlockId blockIdAtMinBlockHeight = blockHeaderDatabaseManager.getBlockIdAtHeight(headBlockchainSegmentId, minBlockHeight);
                minAlternateChainWork = blockHeaderDatabaseManager.getChainWork(blockIdAtMinBlockHeight);
            }

            return _getAlternateChainPendingBlockInventoryBatch(minAlternateChainWork, databaseManager);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
            return new ImmutableList<>();
        }
        finally {
            nanoTimer.stop();
            Logger.trace("Determined next BlockDownloader batch in " + nanoTimer.getMillisecondsElapsed() + "ms.");
        }
    }

    @Override
    public void markInventoryComplete(final PendingBlockInventory pendingBlockInventory) {
        final PendingBlockInventory bestCompletedInventory = _bestCompletedInventory;
        final long bestBlockHeight = (bestCompletedInventory != null ? bestCompletedInventory.blockHeight : 0L);
        if (pendingBlockInventory.blockHeight > bestBlockHeight) {
            try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
                final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

                final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(pendingBlockInventory.blockHash);
                final BlockchainSegmentId blockchainSegmentId = blockchainDatabaseManager.getHeadBlockchainSegmentId();

                final Boolean isMainChainBlock = blockHeaderDatabaseManager.isBlockConnectedToChain(blockId, blockchainSegmentId, BlockRelationship.ANY);
                if (isMainChainBlock) {
                    _bestCompletedInventory = pendingBlockInventory;
                }
            }
            catch (final DatabaseException exception) {
                Logger.debug(exception);
            }
        }
    }

    public void clearInventoryComplete() {
        _bestCompletedInventory = null;
    }

    public void setMaxDownloadAheadDepth(final Integer maxDownloadAheadDepth) {
        _maxDownloadAheadDepth = maxDownloadAheadDepth;
    }

    public void enableAlternateChainDownload(final Boolean isEnabled) {
        _alternateChainDownloadIsEnabled = isEnabled;
    }
}

package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.context.core.MutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.context.lazy.LazyMutableUnspentTransactionOutputSet;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class PendingBlockLoader {
    public interface Context extends BlockInflaters, ThreadPoolContext, MultiConnectionFullDatabaseContext { }

    protected final Context _context;
    protected final CircleBuffer<PendingBlockFuture> _pendingBlockFutures;
    protected Long _loadUnspentOutputsAfterBlockHeight = null;

    /**
     * Preloads the block, specified by the pendingBlockId, and the unspentOutputs it requires.
     * When complete, the pin is released.
     * blockHeight may be null; this usually indicates the blockHeader has not been loaded yet.
     *  If the blockHeight is not provided, then outputs are not pre-loaded, and the outputs and blockHeight are determined on-demand.
     */
    protected PendingBlockFuture _asynchronouslyLoadNextPendingBlock(final Sha256Hash blockHash, final PendingBlockId pendingBlockId, final Long blockHeight, final Boolean shouldLoadUnspentOutputs) {
        final BlockInflater blockInflater = _context.getBlockInflater();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        final ThreadPool threadPool = _context.getThreadPool();

        final PendingBlockFuture pendingBlockFuture = new PendingBlockFuture(blockHash, blockInflater);
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

                    final MilliTimer milliTimer = new MilliTimer();
                    milliTimer.start();

                    final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(pendingBlockId);
                    if (pendingBlock == null) {
                        Logger.debug("Unable to load pending block: " + pendingBlockId);
                        return;
                    }

                    final Block block = pendingBlock.inflateBlock(blockInflater);
                    if (block == null) {
                        Logger.debug("Unable to inflate pending block: " + pendingBlock.getBlockHash());
                        return;
                    }

                    final MutableUnspentTransactionOutputSet unspentTransactionOutputSet;
                    if ( shouldLoadUnspentOutputs && (blockHeight != null) ) {
                        unspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                        unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight);
                        Logger.trace("Loaded UTXOs for " + blockHeight);
                    }
                    else { // NOTE: Outputs are available upon demand via LazyLoading.
                        unspentTransactionOutputSet = new LazyMutableUnspentTransactionOutputSet(databaseManagerFactory);
                        unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, block, blockHeight); // Operation is only executed on demand, including blockHeight lookup if null...
                        Logger.trace("Lazy-loading UTXOs for " + block.getHash() + "(" + blockHeight + ")");
                    }

                    pendingBlockFuture.setLoadedPendingBlock(blockHeight, pendingBlock, unspentTransactionOutputSet);

                    milliTimer.stop();
                    Logger.trace("Preloaded block " + blockHash + " in: " + milliTimer.getMillisecondsElapsed() + "ms.");
                }
                catch (final DatabaseException exception) {
                    pendingBlockFuture.setLoadedPendingBlock(null, null, null);
                    Logger.debug(exception);
                }
            }
        });
        return pendingBlockFuture;
    }

    protected Long _getBlockHeight(final Sha256Hash blockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        return blockHeaderDatabaseManager.getBlockHeight(blockId);
    }

    protected Boolean _shouldLoadUnspentOutputs(final Long blockHeight) {
        final long loadUnspentOutputsAfterBlockHeight = Util.coalesce(_loadUnspentOutputsAfterBlockHeight, 0L);
        if (loadUnspentOutputsAfterBlockHeight < 1L) {
            return true;
        }

        return (Util.coalesce(blockHeight, Long.MAX_VALUE) > loadUnspentOutputsAfterBlockHeight);
    }

    /**
     * QueueCount must be greater than zero.
     */
    public PendingBlockLoader(final Context context, final Integer queueCount) {
        _context = context;
        _pendingBlockFutures = new CircleBuffer<PendingBlockFuture>(queueCount);
    }

    public synchronized PreloadedPendingBlock getBlock(final Sha256Hash blockHash, final PendingBlockId nullablePendingBlockId) {
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        // Find the requested block in the queue, emptying/discarding the queue until a match is found.
        PendingBlockFuture requestedBlockFuture = null;
        while (_pendingBlockFutures.getCount() > 0) {
            final PendingBlockFuture pendingBlockFuture = _pendingBlockFutures.pop();
            if (Util.areEqual(blockHash, pendingBlockFuture.getBlockHash())) {
                requestedBlockFuture = pendingBlockFuture;
                break;
            }

            Logger.trace("Discarding preloaded block: " + pendingBlockFuture.getBlockHash());
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            final Long blockHeight = _getBlockHeight(blockHash, databaseManager);

            // If a matching block wasn't found, then load the requested block.
            final Boolean shouldLoadRequestedBlockUnspentOutputs = _shouldLoadUnspentOutputs(blockHeight);
            if (requestedBlockFuture == null) {
                final PendingBlockId pendingBlockId = (nullablePendingBlockId != null ? nullablePendingBlockId : pendingBlockDatabaseManager.getPendingBlockId(blockHash));
                requestedBlockFuture = _asynchronouslyLoadNextPendingBlock(blockHash, pendingBlockId, blockHeight, shouldLoadRequestedBlockUnspentOutputs);
            }

            if (shouldLoadRequestedBlockUnspentOutputs) { // Load the next n-blocks after the requested block, as long as they aren't contentious...  If the requested block's outputs weren't loaded then don't preload next blocks since its benefit is limited...
                Sha256Hash nextBlockHash = blockHash;
                final int allowedNewFutureCount = (_pendingBlockFutures.getMaxCount() - _pendingBlockFutures.getCount());
                int newFutureCount = 0;
                final boolean queueHasBeenDrained = (_pendingBlockFutures.getCount() == 0); // Only queue up additional items once the full queue has been drained in order to prevent read/write contention.
                while (queueHasBeenDrained && (newFutureCount < allowedNewFutureCount)) {
                    final Long futureBlockHeight = (blockHeight != null ? (blockHeight + 1L + newFutureCount) : null);
                    final List<PendingBlockId> nextPendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(nextBlockHash);
                    if (nextPendingBlockIds.getCount() != 1) { break; } // If the next block is contentious then abort.

                    final PendingBlockId nextPendingBlockId = nextPendingBlockIds.get(0);
                    nextBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(nextPendingBlockId);

                    final Boolean hasBlockData = pendingBlockDatabaseManager.hasBlockData(nextPendingBlockId);
                    if (! hasBlockData) { break; }

                    { // Skip if the block is already within the buffer...
                        boolean futureExists = false;
                        for (final PendingBlockFuture pendingBlockFuture : _pendingBlockFutures) {
                            if (Util.areEqual(nextBlockHash, pendingBlockFuture.getBlockHash())) {
                                futureExists = true;
                                break;
                            }
                        }
                        if (futureExists) { continue; }
                    }

                    final Boolean shouldLoadUnspentOutputs = _shouldLoadUnspentOutputs(futureBlockHeight);
                    if (! shouldLoadUnspentOutputs) {
                        newFutureCount += 1; // Still increment the newFutureCount in order to prevent looping through too many future blocks...
                        continue;
                    }

                    final PendingBlockFuture nextBlockFuture = _asynchronouslyLoadNextPendingBlock(nextBlockHash, nextPendingBlockId, futureBlockHeight, true);

                    { // Add the queued blocks as dependents for the pending block's output set.
                        nextBlockFuture.addPredecessorBlock(requestedBlockFuture);
                        for (final PendingBlockFuture predecessorBlock : _pendingBlockFutures) {
                            nextBlockFuture.addPredecessorBlock(predecessorBlock);
                        }
                    }

                    _pendingBlockFutures.push(nextBlockFuture);
                    newFutureCount += 1;
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }

        if (requestedBlockFuture == null) {
            return null;
        }

        try {
            final Long timeout = 1000L; // TimeUnit.MINUTES.toMillis(3L);
            final boolean timedOut = (! requestedBlockFuture.waitFor(timeout));
            if (timedOut) { return null; }

            return requestedBlockFuture;
        }
        catch (final Exception exception) {
            return null;
        }
    }

    public synchronized void invalidateUtxoSets() {
        for (final PendingBlockFuture pendingBlockFuture : _pendingBlockFutures) {
            pendingBlockFuture.invalidateUnspentTransactionOutputSet();
        }
    }

    public void setLoadUnspentOutputsAfterBlockHeight(final Long blockHeight) {
        _loadUnspentOutputsAfterBlockHeight = blockHeight;
    }
}

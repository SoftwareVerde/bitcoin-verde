package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.transaction.validator.MutableUnspentTransactionOutputSet;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

public class BlockLoader {
    protected final BlockInflaters _blockInflaters;
    protected final ThreadPool _threadPool;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final CircleBuffer<PendingBlockFuture> _pendingBlockFutures;
    protected Long _loadUnspentOutputsAfterBlockHeight = null;

    /**
     * Preloads the block, specified by the nextPendingBlockId, and the unspentOutputs it requires.
     * When complete, the pin is released.
     */
    protected PendingBlockFuture _asynchronouslyLoadNextPendingBlock(final Sha256Hash blockHash, final PendingBlockId nextPendingBlockId, final Boolean shouldLoadUnspentOutputs) {
        final BlockInflater blockInflater = _blockInflaters.getBlockInflater();

        final PendingBlockFuture blockFuture = new PendingBlockFuture(blockHash, _blockInflaters.getBlockInflater());
        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

                    final MilliTimer milliTimer = new MilliTimer();
                    milliTimer.start();

                    final PendingBlock pendingBlock = pendingBlockDatabaseManager.getPendingBlock(nextPendingBlockId);
                    if (pendingBlock == null) {
                        Logger.debug("Unable to load pending block: " + nextPendingBlockId, new Exception());
                        return;
                    }

                    final Block nextBlock = pendingBlock.inflateBlock(blockInflater);
                    if (nextBlock == null) {
                        Logger.debug("Unable to inflate pending block: " + pendingBlock.getBlockHash(), new Exception());
                        return;
                    }

                    blockFuture._pendingBlock = pendingBlock;

                    if (shouldLoadUnspentOutputs) {
                        final MutableUnspentTransactionOutputSet unspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                        unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, nextBlock);
                        blockFuture._unspentTransactionOutputSet = unspentTransactionOutputSet;
                    }

                    milliTimer.stop();
                    Logger.trace("Preloaded block " + blockHash + " in: " + milliTimer.getMillisecondsElapsed() + "ms.");
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                }
                finally {
                    blockFuture._pin.release();
                }
            }
        });
        return blockFuture;
    }

    protected Boolean _shouldLoadUnspentOutputs(final Sha256Hash blockHash, final DatabaseManager databaseManager) throws DatabaseException {
        final long loadUnspentOutputsAfterBlockHeight = Util.coalesce(_loadUnspentOutputsAfterBlockHeight, 0L);
        if (loadUnspentOutputsAfterBlockHeight < 1L) {
            return true;
        }

        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        return (Util.coalesce(blockHeight, Long.MAX_VALUE) > loadUnspentOutputsAfterBlockHeight);
    }

    public BlockLoader(final Integer queueCount, final FullNodeDatabaseManagerFactory databaseManagerFactory, final ThreadPool threadPool, final BlockInflaters blockInflaters) {
        _blockInflaters = blockInflaters;
        _threadPool = threadPool;
        _databaseManagerFactory = databaseManagerFactory;
        _pendingBlockFutures = new CircleBuffer<PendingBlockFuture>(queueCount);
    }

    public synchronized PreloadedPendingBlock getBlock(final Sha256Hash blockHash, final PendingBlockId nullablePendingBlockId) {
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

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            // If a matching block wasn't found, then load the requested block.
            final Boolean shouldLoadRequestedBlockUnspentOutputs = _shouldLoadUnspentOutputs(blockHash, databaseManager);
            if (requestedBlockFuture == null) {
                final PendingBlockId pendingBlockId = (nullablePendingBlockId != null ? nullablePendingBlockId : pendingBlockDatabaseManager.getPendingBlockId(blockHash));
                requestedBlockFuture = _asynchronouslyLoadNextPendingBlock(blockHash, pendingBlockId, shouldLoadRequestedBlockUnspentOutputs);
            }

            if (shouldLoadRequestedBlockUnspentOutputs) { // Load the next n-blocks after the requested block, as long as they aren't contentious...  If the requested block's outputs weren't loaded then don't preload next blocks since its benefit is limited...
                Sha256Hash nextBlockHash = blockHash;
                final int allowedNewFutureCount = (_pendingBlockFutures.getMaxCount() - _pendingBlockFutures.getCount());
                int newFutureCount = 0;
                while (newFutureCount < allowedNewFutureCount) {
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

                    final Boolean shouldLoadUnspentOutputs = _shouldLoadUnspentOutputs(blockHash, databaseManager);
                    if (! shouldLoadUnspentOutputs) {
                        newFutureCount += 1; // Still increment the newFutureCount in order to prevent looping through too many future blocks...
                        continue;
                    }

                    final PendingBlockFuture nextBlockFuture = _asynchronouslyLoadNextPendingBlock(nextBlockHash, nextPendingBlockId, true);

                    { // Add the queued blocks as dependents for the pending block's output set.
                        nextBlockFuture._predecessorBlocks.addLast(requestedBlockFuture);
                        for (final PendingBlockFuture predecessorBlocks : _pendingBlockFutures) {
                            nextBlockFuture._predecessorBlocks.addLast(predecessorBlocks);
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

        requestedBlockFuture.waitFor();
        return requestedBlockFuture;
    }

    public void setLoadUnspentOutputsAfterBlockHeight(final Long blockHeight) {
        _loadUnspentOutputsAfterBlockHeight = blockHeight;
    }
}

package com.softwareverde.bitcoin.server.module.node.sync;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlock;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.transaction.validator.MutableUnspentTransactionOutputSet;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.CircleBuffer;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;

import java.util.concurrent.ConcurrentLinkedDeque;

public class BlockLoader {

    public interface PreloadedPendingBlock {
        PendingBlock getPendingBlock();
        MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet();
    }

    public static class PendingBlockFuture implements PreloadedPendingBlock {
        protected final BlockInflater _blockInflater;
        protected final Pin _pin;
        protected final Sha256Hash _blockHash;
        protected final ConcurrentLinkedDeque<PendingBlockFuture> _predecessorBlocks = new ConcurrentLinkedDeque<PendingBlockFuture>();

        protected volatile PendingBlock _pendingBlock;
        protected volatile MutableUnspentTransactionOutputSet _unspentTransactionOutputSet;

        public PendingBlockFuture(final Sha256Hash blockHash) {
            this(blockHash, null);
        }

        public PendingBlockFuture(final Sha256Hash blockHash, final BlockInflater blockInflater) {
            _pin = new Pin();
            _blockHash = blockHash;
            _blockInflater = blockInflater;

            _pendingBlock = null;
            _unspentTransactionOutputSet = null;
        }

        public Sha256Hash getBlockHash() {
            return _blockHash;
        }

        /**
         * Returns the PendingBlock once it has been loaded, or null if it has not been loaded yet.
         */
        @Override
        public PendingBlock getPendingBlock() {
            if (! _pin.wasReleased()) {
                Logger.debug("Attempted to get block on unreleased pending block.", new Exception());
                return null;
            }

            return _pendingBlock;
        }

        /**
         * Returns the TransactionOutputSet for the PendingBlock once it has been loaded, or null if it has not been loaded yet.
         */
        @Override
        public MutableUnspentTransactionOutputSet getUnspentTransactionOutputSet() {
            if (! _pin.wasReleased()) { return null; }
            if (_unspentTransactionOutputSet == null) { return null; } // _unspentTransactionOutputSet may be set to null for blocks skipping validation...

            // Update the UnspentTransactionOutputSet with the previously cached blocks...
            //  The previous Blocks that weren't processed at the time of the initial load are loaded into the UnspentTransactionOutputSet.
            while (! _predecessorBlocks.isEmpty()) {
                final PendingBlockFuture pendingBlockFuture = _predecessorBlocks.removeFirst();
                // Logger.trace(_blockHash + " outputs are being updated with " + pendingBlockFuture.getBlockHash() + " outputs.");
                pendingBlockFuture.waitFor();

                final PendingBlock pendingBlock = pendingBlockFuture.getPendingBlock();
                if (pendingBlock == null) {
                    Logger.debug(_blockHash + " was unable to get a predecessor block.");
                    return null;
                }

                final Block previousBlock;
                {
                    final Block inflatedBlock = pendingBlock.getInflatedBlock();
                    if (inflatedBlock != null) {
                        previousBlock = inflatedBlock;
                    }
                    else {
                        if (_blockInflater == null) {
                            Logger.debug("No BlockInflater found. " + _blockHash + " was unable to get a predecessor block.");
                            return null;
                        }
                        previousBlock = pendingBlock.inflateBlock(_blockInflater);
                    }
                }

                // Logger.trace("Updating " + _blockHash + " with outputs from " + previousBlock.getHash());
                _unspentTransactionOutputSet.update(previousBlock);
            }

            return _unspentTransactionOutputSet;
        }

        /**
         * Blocks until the PendingBlock and its TransactionOutputSet have been loaded.
         */
        public void waitFor() {
            _pin.waitForRelease();
        }
    }

    protected final BlockInflaters _blockInflaters;
    protected final ThreadPool _threadPool;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final CircleBuffer<PendingBlockFuture> _pendingBlockFutures;
    protected Long _loadUnspentOutputsAfterBlockHeight = null;

    /**
     * Preloads the block, specified by the nextPendingBlockId, and the unspentOutputs it requires.
     * When complete, the pin is released.
     */
    protected PendingBlockFuture _asynchronouslyLoadNextPendingBlock(final Sha256Hash blockHash, final PendingBlockId nextPendingBlockId) {
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

                    final boolean shouldLoadUnspentOutputs;
                    {
                        if (_loadUnspentOutputsAfterBlockHeight == null) {
                            shouldLoadUnspentOutputs = true;
                        }
                        else {
                            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                            final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                            final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
                            shouldLoadUnspentOutputs = (Util.coalesce(blockHeight, Long.MAX_VALUE) > _loadUnspentOutputsAfterBlockHeight);
                        }
                    }

                    if (shouldLoadUnspentOutputs) {
                        final MutableUnspentTransactionOutputSet unspentTransactionOutputSet = new MutableUnspentTransactionOutputSet();
                        unspentTransactionOutputSet.loadOutputsForBlock(databaseManager, nextBlock);
                        blockFuture._unspentTransactionOutputSet = unspentTransactionOutputSet;
                    }

                    milliTimer.stop();
                    Logger.trace("Pre-loaded block " + blockHash + " in: " + milliTimer.getMillisecondsElapsed() + "ms.");
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
            if (requestedBlockFuture == null) {
                final PendingBlockId pendingBlockId = (nullablePendingBlockId != null ? nullablePendingBlockId : pendingBlockDatabaseManager.getPendingBlockId(blockHash));
                requestedBlockFuture = _asynchronouslyLoadNextPendingBlock(blockHash, pendingBlockId);
            }

            { // Load the next n-blocks after the requested block, as long as they aren't contentious...
                Sha256Hash nextBlockHash = blockHash;
                final int allowedNewFutureCount = (_pendingBlockFutures.getMaxCount() - _pendingBlockFutures.getCount());
                int newFutureCount = 0;
                while (newFutureCount < allowedNewFutureCount) {
                    final List<PendingBlockId> nextPendingBlockIds = pendingBlockDatabaseManager.getPendingBlockIdsWithPreviousBlockHash(nextBlockHash);
                    if (nextPendingBlockIds.getCount() != 1) { break; } // If the next block is contentious then abort.

                    final PendingBlockId nextPendingBlockId = nextPendingBlockIds.get(0);
                    nextBlockHash = pendingBlockDatabaseManager.getPendingBlockHash(nextPendingBlockId);

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

                    Logger.trace("Preloading Block: " + nextBlockHash);
                    final PendingBlockFuture nextBlockFuture = _asynchronouslyLoadNextPendingBlock(nextBlockHash, nextPendingBlockId);

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
        Logger.trace("Waited for: " + requestedBlockFuture.getBlockHash());
        return requestedBlockFuture;
    }

    public void setLoadUnspentOutputsAfterBlockHeight(final Long blockHeight) {
        _loadUnspentOutputsAfterBlockHeight = blockHeight;
    }
}

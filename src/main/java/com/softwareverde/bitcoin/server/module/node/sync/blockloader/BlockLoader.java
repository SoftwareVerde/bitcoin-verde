package com.softwareverde.bitcoin.server.module.node.sync.blockloader;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.module.node.database.block.fullnode.FullNodeBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BlockLoader {
    protected final ThreadPool _threadPool;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BlockchainSegmentId _blockchainSegmentId;
    protected final HashMap<Long, BlockFuture> _blockFutures;
    protected final Integer _maxQueueCount;
    protected Long _nextBlockHeight;

    /**
     * Preloads the block, specified by the nextPendingBlockId, and the unspentOutputs it requires.
     * When complete, the pin is released.
     */
    protected BlockFuture _asynchronouslyLoadNextBlock(final Long blockHeight) {
        final BlockFuture blockFuture = new BlockFuture(blockHeight);
        _threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
                    final FullNodeBlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();

                    final MilliTimer milliTimer = new MilliTimer();
                    milliTimer.start();

                    // Expensive relative to the other actions during the UTXO loading...
                    final BlockId blockId = blockHeaderDatabaseManager.getBlockIdAtHeight(_blockchainSegmentId, blockHeight);
                    blockFuture._block = blockDatabaseManager.getBlock(blockId);

                    milliTimer.stop();
                    Logger.trace("Preloaded block#" + blockHeight + " in: " + milliTimer.getMillisecondsElapsed() + "ms.");
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

    public BlockLoader(final BlockchainSegmentId blockchainSegmentId, final Integer queueCount, final FullNodeDatabaseManagerFactory databaseManagerFactory, final ThreadPool threadPool) {
        _blockchainSegmentId = blockchainSegmentId;
        _threadPool = threadPool;
        _databaseManagerFactory = databaseManagerFactory;
        _blockFutures = new HashMap<Long, BlockFuture>(queueCount);
        _maxQueueCount = queueCount;
        _nextBlockHeight = 0L;
    }

    public synchronized PreloadedBlock getBlock(final Long blockHeight) {
        while (_blockFutures.size() < _maxQueueCount) {
            _nextBlockHeight = Math.max(_nextBlockHeight, blockHeight);
            final BlockFuture blockFuture = _asynchronouslyLoadNextBlock(_nextBlockHeight);
            _blockFutures.put(_nextBlockHeight, blockFuture);
            _nextBlockHeight += 1L;
        }

        { // Check for already preloadedBlock...
            final BlockFuture blockFuture = _blockFutures.remove(blockHeight);
            if (blockFuture != null) {
                blockFuture.waitFor();
                return blockFuture;
            }
        }

        // If the requested block is out of range of the queue then load it outside of the regular process...
        final BlockFuture blockFuture = _asynchronouslyLoadNextBlock(blockHeight);
        blockFuture.waitFor();
        return blockFuture;
    }
}
package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.map.mutable.MutableHashMap;
import com.softwareverde.constable.map.mutable.MutableMap;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.filedb.WorkerManager;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.TimedPromise;
import com.softwareverde.util.Tuple;

import java.util.concurrent.atomic.AtomicBoolean;

public class PendingBlockQueue {
    public interface BitcoinNodeSelector {
        BitcoinNode getBitcoinNode(Long blockHeight);
    }

    protected final double _minMegabitsPerSecond = 15.0D;

    protected final Blockchain _blockchain;
    protected final PendingBlockStore _blockStore;
    protected final WorkerManager _blockLoader;
    protected final MutableHashMap<Long, TimedPromise<Block>> _requests = new MutableHashMap<>();
    protected final Long _requestTimeout = 10000L;
    protected final int _queueDepth = 20;
    protected Thread _thread;
    protected final AtomicBoolean _isShutdown = new AtomicBoolean(true);
    protected final BitcoinNodeSelector _nodeSelector;

    protected double _calculateMegabitsPerSecond(final TimedPromise<Block> promise) {
        final Block block = promise.pollResult();
        if (block == null) { return 0F; }

        final double ms = promise.getMsElapsed();
        final long byteCount = block.getByteCount();
        return (byteCount * 8D) / ms;
    }

    protected TimedPromise<Block> _createPromise(final Long blockHeight, final Sha256Hash blockHash) {
        final TimedPromise<Block> promise = new TimedPromise<>();
        _requests.put(blockHeight, promise);

        final BitcoinNode bitcoinNode = _nodeSelector.getBitcoinNode(blockHeight);
        if (bitcoinNode == null) {
            promise.setResult(null);
            return promise;
        }

        Logger.debug("Requested " + blockHash + " from " + bitcoinNode + ".");
        bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                Logger.debug("Downloaded " + blockHash + " from " + bitcoinNode + " in " + promise.getMsElapsed()  + "ms.");
                promise.setResult(block);

                final double mbps = _calculateMegabitsPerSecond(promise);
                final boolean wasSlowDownload = (mbps < _minMegabitsPerSecond);
                if (wasSlowDownload) {
                    Logger.debug("Disconnecting from slow peer (" + mbps + "mbps).");
                    bitcoinNode.disconnect();
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                Logger.debug("Failed to download " + blockHash + " from " + bitcoinNode + " in " + promise.getMsElapsed()  + "ms.");
                promise.setResult(null);
                bitcoinNode.disconnect();
            }
        }, RequestPriority.NORMAL);

        return promise;
    }

    protected void _createExistingBlockPromise(final Long blockHeight, final Sha256Hash blockHash) {
        if (_isShutdown.get()) { return; }

        final TimedPromise<Block> promise = new TimedPromise<>();
        _requests.put(blockHeight, promise);

        _blockLoader.submitTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_isShutdown.get()) { return; }

                final BlockInflater blockInflater = new BlockInflater();
                final ByteArray blockData = _blockStore.getPendingBlockData(blockHash);
                final Block block = blockInflater.fromBytes(blockData);
                promise.setResult(block);
            }
        });
    }

    protected void _createRequests(final Long headBlockHeight) {
        for (int i = 0; i < _queueDepth; ++i) {
            final Long blockHeight = headBlockHeight + 1L + i;
            final TimedPromise<Block> promise = _requests.get(blockHeight);
            if ( (promise != null) && (! promise.isComplete()) ) { continue; } // Request is still executing.

            final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
            if (blockHeader == null) { break; }

            final Sha256Hash blockHash = blockHeader.getHash();
            final boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
            if (pendingBlockExists) {
                _createExistingBlockPromise(blockHeight, blockHash);
            }
            else {
                _createPromise(blockHeight, blockHash);
            }
        }
    }

    public PendingBlockQueue(final Blockchain blockchain, final PendingBlockStore blockStore, final BitcoinNodeSelector nodeSelector) {
        _blockchain = blockchain;
        _blockStore = blockStore;
        _nodeSelector = nodeSelector;

        _blockLoader = new WorkerManager(2, 256);
        _blockLoader.setName("Block Loader");
    }

    public void start() {
        if (! _isShutdown.compareAndSet(true, false)) { return; }

        _blockLoader.start();

        _thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.debug("PendingBlockQueue started.");
                try {
                    while (! _isShutdown.get()) {
                        final Long headBlockHeight = _blockchain.getHeadBlockHeight();
                        synchronized (_requests) {
                            // Delete old and/or expired requests.
                            _requests.mutableVisit(new MutableMap.MutableVisitor<>() {
                                @Override
                                public boolean run(final Tuple<Long, TimedPromise<Block>> entry) {
                                    final Long blockHeight = entry.first;
                                    if (blockHeight <= headBlockHeight) {
                                        entry.first = null; // Delete entry.
                                        return true;
                                    }

                                    final TimedPromise<Block> promise = entry.second;
                                    if ( (! promise.isComplete()) && (promise.getMsElapsed() > _requestTimeout) ) {
                                        Logger.debug("Download of " + blockHeight + " failed after " + promise.getMsElapsed()  + "ms.");
                                        promise.setResult(null);
                                        entry.first = null; // Delete entry.
                                        return true;
                                    }

                                    return true;
                                }
                            });

                            _createRequests(headBlockHeight);
                        }

                        Thread.sleep(_requestTimeout / 2L);
                    }
                }
                catch (final Exception exception) {
                    Logger.debug(exception);
                }
                finally {
                    _isShutdown.set(true);
                    Logger.debug("PendingBlockQueue shutdown.");
                }
            }
        });
        _thread.setDaemon(true);
        _thread.setName("PendingBlockQueue");
        _thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
        _thread.start();
    }

    public TimedPromise<Block> getBlock(final Long blockHeight) {
        synchronized (_requests) {
            final TimedPromise<Block> existingPromise = _requests.get(blockHeight);
            if (existingPromise != null) {
                return existingPromise;
            }

            final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
            if (blockHeader == null) {
                return new TimedPromise<>(null);
            }

            final Sha256Hash blockHash = blockHeader.getHash();
            return _createPromise(blockHeight, blockHash);
        }
    }

    public void addBlock(final Long blockHeight, final TimedPromise<Block> promise) {
        synchronized (_requests) {
            _requests.put(blockHeight, promise);
        }
    }

    public void onBlockProcessed() {
        if (_isShutdown.get()) { return; }

        final Long headBlockHeight = _blockchain.getHeadBlockHeight();
        synchronized (_requests) {
            _createRequests(headBlockHeight);
        }
    }

    public void stop() throws Exception {
        if (! _isShutdown.compareAndSet(false, true)) { return; }

        _blockLoader.close();

        final Thread thread = _thread;
        if (thread != null) {
            thread.join();
        }
    }
}

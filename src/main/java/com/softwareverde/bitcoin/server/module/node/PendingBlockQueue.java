package com.softwareverde.bitcoin.server.module.node;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.bitcoin.server.node.request.UnfulfilledSha256HashRequest;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableArrayList;
import com.softwareverde.constable.list.mutable.MutableList;
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

    protected final MutableHashMap<RequestId, BlockRequest> _pendingRequests = new MutableHashMap<>(0);

    public static class BlockRequest extends UnfulfilledSha256HashRequest {
        public final AtomicBoolean isComplete = new AtomicBoolean(false);
        public BlockRequest(final BitcoinNode bitcoinNode, final RequestId requestId, final RequestPriority requestPriority, final Sha256Hash hash) {
            super(bitcoinNode, requestId, requestPriority, hash);
        }
    }

    protected double _calculateMegabitsPerSecond(final TimedPromise<Block> promise) {
        final Block block = promise.pollResult();
        if (block == null) { return 0D; }

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
        final RequestId requestId = bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                Logger.debug("Downloaded " + blockHash + " from " + bitcoinNode + " in " + promise.getMsElapsed()  + "ms.");
                promise.setResult(block);

                _blockStore.storePendingBlock(block);

                final BlockRequest blockRequest = _pendingRequests.get(requestId);
                if (blockRequest != null) {
                    blockRequest.isComplete.set(true);
                }

                final double mbps = _calculateMegabitsPerSecond(promise);
                final boolean wasSlowDownload = (mbps < _minMegabitsPerSecond);
                if (wasSlowDownload) {
                    Logger.debug("Slow peer (" + mbps + "mbps) detected: " + bitcoinNode);
                    // bitcoinNode.disconnect();
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                Logger.debug("Failed to download " + blockHash + " from " + bitcoinNode + " in " + promise.getMsElapsed()  + "ms.");
                promise.setResult(null);

                final BlockRequest blockRequest = _pendingRequests.get(requestId);
                if (blockRequest != null) {
                    blockRequest.isComplete.set(true);
                }

                // bitcoinNode.disconnect();
            }
        }, RequestPriority.NORMAL);

        _pendingRequests.put(requestId, new BlockRequest(bitcoinNode, requestId, RequestPriority.NORMAL, blockHash));

        return promise;
    }

    protected TimedPromise<Block> _createExistingBlockPromise(final Long blockHeight, final Sha256Hash blockHash) {
        if (_isShutdown.get()) { return new TimedPromise<>(null); }

        final TimedPromise<Block> promise = new TimedPromise<>();
        _requests.put(blockHeight, promise);

        _blockLoader.submitTask(new WorkerManager.Task() {
            @Override
            public void run() {
                if (_isShutdown.get()) { return; }

                final BlockInflater blockInflater = new BlockInflater();
                final ByteArray blockData = _blockStore.getPendingBlockData(blockHash);
                if (blockData == null) {
                    promise.setException(new Exception("BlockStore did not contain data: " + blockHash));
                    promise.setResult(null);
                    return;
                }

                final Block block = blockInflater.fromBytes(blockData);
                promise.setResult(block);
            }
        });
        return promise;
    }

    protected final Object _headBlockHeightLock = new Object();
    protected Long _headBlockHeight = null;
    protected Long _headRequestedBlockHeight = 0L;
    protected void _createRequests() {
        synchronized (_headBlockHeightLock) {
            if (_headBlockHeight == null) {
                _headBlockHeight = _blockchain.getHeadBlockHeight();
                if (_headBlockHeight == null) { return; }
            }

            int newPromiseCount = 0;
            do {
                for (int i = 0; i < _queueDepth; ++i) {
                    final Long blockHeight = _headBlockHeight + 1L + i;
                    final TimedPromise<Block> promise = _requests.get(blockHeight);
                    if ((promise != null) && (! promise.isComplete())) { continue; } // Request is still executing.

                    final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
                    if (blockHeader == null) { break; }

                    final Sha256Hash blockHash = blockHeader.getHash();
                    final boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
                    if (pendingBlockExists) {
                        _createExistingBlockPromise(blockHeight, blockHash);
                        _headRequestedBlockHeight = Math.max(_headRequestedBlockHeight, blockHeight);
                    }
                    else {
                        Logger.debug("_createPromise: " + _headRequestedBlockHeight + " " + _headBlockHeight);
                        _createPromise(blockHeight, blockHash);
                        _headRequestedBlockHeight = Math.max(_headRequestedBlockHeight, blockHeight);
                    }
                    newPromiseCount += 1;
                }

                if (newPromiseCount == 0) {
                    if (_requests.isEmpty()) {
                        _headBlockHeight = Math.max(_headRequestedBlockHeight, _headBlockHeight);
                    }
                    else { break; }
                }
            } while (newPromiseCount == 0);
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

                            _createRequests();

                            _pendingRequests.mutableVisit(new MutableMap.MutableVisitor<>() {
                                @Override
                                public boolean run(final Tuple<RequestId, BlockRequest> entry) {
                                    final UnfulfilledSha256HashRequest request = entry.second;
                                    final Sha256Hash blockHash = request.hash;
                                    final Long blockHeight = _blockchain.getBlockHeight(blockHash);
                                    if (entry.second.isComplete.get() || (! _requests.containsKey(blockHeight))) {
                                        entry.first = null; // Delete entry.
                                    }

                                    return true;
                                }
                            });
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

    public void removeBlock(final Long blockHeight) {
        synchronized (_requests) {
            final TimedPromise<Block> existingPromise = _requests.remove(blockHeight);
            if (existingPromise != null) {
                existingPromise.setResult(null);
            }
        }
    }

    public void purgeBlock(final Long blockHeight) {
        final BlockHeader blockHeader = _blockchain.getBlockHeader(blockHeight);
        if (blockHeader == null) { return; }

        final Sha256Hash blockHash = blockHeader.getHash();
        _blockStore.removePendingBlock(blockHash);
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
            final boolean pendingBlockExists = _blockStore.pendingBlockExists(blockHash);
            if (pendingBlockExists) {
                return _createExistingBlockPromise(blockHeight, blockHash);
            }
            else {
                Logger.debug("_createPromise");
                return _createPromise(blockHeight, blockHash);
            }
        }
    }

    public void addBlock(final Long blockHeight, final TimedPromise<Block> promise) {
        synchronized (_requests) {
            _requests.put(blockHeight, promise);
        }
    }

    public void onBlockProcessed() {
        if (_isShutdown.get()) { return; }

        synchronized (_requests) {
            _createRequests();
        }
    }

    public List<UnfulfilledSha256HashRequest> getPendingDownloads() {
        synchronized (_requests) {
            final MutableList<UnfulfilledSha256HashRequest> requests = new MutableArrayList<>();
            for (final BlockRequest blockRequest : _pendingRequests.getValues()) {
                if (blockRequest.isComplete.get()) { continue; }
                requests.add(blockRequest);
            }
            return requests;
        }
    }

    public void stop() throws Exception {
        if (! _isShutdown.compareAndSet(false, true)) { return; }

        _blockLoader.close(3000L);

        final Thread thread = _thread;
        if (thread != null) {
            thread.join(1000);
        }
    }
}

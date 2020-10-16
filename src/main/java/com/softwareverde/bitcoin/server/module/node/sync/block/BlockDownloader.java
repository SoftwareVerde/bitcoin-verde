package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.BlockInflater;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.PendingBlockStoreContext;
import com.softwareverde.bitcoin.context.SynchronizationStatusContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.context.ThreadPoolContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.message.type.node.feature.NodeFeatures;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.p2p.node.manager.NodeManager;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BlockDownloader extends SleepyService {
    public interface Context extends BlockInflaters, NodeManagerContext, MultiConnectionFullDatabaseContext, PendingBlockStoreContext, SynchronizationStatusContext, SystemTimeContext, ThreadPoolContext { }

    public static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    protected static class CurrentDownload {
        public final NodeId nodeId;
        public final MilliTimer milliTimer;

        public CurrentDownload(final NodeId nodeId, final MilliTimer milliTimer) {
            this.nodeId = nodeId;
            this.milliTimer = milliTimer;
        }
    }

    protected final Context _context;

    protected final ConcurrentHashMap<Sha256Hash, CurrentDownload> _currentBlockDownloadSet = new ConcurrentHashMap<Sha256Hash, CurrentDownload>(12);

    protected Runnable _newBlockAvailableCallback = null;

    protected Long _lastBlockfinderTime = null;

    protected Boolean _hasGenesisBlock = false;
    protected Long _lastGenesisDownloadTimestamp = null;

    protected Thread _unsynchronizedWatcher = null;

    protected final Integer _historicThroughputMaxItemCount = 256;
    protected final AtomicLong _totalDownloadCount = new AtomicLong(0L);
    protected final AtomicLong _totalBytesDownloaded = new AtomicLong(0L);
    protected final RotatingQueue<Long> _historicBytesPerSecond = new RotatingQueue<Long>(_historicThroughputMaxItemCount);
    protected Long _cachedBytesPerSecond = null;

    protected void _calculateThroughput() {
        int i = 0;
        long sum = 0L;
        for (final Long bytesPerSecond : _historicBytesPerSecond) {
            sum += bytesPerSecond;
            i += 1;
        }
        if (i == 0) { return; }

        _cachedBytesPerSecond = (sum / i);
    }

    protected Long _calculateTimeout() {
        final long maxTimeoutMs = (5L * 60000L);
        final long defaultTimeoutMs = 60000L;
        final long minTimeoutMs = 10000L;
        final Long downloadSpeedInBytesPerSecond = _cachedBytesPerSecond;
        if (downloadSpeedInBytesPerSecond == null) {
            return defaultTimeoutMs;
        }

        final long bufferFactor = 2L;
        final long maxBlockByteCount = BlockInflater.MAX_BYTE_COUNT;
        final long minSecondsRequired = (maxBlockByteCount / downloadSpeedInBytesPerSecond);
        final long bufferedMinSecondsRequired = (minSecondsRequired * bufferFactor);

        if (bufferedMinSecondsRequired > maxTimeoutMs) {
            return maxTimeoutMs;
        }

        if (bufferedMinSecondsRequired < minTimeoutMs) {
            return minTimeoutMs;
        }

        return bufferedMinSecondsRequired;
    }

    protected void _storePendingBlock(final Block block, final FullNodeDatabaseManager databaseManager) {
        try {
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
            pendingBlockDatabaseManager.storeBlock(block);
        }
        catch (final DatabaseException exception) {
            Logger.debug(exception);
        }
    }

    protected void _markPendingBlockIdsAsFailed(final Set<Sha256Hash> pendingBlockHashes) {
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            for (final Sha256Hash pendingBlockHash : pendingBlockHashes) {
                final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(pendingBlockHash);
                if (pendingBlockId == null) { continue; }

                pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
            }
            pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    // This function iterates through each Block in-flight, and checks for items that have exceeded the MAX_TIMEOUT.
    //  Items exceeding the timeout have their onFailure method called.
    //  This function should not be necessary, and is a work-around for a bug within the NodeManager that is causing onFailure to not be triggered.
    protected void _checkForStalledDownloads() {
        final Long maxRequestDurationInMilliseconds = _calculateTimeout();
        Logger.trace("Max download request duration: " + maxRequestDurationInMilliseconds + "ms");

        final MutableList<Sha256Hash> stalledBlockHashes = new MutableList<Sha256Hash>();
        for (final Sha256Hash blockHash : _currentBlockDownloadSet.keySet()) {
            final CurrentDownload currentDownload = _currentBlockDownloadSet.get(blockHash);
            final MilliTimer milliTimer = currentDownload.milliTimer;
            if (milliTimer == null) {
                stalledBlockHashes.add(blockHash);
                continue;
            }

            milliTimer.stop();
            final Long msElapsed = milliTimer.getMillisecondsElapsed();
            if (msElapsed >= maxRequestDurationInMilliseconds) {
                stalledBlockHashes.add(blockHash);
            }
        }

        for (final Sha256Hash stalledBlockHash : stalledBlockHashes) {
            Logger.warn("Stalled Block Detected: " + stalledBlockHash);
            _currentBlockDownloadSet.remove(stalledBlockHash);
        }
    }

    protected void _downloadBlock(final Sha256Hash blockHash, final BitcoinNode bitcoinNode, final CurrentDownload currentDownload) {
        Logger.trace("Downloading " + blockHash + " from " + (bitcoinNode != null ? bitcoinNode.getConnectionString() : null));

        final BitcoinNodeManager nodeManager = _context.getNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final long bitcoinNodePing = (bitcoinNode != null ? bitcoinNode.getAveragePing() : 0L);
        final Long maxTimeout = Math.min(Math.max(1000L, bitcoinNodePing), 5000L);
        final AtomicBoolean didRespond = new AtomicBoolean(false);
        final Pin pin = new Pin();

        final String nodeName = (bitcoinNode != null ? bitcoinNode.getConnectionString() : "best peer");
        final BitcoinNodeManager.DownloadBlockCallback downloadBlockCallback = new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                didRespond.set(true);
                pin.release();

                _currentBlockDownloadSet.remove(blockHash);
                if (currentDownload != null) {
                    currentDownload.milliTimer.stop();
                }

                final Sha256Hash blockHash = block.getHash();
                try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    _storePendingBlock(block, databaseManager);
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                }
                final Long msElapsed = (currentDownload != null ? currentDownload.milliTimer.getMillisecondsElapsed() : null);
                Logger.info("Block " + blockHash + " downloaded from " + nodeName + " in " + msElapsed + "ms");

                { // Handle throughput monitoring...
                    final Integer byteCount = block.getByteCount();
                    final Long bytesPerSecond = ( ((msElapsed != null) && (msElapsed > 0)) ? ((byteCount / msElapsed) * 1000L) : null );
                    if (bytesPerSecond != null) {
                        _historicBytesPerSecond.add(bytesPerSecond);
                    }
                    _totalBytesDownloaded.addAndGet(byteCount);
                    final long downloadCount = _totalDownloadCount.incrementAndGet();
                    if ((downloadCount % _historicThroughputMaxItemCount) == 0) {
                        _calculateThroughput();
                    }
                }

                BlockDownloader.this.wakeUp();

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                didRespond.set(true);
                pin.release();

                _currentBlockDownloadSet.remove(blockHash);
                if (currentDownload != null) {
                    currentDownload.milliTimer.stop();
                }
                final Long msElapsed = (currentDownload != null ? currentDownload.milliTimer.getMillisecondsElapsed() : null);

                Logger.info("Block " + blockHash + " failed from " + nodeName + ((msElapsed != null) ? (" after " + msElapsed + "ms.") : "."));
            }
        };

        final ThreadPool threadPool = _context.getThreadPool();
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    pin.waitForRelease(maxTimeout);
                }
                catch (final Exception exception) { }

                if (! didRespond.get()) {
                    downloadBlockCallback.onFailure(blockHash);
                }
            }
        });

        nodeManager.requestBlock(bitcoinNode, blockHash, downloadBlockCallback);
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final SystemTime systemTime = _context.getSystemTime();
        final BitcoinNodeManager bitcoinNodeManager = _context.getNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final int maximumConcurrentDownloadCount = Math.max(1, Math.min(16, (bitcoinNodeManager.getActiveNodeCount() * 2)));

        _checkForStalledDownloads();

        if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) {
            Logger.trace("Downloader busy; " + _currentBlockDownloadSet.size() + " in flight. Sleeping.");
            return false;
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            if (! _hasGenesisBlock) { // Since nodes do not advertise inventory of the genesis block, specifically add it if it is required...
                final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(BlockHeader.GENESIS_BLOCK_HASH);
                if ( (genesisBlockId == null) || (! blockDatabaseManager.hasTransactions(genesisBlockId)) ) {
                    final PendingBlockId genesisPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(BlockHeader.GENESIS_BLOCK_HASH);
                    if ( (genesisPendingBlockId == null) || (! pendingBlockDatabaseManager.hasBlockData(genesisPendingBlockId)) ) {
                        final Long now = systemTime.getCurrentTimeInSeconds();
                        final long secondsSinceLastDownloadAttempt = (now - Util.coalesce(_lastGenesisDownloadTimestamp));

                        if (secondsSinceLastDownloadAttempt > 5) {
                            _lastGenesisDownloadTimestamp = systemTime.getCurrentTimeInSeconds();
                            _downloadBlock(BlockHeader.GENESIS_BLOCK_HASH, null, null);
                        }
                    }
                }
                else {
                    _hasGenesisBlock = true;
                }
            }

            final List<PendingBlockId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(maximumConcurrentDownloadCount * 2);
            if (downloadPlan.isEmpty()) {
                Logger.trace("Downloader has nothing to do.");
                return false;
            }
            Logger.trace("Download plan contains " + downloadPlan.getCount() + " items.");

            final Integer activeNodeCount = bitcoinNodeManager.getActiveNodeCount();
            final List<BitcoinNode> bitcoinNodes = bitcoinNodeManager.getBestNodes(activeNodeCount, new NodeManager.NodeFilter<BitcoinNode>() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    final NodeFeatures nodeFeatures = bitcoinNode.getNodeFeatures();
                    return nodeFeatures.hasFeatureFlagEnabled(NodeFeatures.Feature.BLOCKCHAIN_ENABLED);
                }
            });
            final int nodeCount = bitcoinNodes.getCount();
            if (nodeCount == 0) { return false; }

            int nextNodeIndex = 0;
            for (final PendingBlockId pendingBlockId : downloadPlan) {
                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(blockHash);
                if (itemIsAlreadyBeingDownloaded) {
                    Logger.trace(blockHash + " already in-flight.");
                    continue;
                }

                final BitcoinNode bitcoinNode = bitcoinNodes.get(nextNodeIndex % nodeCount);
                final NodeId nodeId = bitcoinNode.getId();

                final MilliTimer timer = new MilliTimer();
                final CurrentDownload currentDownload = new CurrentDownload(nodeId, timer);

                _currentBlockDownloadSet.put(blockHash, currentDownload);

                timer.start();
                _downloadBlock(blockHash, bitcoinNode, currentDownload);

                pendingBlockDatabaseManager.updateLastDownloadAttemptTime(pendingBlockId);

                nextNodeIndex += 1;
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        if (Logger.isTraceEnabled()) {
            final StringBuilder stringBuilder = new StringBuilder("In Flight: ");
            stringBuilder.append(_currentBlockDownloadSet.size());
            stringBuilder.append(" of ");
            stringBuilder.append(maximumConcurrentDownloadCount);
            for (final Map.Entry<Sha256Hash, CurrentDownload> entry : _currentBlockDownloadSet.entrySet()) {
                final CurrentDownload currentDownload = entry.getValue();
                final MilliTimer milliTimer = (currentDownload != null ? currentDownload.milliTimer : null);
                if (milliTimer != null) { milliTimer.stop(); }
                final Long msElapsed = (milliTimer != null ? milliTimer.getMillisecondsElapsed() : null);
                stringBuilder.append("\n");
                stringBuilder.append(entry.getKey() + ": " + msElapsed + "ms via " + (currentDownload != null ? currentDownload.nodeId : null));
            }
            Logger.trace(stringBuilder.toString());
        }

        return true;
    }

    @Override
    protected void _onSleep() {
        final SynchronizationStatus synchronizationStatus = _context.getSynchronizationStatus();

        synchronized (this) {
            final boolean isInterrupted;
            {
                final Thread currentThread = Thread.currentThread();
                isInterrupted = currentThread.isInterrupted();
            }

            if ( isInterrupted && (_unsynchronizedWatcher != null) ) {
                _unsynchronizedWatcher.interrupt();
            }
            else if ( (! isInterrupted) && (_unsynchronizedWatcher == null) ) {
                _unsynchronizedWatcher = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(10000L);

                            final boolean synchronizationIsComplete = synchronizationStatus.isBlockchainSynchronized();
                            if (! synchronizationIsComplete) {
                                BlockDownloader.this.wakeUp();
                            }
                        }
                        catch (final InterruptedException exception) { }
                        finally {
                            synchronized (BlockDownloader.this) {
                                _unsynchronizedWatcher = null;
                            }
                        }
                    }
                });
                _unsynchronizedWatcher.start();
            }
        }
    }

    public BlockDownloader(final Context context) {
        _context = context;
    }

    public void setNewBlockAvailableCallback(final Runnable runnable) {
        _newBlockAvailableCallback = runnable;
    }

    public void submitBlock(final Block block) {
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            _storePendingBlock(block, databaseManager);
            Logger.info("Block submitted: " + block.getHash());
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return;
        }

        final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
        if (newBlockAvailableCallback != null) {
            newBlockAvailableCallback.run();
        }
    }

}

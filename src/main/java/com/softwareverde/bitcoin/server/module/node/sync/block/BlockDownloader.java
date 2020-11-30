package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.async.ConcurrentHashSet;
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
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.NodeFilter;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.concurrent.Pin;
import com.softwareverde.concurrent.pool.ThreadPool;
import com.softwareverde.concurrent.service.GracefulSleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.RotatingQueue;
import com.softwareverde.util.StringUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BlockDownloader extends GracefulSleepyService {
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

    protected final ConcurrentHashMap<Sha256Hash, CurrentDownload> _currentBlockDownloadSet = new ConcurrentHashMap<Sha256Hash, CurrentDownload>();
    protected final ConcurrentHashSet<NodeId> _hasBlockInFlight = new ConcurrentHashSet<NodeId>();
    protected final ConcurrentHashSet<NodeId> _hasSecondBlockInFlight = new ConcurrentHashSet<NodeId>();

    protected Runnable _newBlockAvailableCallback = null;

    protected Long _lastBlockfinderTime = null;

    protected Boolean _hasGenesisBlock = false;
    protected Long _lastGenesisDownloadTimestamp = null;

    protected Thread _unsynchronizedWatcher = null;

    protected final Integer _historicThroughputMaxItemCount = 256;
    protected final AtomicLong _totalDownloadCount = new AtomicLong(0L);
    protected final AtomicLong _totalBytesDownloaded = new AtomicLong(0L);
    protected final RotatingQueue<Long> _historicBytesPerSecond = new RotatingQueue<Long>(_historicThroughputMaxItemCount);
    protected Long _lastCacheUpdate = null;
    protected Long _cachedBytesPerSecond = null;
    protected Float _cachedBlocksPerSecond = null;

    /**
     * Attempts to reserve a download slot for the node.
     *  Returns true if the nodeId had capacity to accept a new request.
     */
    protected boolean _addBlockInFlight(final NodeId nodeId) {
        final boolean hadCapacity = _hasBlockInFlight.add(nodeId);
        if (hadCapacity) { return true; }

        final boolean hadSecondaryCapacity = _hasSecondBlockInFlight.add(nodeId);
        return hadSecondaryCapacity;
    }

    protected void _removeInFlightBlock(final NodeId nodeId) {
        final boolean wasRemoved = _hasBlockInFlight.remove(nodeId);
        if (! wasRemoved) {
            _hasSecondBlockInFlight.remove(nodeId);
        }
    }

    protected void _calculateThroughput() {
        final SystemTime systemTime = _context.getSystemTime();
        final Long now = systemTime.getCurrentTimeInSeconds();

        int i = 0;
        long sum = 0L;
        for (final Long bytesPerSecond : _historicBytesPerSecond) {
            sum += bytesPerSecond;
            i += 1;
        }
        if (i == 0) { return; }

        if (_lastCacheUpdate != null) {
            final long secondsSinceLastUpdate = (now - _lastCacheUpdate);
            _cachedBlocksPerSecond = ( ((float) i) / secondsSinceLastUpdate);
        }

        _cachedBytesPerSecond = (sum / i);
        _lastCacheUpdate = now;
    }

    protected Long _calculateTimeout() {
        final float buffer = 2.0F;
        return (long) ((BlockInflater.MAX_BYTE_COUNT / BitcoinNode.MIN_MEGABYTES_PER_SECOND) * buffer * 1000L);
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
            if (currentDownload == null) { continue; }

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

        boolean encounteredStalledBlock = false;
        for (final Sha256Hash stalledBlockHash : stalledBlockHashes) {
            Logger.warn("Stalled Block Detected: " + stalledBlockHash);
            encounteredStalledBlock = true;
            final CurrentDownload currentDownload = _currentBlockDownloadSet.remove(stalledBlockHash);
            if (currentDownload != null) {
                final NodeId nodeId = currentDownload.nodeId;
                if (nodeId != null) {
                    _removeInFlightBlock(nodeId);
                }
            }
        }
        if (encounteredStalledBlock) {
            BlockDownloader.this.wakeUp();
        }
    }

    protected void _downloadBlock(final Sha256Hash blockHash, final BitcoinNode bitcoinNode, final CurrentDownload currentDownload) {
        Logger.trace("Downloading " + blockHash + " from " + bitcoinNode.getConnectionString() + " (id: " + bitcoinNode.getId() + ")");

        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final long maxTimeoutMs = _calculateTimeout();
        final AtomicBoolean didRespond = new AtomicBoolean(false);
        final Pin pin = new Pin();

        final String nodeName = bitcoinNode.getConnectionString();
        final BitcoinNode.DownloadBlockCallback downloadBlockCallback = new BitcoinNode.DownloadBlockCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                if (_shouldAbort()) { return; }

                final boolean hasAlreadyResponded = (! didRespond.compareAndSet(false, true));
                pin.release();

                _currentBlockDownloadSet.remove(blockHash);
                if (currentDownload != null) {
                    currentDownload.milliTimer.stop();

                    final NodeId nodeId = currentDownload.nodeId;
                    if (nodeId != null) {
                        _removeInFlightBlock(nodeId);
                    }
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
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                if (_shouldAbort()) { return; }

                final boolean hasAlreadyResponded = (! didRespond.compareAndSet(false, true));
                pin.release();

                if (bitcoinNode != null) {
                    bitcoinNode.removeCallback(requestId);
                }

                final boolean callbackExistedInSet = (_currentBlockDownloadSet.remove(blockHash) != null);
                if (currentDownload != null) {
                    currentDownload.milliTimer.stop();

                    final NodeId nodeId = currentDownload.nodeId;
                    if (nodeId != null) {
                        _removeInFlightBlock(nodeId);
                    }
                }
                final Long msElapsed = (currentDownload != null ? currentDownload.milliTimer.getMillisecondsElapsed() : null);

                if ( callbackExistedInSet && (! hasAlreadyResponded) ) {
                    Logger.info("Block " + blockHash + " failed from " + nodeName + ((msElapsed != null) ? (" after " + msElapsed + "ms.") : "."));
                }

                BlockDownloader.this.wakeUp();
            }
        };

        final RequestId requestId = bitcoinNode.requestBlock(blockHash, downloadBlockCallback);

        final ThreadPool threadPool = _context.getThreadPool();
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                final Thread thread = Thread.currentThread();

                try {
                    final long sleepInterval = 500L;
                    int msWaited = 0;
                    while ( (! thread.isInterrupted()) && (! pin.wasReleased()) ) {
                        pin.waitForRelease(sleepInterval);
                        if (pin.wasReleased()) { break; }

                        msWaited += sleepInterval;
                        if (msWaited >= maxTimeoutMs) { break; }
                    }
                }
                catch (final Exception exception) { }

                if (! didRespond.get()) {
                    downloadBlockCallback.onFailure(requestId, bitcoinNode, blockHash);
                }
            }
        });

    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final SystemTime systemTime = _context.getSystemTime();
        final BitcoinNodeManager bitcoinNodeManager = _context.getBitcoinNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final Integer activeNodeCount = bitcoinNodeManager.getActiveNodeCount();
        final int maximumConcurrentDownloadCount = (activeNodeCount * 2);

        _checkForStalledDownloads();
        if (_shouldAbort()) { return false; }

        if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) {
            Logger.trace("Downloader busy; " + _currentBlockDownloadSet.size() + " in flight. Sleeping.");
            return false;
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            if (! _hasGenesisBlock) { // Since nodes do not advertise inventory of the genesis block, specifically add it if it is required...
                final Sha256Hash blockHash = BlockHeader.GENESIS_BLOCK_HASH;
                final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
                if ( (genesisBlockId == null) || (! blockDatabaseManager.hasTransactions(genesisBlockId)) ) {
                    final PendingBlockId genesisPendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(BlockHeader.GENESIS_BLOCK_HASH);
                    if ( (genesisPendingBlockId == null) || (! pendingBlockDatabaseManager.hasBlockData(genesisPendingBlockId)) ) {
                        final Long now = systemTime.getCurrentTimeInSeconds();
                        final long secondsSinceLastDownloadAttempt = (now - Util.coalesce(_lastGenesisDownloadTimestamp));

                        if (secondsSinceLastDownloadAttempt > 5) {
                            _lastGenesisDownloadTimestamp = systemTime.getCurrentTimeInSeconds();
                            final List<BitcoinNode> bitcoinNodes = bitcoinNodeManager.getPreferredNodes();
                            for (final BitcoinNode bitcoinNode : bitcoinNodes) {
                                final NodeId nodeId = bitcoinNode.getId();

                                final MilliTimer timer = new MilliTimer();
                                final CurrentDownload currentDownload = new CurrentDownload(nodeId, timer);

                                _currentBlockDownloadSet.put(blockHash, currentDownload);
                                _downloadBlock(blockHash, bitcoinNode, currentDownload);
                            }
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

            if (_shouldAbort()) { return false; }

            final List<BitcoinNode> bitcoinNodes = bitcoinNodeManager.getPreferredNodes(new NodeFilter() {
                @Override
                public Boolean meetsCriteria(final BitcoinNode bitcoinNode) {
                    return true; // TODO: Ensure bitcoinNode is up-to-date before requesting block.
                }
            });
            final int nodeCount = bitcoinNodes.getCount();
            if (nodeCount == 0) {
                Logger.debug("No nodes met download criteria.");
                return false;
            }

            for (final PendingBlockId pendingBlockId : downloadPlan) {
                if (_shouldAbort()) { return false; }

                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(blockHash);
                if (itemIsAlreadyBeingDownloaded) {
                    Logger.trace(blockHash + " already in-flight.");
                    continue;
                }

                BitcoinNode selectedNode = null;
                { // Prefer a node that does not already have a block in-flight.
                    for (final BitcoinNode bitcoinNode : bitcoinNodes) {
                        final NodeId nodeId = bitcoinNode.getId();
                        final boolean hadCapacity = _addBlockInFlight(nodeId);
                        if (! hadCapacity) { continue; }

                        selectedNode = bitcoinNode;
                        break;
                    }
                    if (selectedNode == null) { break; }
                }

                final NodeId nodeId = selectedNode.getId();

                final MilliTimer timer = new MilliTimer();
                final CurrentDownload currentDownload = new CurrentDownload(nodeId, timer);

                _currentBlockDownloadSet.put(blockHash, currentDownload);

                timer.start();
                _downloadBlock(blockHash, selectedNode, currentDownload);

                pendingBlockDatabaseManager.updateLastDownloadAttemptTime(pendingBlockId);
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

        if (_cachedBytesPerSecond != null) {
            final String blocksPerSecondLog = (StringUtil.formatPercent(Util.coalesce(_cachedBlocksPerSecond), false) + " blocks/s, ");
            final String kBpsLog = ((_cachedBytesPerSecond / 1024L) + " kBps, ");
            final String activeNodeLog = (activeNodeCount + " active nodes, ");
            final String blocksInFlightLog = (_currentBlockDownloadSet.size() + " blocks in flight");
            Logger.info("Download " + kBpsLog + blocksPerSecondLog + activeNodeLog + blocksInFlightLog + ".");
        }

        return false;
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

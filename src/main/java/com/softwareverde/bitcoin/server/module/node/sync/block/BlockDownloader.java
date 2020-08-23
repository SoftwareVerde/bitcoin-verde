package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.context.MultiConnectionFullDatabaseContext;
import com.softwareverde.bitcoin.context.NodeManagerContext;
import com.softwareverde.bitcoin.context.PendingBlockStoreContext;
import com.softwareverde.bitcoin.context.SynchronizationStatusContext;
import com.softwareverde.bitcoin.context.SystemTimeContext;
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.BlockFinderHashesBuilder;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BlockDownloader extends SleepyService {
    public interface Context extends BlockInflaters, NodeManagerContext, MultiConnectionFullDatabaseContext, PendingBlockStoreContext, SynchronizationStatusContext, SystemTimeContext { }

    public static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    protected static class CurrentDownload {
        public final NodeId nodeId;
        public final MilliTimer milliTimer;

        public CurrentDownload(final NodeId nodeId, final MilliTimer milliTimer) {
            this.nodeId = nodeId;
            this.milliTimer = milliTimer;
        }
    }

    protected static final Long MAX_TIMEOUT = 30000L;

    protected final Context _context;

    protected final Map<Sha256Hash, CurrentDownload> _currentBlockDownloadSet = new ConcurrentHashMap<Sha256Hash, CurrentDownload>(12);

    protected Runnable _newBlockAvailableCallback = null;

    protected Long _lastBlockfinder = null;

    protected Boolean _hasGenesisBlock = false;
    protected Long _lastGenesisDownloadTimestamp = null;

    protected Thread _unsynchronizedWatcher = null;

    protected void _queueBlockfinderBroadcast() {
        final SystemTime systemTime = _context.getSystemTime();
        final Long now = systemTime.getCurrentTimeInMilliSeconds();

        final long timeElapsed = (now - Util.coalesce(_lastBlockfinder));
        if (timeElapsed < MAX_TIMEOUT) { return; }

        _lastBlockfinder = now;

        final BitcoinNodeManager bitcoinNodeManager = _context.getNodeManager();
        final DatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final DatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockFinderHashesBuilder blockFinderHashesBuilder = new BlockFinderHashesBuilder(databaseManager);
            final List<Sha256Hash> blockFinderHashes = blockFinderHashesBuilder.createBlockFinderBlockHashes();
            bitcoinNodeManager.broadcastBlockFinder(blockFinderHashes);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
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
            if (msElapsed >= MAX_TIMEOUT) {
                stalledBlockHashes.add(blockHash);
            }
        }

        for (final Sha256Hash stalledBlockHash : stalledBlockHashes) {
            Logger.warn("Stalled Block Detected: " + stalledBlockHash);
            _currentBlockDownloadSet.remove(stalledBlockHash);
        }
    }

    protected void _downloadBlock(final Sha256Hash blockHash, final BitcoinNode bitcoinNode) {
        Logger.trace("Downloading " + blockHash + " from " + (bitcoinNode != null ? bitcoinNode.getConnectionString() : null));

        final BitcoinNodeManager nodeManager = _context.getNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final BitcoinNodeManager.DownloadBlockCallback downloadBlockCallback = new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {
                final Sha256Hash blockHash = block.getHash();
                final CurrentDownload currentDownload = _currentBlockDownloadSet.remove(blockHash);
                currentDownload.milliTimer.stop();

                try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    _storePendingBlock(block, databaseManager);
                }
                catch (final DatabaseException exception) {
                    Logger.debug(exception);
                }

                Logger.info("Block " + blockHash + " downloaded from " + (bitcoinNode != null ? bitcoinNode.getConnectionString() : null) + " in " + currentDownload.milliTimer.getMillisecondsElapsed() + "ms");

                BlockDownloader.this.wakeUp();

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
            }
        };

        if (bitcoinNode == null) {
            nodeManager.requestBlock(blockHash, downloadBlockCallback);
        }
        else {
            bitcoinNode.requestBlock(blockHash, downloadBlockCallback);
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        Logger.trace("Downloader awake.");

        final SystemTime systemTime = _context.getSystemTime();
        final BitcoinNodeManager nodeManager = _context.getNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final int maximumConcurrentDownloadCount = Math.max(1, Math.min(12, (nodeManager.getActiveNodeCount() * 3)));

        _checkForStalledDownloads();

        if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) {
            Logger.trace("Downloader busy; " + _currentBlockDownloadSet.size() + " in flight. Sleeping.");
            return false;
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();
            final BlockDatabaseManager blockDatabaseManager = databaseManager.getBlockDatabaseManager();
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

            if (! _hasGenesisBlock) { // Since nodes do not advertise inventory of the genesis block, specifically add it if it is required...
                final BlockId genesisBlockId = blockHeaderDatabaseManager.getBlockHeaderId(BlockHeader.GENESIS_BLOCK_HASH);
                if ( (genesisBlockId == null) || (! blockDatabaseManager.hasTransactions(genesisBlockId)) ) {
                    final Long now =systemTime.getCurrentTimeInSeconds();
                    final long secondsSinceLastDownloadAttempt = (now - Util.coalesce(_lastGenesisDownloadTimestamp));

                    if (secondsSinceLastDownloadAttempt > 30) {
                        _lastGenesisDownloadTimestamp = systemTime.getCurrentTimeInSeconds();
                        _downloadBlock(BlockHeader.GENESIS_BLOCK_HASH, null);
                    }
                }
                else {
                    _hasGenesisBlock = true;
                }
            }

            final List<BitcoinNode> nodes = nodeManager.getNodes();
            final int nodeCount = nodes.getCount();

            final HashMap<NodeId, BitcoinNode> nodeMap = new HashMap<NodeId, BitcoinNode>(nodeCount);
            final List<NodeId> nodeIds;
            {
                final ImmutableListBuilder<NodeId> listBuilder = new ImmutableListBuilder<NodeId>(nodeCount);
                for (final BitcoinNode node : nodes) {
                    final NodeId nodeId = nodeDatabaseManager.getNodeId(node);
                    if (nodeId != null) {
                        listBuilder.add(nodeId);
                        nodeMap.put(nodeId, node);
                    }
                }
                nodeIds = listBuilder.build();
            }

            final Boolean highPriorityUnknownInventoryExists = pendingBlockDatabaseManager.hasUnknownHighPriorityInventory(nodeIds);
            if (highPriorityUnknownInventoryExists) {
                _queueBlockfinderBroadcast();
            }

            final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(nodeIds, maximumConcurrentDownloadCount * 2);
            if (downloadPlan.isEmpty()) {
                final Boolean unknownInventoryExists = pendingBlockDatabaseManager.hasUnknownInventory(nodeIds);
                if (unknownInventoryExists) {
                    _queueBlockfinderBroadcast();
                }

                Logger.trace("Downloader has nothing to do.");
                return false;
            }

            for (final PendingBlockId pendingBlockId : downloadPlan.keySet()) {
                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(blockHash);
                if (itemIsAlreadyBeingDownloaded) {
                    Logger.trace(blockHash + " already in-flight.");
                    continue;
                }

                final NodeId nodeId = downloadPlan.get(pendingBlockId);
                final BitcoinNode bitcoinNode = nodeMap.get(nodeId);

                final MilliTimer timer = new MilliTimer();
                final CurrentDownload currentDownload = new CurrentDownload(nodeId, timer);

                _currentBlockDownloadSet.put(blockHash, currentDownload);

                timer.start();
                _downloadBlock(blockHash, bitcoinNode);

                pendingBlockDatabaseManager.updateLastDownloadAttemptTime(pendingBlockId);
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() {
        final SynchronizationStatus synchronizationStatus = _context.getSynchronizationStatus();
        Logger.trace("Downloader getting sleepy.");

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

        Logger.trace("Downloader sleeps.");
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

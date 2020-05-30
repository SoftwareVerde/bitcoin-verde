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
import com.softwareverde.bitcoin.inflater.BlockInflaters;
import com.softwareverde.bitcoin.server.SynchronizationStatus;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.fullnode.FullNodePendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
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

    protected static final Long MAX_TIMEOUT = 90000L;

    protected final Context _context;

    protected final Object _downloadCallbackPin = new Object();

    protected final Map<Sha256Hash, MilliTimer> _currentBlockDownloadSet = new ConcurrentHashMap<Sha256Hash, MilliTimer>();
    protected final BitcoinNodeManager.DownloadBlockCallback _blockDownloadedCallback;

    protected Runnable _newBlockAvailableCallback = null;

    protected Boolean _hasGenesisBlock = false;
    protected Long _lastGenesisDownloadTimestamp = null;

    protected Long _lastFindInventoryTimestamp = null;
    protected Thread _unsynchronizedWatcher = null;

    protected void _onBlockDownloaded(final Block block, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();
        pendingBlockDatabaseManager.storeBlock(block);
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
    // TODO: Investigate why onFailure is not being invoked by the BitcoinNodeManager.
    protected void _checkForStalledDownloads() {
        final MutableList<Sha256Hash> stalledBlockHashes = new MutableList<Sha256Hash>();
        for (final Sha256Hash blockHash : _currentBlockDownloadSet.keySet()) {
            final MilliTimer milliTimer = _currentBlockDownloadSet.get(blockHash);
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
            _blockDownloadedCallback.onFailure(stalledBlockHash);
        }
    }

    protected void _downloadBlock(final Sha256Hash blockHash) {
        _downloadBlock(blockHash, null);
    }

    protected void _downloadBlock(final Sha256Hash blockHash, final BitcoinNode nullableBitcoinNode) {
        final BlockInflater blockInflater = _context.getBlockInflater();
        final PendingBlockStore blockStore = _context.getPendingBlockStore();

        boolean pendingBlockExists = ( (blockInflater != null) && (blockStore != null) && (blockStore.pendingBlockExists(blockHash)) );
        if (pendingBlockExists) {
            pendingBlockExists = false;
            final ByteArray blockData = blockStore.getPendingBlockData(blockHash);
            if (blockData != null) {
                final Block block = blockInflater.fromBytes(blockData);
                if (block != null) {
                    pendingBlockExists = true;
                    _blockDownloadedCallback.onResult(block);
                }
            }
        }

        if (! pendingBlockExists) {
            final BitcoinNodeManager nodeManager = _context.getNodeManager();
            if (nullableBitcoinNode == null) {
                nodeManager.requestBlock(blockHash, _blockDownloadedCallback);
            }
            else {
                nodeManager.requestBlock(nullableBitcoinNode, blockHash, _blockDownloadedCallback);
            }
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final SystemTime systemTime = _context.getSystemTime();
        final BitcoinNodeManager nodeManager = _context.getNodeManager();
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();

        final int maximumConcurrentDownloadCount = Math.min(21, ((nodeManager.getActiveNodeCount() * 3) + 1) );

        _checkForStalledDownloads();

        { // Determine if routine should wait for a request to complete...
            int currentDownloadCount = _currentBlockDownloadSet.size();
            while (currentDownloadCount >= maximumConcurrentDownloadCount) {
                synchronized (_downloadCallbackPin) {
                    final MilliTimer waitTimer = new MilliTimer();
                    try {
                        waitTimer.start();
                        _downloadCallbackPin.wait(MAX_TIMEOUT);
                        waitTimer.stop();
                    }
                    catch (final InterruptedException exception) { return false; }

                    if (waitTimer.getMillisecondsElapsed() >= MAX_TIMEOUT) {
                        Logger.warn("Block download stalled.");

                        _markPendingBlockIdsAsFailed(_currentBlockDownloadSet.keySet());
                        _currentBlockDownloadSet.clear();
                        return false;
                    }
                }

                currentDownloadCount = _currentBlockDownloadSet.size();
            }
        }

        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
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
                        _downloadBlock(BlockHeader.GENESIS_BLOCK_HASH);
                    }
                }
                else {
                    _hasGenesisBlock = true;
                }
            }

            final List<BitcoinNode> nodes = nodeManager.getNodes();

            final HashMap<NodeId, BitcoinNode> nodeMap = new HashMap<NodeId, BitcoinNode>();
            final List<NodeId> nodeIds;
            {
                final ImmutableListBuilder<NodeId> listBuilder = new ImmutableListBuilder<NodeId>(nodes.getCount());
                for (final BitcoinNode node : nodes) {
                    final NodeId nodeId = nodeDatabaseManager.getNodeId(node);
                    if (nodeId != null) {
                        listBuilder.add(nodeId);
                        nodeMap.put(nodeId, node);
                    }
                }
                nodeIds = listBuilder.build();
            }

            try {
                TransactionUtil.startTransaction(databaseConnection);
                pendingBlockDatabaseManager.cleanupPendingBlocks();
                TransactionUtil.commitTransaction(databaseConnection);
            }
            catch (final DatabaseException exception) {
                Logger.warn("Unable to cleanup pending blocks..."); // Often encounters SQL deadlock...
            }

            final Map<PendingBlockId, NodeId> downloadPlan = pendingBlockDatabaseManager.selectIncompletePendingBlocks(nodeIds, maximumConcurrentDownloadCount * 2);
            if (downloadPlan.isEmpty()) { return false; }

            for (final PendingBlockId pendingBlockId : downloadPlan.keySet()) {
                if (_currentBlockDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }

                final Sha256Hash blockHash = pendingBlockDatabaseManager.getPendingBlockHash(pendingBlockId);
                if (blockHash == null) { continue; }

                final boolean itemIsAlreadyBeingDownloaded = _currentBlockDownloadSet.containsKey(blockHash);
                if (itemIsAlreadyBeingDownloaded) { continue; }

                final NodeId nodeId = downloadPlan.get(pendingBlockId);
                final BitcoinNode bitcoinNode = nodeMap.get(nodeId);

                final MilliTimer timer = new MilliTimer();
                timer.start();

                _currentBlockDownloadSet.put(blockHash, timer);

                // if (bitcoinNode.supportsExtraThinBlocks() && _synchronizationStatus.isReadyForTransactions()) {
                //     _bitcoinNodeManager.requestThinBlock(bitcoinNode, blockHash, _blockDownloadedCallback);
                // }
                // else {
                //     _bitcoinNodeManager.requestBlock(bitcoinNode, blockHash, _blockDownloadedCallback);
                // }
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
        final SystemTime systemTime = _context.getSystemTime();
        final BitcoinNodeManager nodeManager = _context.getNodeManager();
        final SynchronizationStatus synchronizationStatus = _context.getSynchronizationStatus();

        final List<NodeId> connectedNodeIds = nodeManager.getNodeIds();
        if (! connectedNodeIds.isEmpty()) {

            final Long now = systemTime.getCurrentTimeInMilliSeconds();
            if (now - Util.coalesce(_lastFindInventoryTimestamp) >= 10000L) {
                _lastFindInventoryTimestamp = now;
                nodeManager.findNodeInventory();
            }

            // TODO: Re-enable and move purge logic to somewhere more appropriate.
            // Purging unlocatable blocks is disabled due to it preventing NodeInventory from being recorded.
            //  The purge should be performed, but BlockDownloader sleeps too frequently.
            //  Particularly, the purge deletes the unlocatable (but desired) entry recorded by the BlockRequester.

            // Logger.info("Searching for Unlocatable Pending Blocks...");
            // try (final DatabaseConnection databaseConnection = _databaseConnectionFactory.newConnection()) {
            //     final PendingBlockDatabaseManager pendingBlockDatabaseManager = new PendingBlockDatabaseManager(databaseConnection);
            //
            //     TransactionUtil.startTransaction(databaseConnection);
            //     pendingBlockDatabaseManager.purgeUnlocatablePendingBlocks(connectedNodeIds);
            //     TransactionUtil.commitTransaction(databaseConnection);
            // }
            // catch (final DatabaseException exception) {
            //     Logger.warn(exception);
            // }
        }

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

        _blockDownloadedCallback = new BitcoinNodeManager.DownloadBlockCallback() {
            @Override
            public void onResult(final Block block) {

                final Sha256Hash blockHash = block.getHash();
                final MilliTimer timer = _currentBlockDownloadSet.remove(blockHash);
                if (timer != null) {
                    timer.stop();
                }

                Logger.info("Downloaded Block: " + blockHash + " (" + (timer != null ? timer.getMillisecondsElapsed() : "??") + "ms)");

                final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
                try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    _onBlockDownloaded(block, databaseManager);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return;
                }
                finally {
                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }

                final Runnable newBlockAvailableCallback = _newBlockAvailableCallback;
                if (newBlockAvailableCallback != null) {
                    newBlockAvailableCallback.run();
                }
            }

            @Override
            public void onFailure(final Sha256Hash blockHash) {
                final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
                try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
                    final FullNodePendingBlockDatabaseManager pendingBlockDatabaseManager = databaseManager.getPendingBlockDatabaseManager();

                    final PendingBlockId pendingBlockId = pendingBlockDatabaseManager.getPendingBlockId(blockHash);
                    if (pendingBlockId == null) {
                        Logger.warn("Unable to increment download failure count for block: " + blockHash);
                        return;
                    }

                    pendingBlockDatabaseManager.incrementFailedDownloadCount(pendingBlockId);
                    pendingBlockDatabaseManager.purgeFailedPendingBlocks(MAX_DOWNLOAD_FAILURE_COUNT);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    Logger.warn("Unable to increment download failure count for block: " + blockHash);
                }
                finally {
                    _currentBlockDownloadSet.remove(blockHash);

                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }
            }
        };
    }

    public void setNewBlockAvailableCallback(final Runnable runnable) {
        _newBlockAvailableCallback = runnable;
    }

    public void submitBlock(final Block block) {
        final FullNodeDatabaseManagerFactory databaseManagerFactory = _context.getDatabaseManagerFactory();
        try (final FullNodeDatabaseManager databaseManager = databaseManagerFactory.newDatabaseManager()) {
            _onBlockDownloaded(block, databaseManager);
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

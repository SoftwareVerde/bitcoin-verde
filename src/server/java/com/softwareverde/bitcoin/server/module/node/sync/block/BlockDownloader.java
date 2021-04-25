package com.softwareverde.bitcoin.server.module.node.sync.block;

import com.softwareverde.bitcoin.block.Block;
import com.softwareverde.bitcoin.server.module.node.store.PendingBlockStore;
import com.softwareverde.bitcoin.server.module.node.sync.inventory.BitcoinNodeBlockInventoryTracker;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.server.node.RequestPriority;
import com.softwareverde.concurrent.service.GracefulSleepyService;
import com.softwareverde.concurrent.threadpool.ThreadPool;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.Util;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockDownloader extends GracefulSleepyService {
    public static class PendingBlockInventory {
        public final long priority;
        public final Sha256Hash blockHash;
        public final WeakReference<BitcoinNode> bitcoinNode;
        public final Long blockHeight;

        public PendingBlockInventory(final Long priority, final Sha256Hash blockHash) {
            this(priority, blockHash, null);
        }

        public PendingBlockInventory(final Long priority, final Sha256Hash blockHash, final BitcoinNode bitcoinNode) {
            this(priority, blockHash, bitcoinNode, null);
        }

        public PendingBlockInventory(final Long priority, final Sha256Hash blockHash, final BitcoinNode bitcoinNode, final Long blockHeight) {
            this.priority = priority;
            this.blockHash = blockHash;
            this.bitcoinNode = new WeakReference<>(bitcoinNode);
            this.blockHeight = blockHeight;
        }
    }

    public interface BlockDownloadCallback {
        void onBlockDownloaded(final Block block, final BitcoinNode bitcoinNode);
    }

    public interface BitcoinNodeCollector {
        List<BitcoinNode> getBitcoinNodes();
    }

    public interface BlockDownloadPlanner {
        List<PendingBlockInventory> getNextPendingBlockInventoryBatch();
    }

    protected final ThreadPool _threadPool;
    protected final ConcurrentSkipListSet<PendingBlockInventory> _downloadBlockQueue;
    protected final PendingBlockStore _pendingBlockStore;
    protected final BitcoinNodeCollector _bitcoinNodeCollector;
    // References to the nodes are used instead of the NodeId so that cleanup is done automatically when the node is disconnected.
    protected final WeakHashMap<BitcoinNode, AtomicInteger> _activeDownloadCounts = new WeakHashMap<>();
    protected final BitcoinNodeBlockInventoryTracker _blockInventoryTracker;
    protected final BlockDownloadPlanner _blockDownloadPlanner;

    protected Integer _maxConcurrentDownloadCountPerNode = 2;
    protected Integer _maxConcurrentDownloadCount = 8;
    protected BlockDownloadCallback _blockDownloadCallback;

    protected AtomicInteger _getActiveDownloadCount(final BitcoinNode bitcoinNode) {
        final AtomicInteger newActiveDownloadCount = new AtomicInteger(0);
        final AtomicInteger existingActiveDownloadCount;
        synchronized (_activeDownloadCounts) {
            existingActiveDownloadCount = _activeDownloadCounts.putIfAbsent(bitcoinNode, newActiveDownloadCount); // NOTE: Atomic/Thread-Safe...
        }
        return Util.coalesce(existingActiveDownloadCount, newActiveDownloadCount);
    }

    protected List<BitcoinNode> _filterBitcoinNodesByActiveDownloadCount(final List<BitcoinNode> bitcoinNodes) {
        MutableList<Tuple<BitcoinNode, Integer>> bitcoinNodeDownloadCounts = new MutableList<>();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final AtomicInteger activeDownloadCount;
            synchronized (_activeDownloadCounts) {
                activeDownloadCount = _activeDownloadCounts.get(bitcoinNode);
            }

            final int downloadCount = (activeDownloadCount != null ? activeDownloadCount.get() : 0);
            if (downloadCount < _maxConcurrentDownloadCountPerNode) {
                bitcoinNodeDownloadCounts.add(new Tuple<>(bitcoinNode, downloadCount));
            }
        }
        bitcoinNodeDownloadCounts.sort(new Comparator<Tuple<BitcoinNode, Integer>>() {
            @Override
            public int compare(final Tuple<BitcoinNode, Integer> tuple0, final Tuple<BitcoinNode, Integer> tuple1) {
                final Integer downloadCount0 = tuple0.second;
                final Integer downloadCount1 = tuple1.second;
                return Integer.compare(downloadCount0, downloadCount1);
            }
        });

        final MutableList<BitcoinNode> filteredBitcoinNodes = new MutableList<>();
        for (final Tuple<BitcoinNode, ?> tuple : bitcoinNodeDownloadCounts) {
            final BitcoinNode bitcoinNode = tuple.first;
            filteredBitcoinNodes.add(bitcoinNode);
        }
        return filteredBitcoinNodes;
    }

    protected List<BitcoinNode> _filterBitcoinNodesByBlockHeight(final List<BitcoinNode> bitcoinNodes, final Long blockHeight) {
        MutableList<Tuple<BitcoinNode, Long>> bitcoinNodeBlockHeightDifferences = new MutableList<>();
        for (final BitcoinNode bitcoinNode : bitcoinNodes) {
            final Long bitcoinNodeBlockHeight = bitcoinNode.getBlockHeight();
            if (bitcoinNodeBlockHeight == null) { continue; }

            final long blockHeightDifference = (bitcoinNodeBlockHeight - blockHeight);
            if (blockHeightDifference < 0L) { continue; } // The node is behind the requested blockHeight...

            bitcoinNodeBlockHeightDifferences.add(new Tuple<>(bitcoinNode, blockHeightDifference));
        }
        bitcoinNodeBlockHeightDifferences.sort(new Comparator<Tuple<BitcoinNode, Long>>() {
            @Override
            public int compare(final Tuple<BitcoinNode, Long> tuple0, final Tuple<BitcoinNode, Long> tuple1) {
                final Long blockHeightDifference0 = tuple0.second;
                final Long blockHeightDifference1 = tuple1.second;
                final int compareValueAscending = Long.compare(blockHeightDifference0, blockHeightDifference1);
                if (compareValueAscending == 0) { return 0; } // Comparison is equal.

                final int compareValueDescending = (compareValueAscending > 0 ? -1 : 1);
                return compareValueDescending;
            }
        });

        final MutableList<BitcoinNode> filteredBitcoinNodes = new MutableList<>();
        for (final Tuple<BitcoinNode, ?> tuple : bitcoinNodeBlockHeightDifferences) {
            final BitcoinNode bitcoinNode = tuple.first;
            filteredBitcoinNodes.add(bitcoinNode);
        }
        return filteredBitcoinNodes;
    }

    protected BitcoinNode _selectBitcoinNode(final Sha256Hash blockHash) {
        return _selectBitcoinNode(blockHash, null);
    }

    protected BitcoinNode _selectBitcoinNode(final Sha256Hash blockHash, final Long blockHeight) {
        if (blockHash != null) { // Attempt to find a BitcoinNode that has the provided inventory, assuming it isn't at max concurrent download capacity.
            final List<BitcoinNode> bitcoinNodesWithInventory = _blockInventoryTracker.getNodesWithInventory(blockHash);
            final List<BitcoinNode> filteredNodes = _filterBitcoinNodesByActiveDownloadCount(bitcoinNodesWithInventory);
            if (! filteredNodes.isEmpty()) {
                return filteredNodes.get(0);
            }
        }

        // Fallback to selecting any node that is not at max concurrent download capacity...
        final List<BitcoinNode> allPreferredBitcoinNodes = _bitcoinNodeCollector.getBitcoinNodes();

        List<BitcoinNode> filteredNodes = _filterBitcoinNodesByActiveDownloadCount(allPreferredBitcoinNodes);

        // If blockHeight is available then also filter by the bitcoinNode's blockHeight.
        if (blockHeight != null) {
            filteredNodes = _filterBitcoinNodesByBlockHeight(filteredNodes, blockHeight);
        }

        if (filteredNodes.isEmpty()) { return null; }
        return filteredNodes.get(0);
    }

    @Override
    protected void _onStart() {
        if (! _downloadBlockQueue.isEmpty()) { return; }

        final List<PendingBlockInventory> blockInventoryBatch = _blockDownloadPlanner.getNextPendingBlockInventoryBatch();
        if (blockInventoryBatch.isEmpty()) { return; }

        for (final PendingBlockInventory pendingBlockInventory : blockInventoryBatch) {
            _downloadBlockQueue.add(pendingBlockInventory);
        }
    }

    @Override
    protected Boolean _run() {
        if (Logger.isTraceEnabled()) {
            String separator = "";
            int downloadBlockQueueCount = 0; // NOTE: ConcurrentSkipListSet::size is not constant-time...
            final StringBuilder stringBuilder = new StringBuilder();
            for (final PendingBlockInventory pendingBlockInventory : _downloadBlockQueue) {
                final Sha256Hash blockHash = pendingBlockInventory.blockHash;
                final Long priority = pendingBlockInventory.priority;
                final BitcoinNode bitcoinNode = pendingBlockInventory.bitcoinNode.get();
                final Long blockHeight = pendingBlockInventory.blockHeight;

                stringBuilder.append(separator);
                stringBuilder.append(priority);
                stringBuilder.append(":");
                stringBuilder.append(blockHash);
                stringBuilder.append(":");
                stringBuilder.append(blockHeight);
                stringBuilder.append(":");
                stringBuilder.append(bitcoinNode);
                separator = " ";

                downloadBlockQueueCount += 1;
            }
            Logger.trace("BlockDownloader Queue Count: " + downloadBlockQueueCount + " " + stringBuilder);
        }

        while (! _shouldAbort()) {
            final PendingBlockInventory pendingBlockInventory = _downloadBlockQueue.pollFirst();
            if (pendingBlockInventory == null) {
                Logger.debug("BlockDownloader - Nothing to do.");
                return false;
            }

            final Sha256Hash blockHash = pendingBlockInventory.blockHash;
            final Long blockHeight = pendingBlockInventory.blockHeight;

            final Boolean hasAlreadyBeenDownloaded = _pendingBlockStore.pendingBlockExists(blockHash);
            if (hasAlreadyBeenDownloaded) { continue; }

            int currentActiveDownloadCount = 0;
            synchronized (_activeDownloadCounts) {
                for (final AtomicInteger activeDownloadCount : _activeDownloadCounts.values()) {
                    currentActiveDownloadCount += activeDownloadCount.get();
                }
            }
            if (currentActiveDownloadCount > _maxConcurrentDownloadCount) {
                _downloadBlockQueue.add(pendingBlockInventory);
                Logger.trace("BlockDownloader - Too busy.");
                return false;
            }

            final RequestPriority requestPriority;
            final BitcoinNode bitcoinNode;
            {
                final BitcoinNode suggestedBitcoinNode = pendingBlockInventory.bitcoinNode.get();
                if (suggestedBitcoinNode != null) {
                    // If the active download count for the suggested node, then attempt a different node...
                    final AtomicInteger suggestedNodeActiveDownloadCount = _getActiveDownloadCount(suggestedBitcoinNode);

                    if ( suggestedBitcoinNode.isConnected() && (suggestedNodeActiveDownloadCount.get() < _maxConcurrentDownloadCountPerNode) ) {
                        bitcoinNode = suggestedBitcoinNode;
                        requestPriority = RequestPriority.NORMAL;
                    }
                    else {
                        bitcoinNode = _selectBitcoinNode(blockHash);

                        // Set the requestPriority to NORMAL if the selected node should have the block...
                        final Long bitcoinNodeBlockHeight = bitcoinNode.getBlockHeight();
                        if (bitcoinNodeBlockHeight != null && blockHeight != null) {
                            final long minDistanceForNormalPriority = 128L; // TODO: Consider setting to pruning mode depth / max reorg depth...
                            final long blockHeightDistance = (bitcoinNodeBlockHeight - blockHeight);
                            requestPriority = (blockHeightDistance >= minDistanceForNormalPriority ? RequestPriority.NORMAL : RequestPriority.NONE);
                        }
                        else {
                            requestPriority = RequestPriority.NONE;
                        }
                    }
                }
                else {
                    bitcoinNode = _selectBitcoinNode(blockHash, blockHeight);
                    requestPriority = RequestPriority.NONE;
                }
            }
            if (bitcoinNode == null) {
                _downloadBlockQueue.add(pendingBlockInventory);
                Logger.debug("BlockDownloader - No nodes available to download block: " + blockHash);
                return false;
            }

            // Maintain the active download count for each node...
            final AtomicInteger activeDownloadCount = _getActiveDownloadCount(bitcoinNode);
            activeDownloadCount.incrementAndGet();

            Logger.trace("Requesting Block " + blockHash + " from " + bitcoinNode + ".");
            bitcoinNode.requestBlock(blockHash, new BitcoinNode.DownloadBlockCallback() {
                @Override
                public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Block block) {
                    activeDownloadCount.decrementAndGet();
                    _pendingBlockStore.storePendingBlock(block);

                    Logger.debug("Block " + blockHash + " downloaded from " + bitcoinNode + ".");

                    final BlockDownloadCallback blockDownloadCallback = _blockDownloadCallback;
                    if (blockDownloadCallback != null) {
                        _threadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                blockDownloadCallback.onBlockDownloaded(block, bitcoinNode);
                            }
                        });
                    }

                    BlockDownloader.this.wakeUp();
                }

                @Override
                public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash blockHash) {
                    activeDownloadCount.decrementAndGet();
                    Logger.debug("Block " + blockHash + " failed to download from " + bitcoinNode + ".");

                    // Reinsert the pendingBlockInventory into the queue with a higher priority...
                    final Long newPriority = (pendingBlockInventory.priority + Math.max(8, _maxConcurrentDownloadCount));
                    final PendingBlockInventory newPendingBlockInventory = new PendingBlockInventory(newPriority, blockHash, null, blockHeight);
                    _downloadBlockQueue.add(newPendingBlockInventory);
                }
            }, requestPriority);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public BlockDownloader(final PendingBlockStore pendingBlockStore, final BitcoinNodeCollector bitcoinNodeCollector, final BitcoinNodeBlockInventoryTracker blockInventoryTracker, final BlockDownloadPlanner blockDownloadPlanner, final ThreadPool threadPool) {
        _threadPool = threadPool;
        _pendingBlockStore = pendingBlockStore;
        _bitcoinNodeCollector = bitcoinNodeCollector;
        _downloadBlockQueue = new ConcurrentSkipListSet<>(new Comparator<PendingBlockInventory>() {
            @Override
            public int compare(final PendingBlockInventory pendingBlockInventory0, final PendingBlockInventory pendingBlockInventory1) {
                if (pendingBlockInventory0 == pendingBlockInventory1) { return 0; }

                final int priorityCompare = Long.compare(pendingBlockInventory0.priority, pendingBlockInventory1.priority);
                if (priorityCompare != 0) {
                    return priorityCompare;
                }

                final int hashCompare = pendingBlockInventory0.blockHash.compareTo(pendingBlockInventory1.blockHash);
                if (hashCompare != 0) {
                    return hashCompare;
                }

                final long nodeId0;
                {
                    final BitcoinNode bitcoinNode = pendingBlockInventory0.bitcoinNode.get();
                    final NodeId nodeId = (bitcoinNode != null ? bitcoinNode.getId() : null);
                    nodeId0 = (nodeId != null ? nodeId.longValue() : Long.MAX_VALUE);
                }

                final long nodeId1;
                {
                    final BitcoinNode bitcoinNode = pendingBlockInventory1.bitcoinNode.get();
                    final NodeId nodeId = (bitcoinNode != null ? bitcoinNode.getId() : null);
                    nodeId1 = (nodeId != null ? nodeId.longValue() : Long.MAX_VALUE);
                }

                return Long.compare(nodeId0, nodeId1);
            }
        });
        _blockInventoryTracker = blockInventoryTracker;
        _blockDownloadPlanner = blockDownloadPlanner;
    }

    public void requestBlock(final Sha256Hash blockHash, final Long priority, final BitcoinNode bitcoinNode) {
        final PendingBlockInventory pendingBlockInventory = new PendingBlockInventory(priority, blockHash, bitcoinNode);
        _downloadBlockQueue.add(pendingBlockInventory);

        this.wakeUp();
    }

    public void setBlockDownloadedCallback(final BlockDownloadCallback blockDownloadCallback) {
        _blockDownloadCallback = blockDownloadCallback;
    }

    public void setMaxConcurrentDownloadCount(final Integer maxConcurrentDownloadCount) {
        _maxConcurrentDownloadCount = maxConcurrentDownloadCount;
    }

    public void setMaxConcurrentDownloadCountPerNode(final Integer maxConcurrentDownloadCountPerNode) {
        _maxConcurrentDownloadCountPerNode = maxConcurrentDownloadCountPerNode;
    }

    public void submitBlock(final Block block) {
        final Sha256Hash blockHash = block.getHash();
        final Boolean blockExists = _pendingBlockStore.pendingBlockExists(blockHash);
        if (blockExists) { return; }

        _pendingBlockStore.storePendingBlock(block);
        // NOTE: The blockHash is not removed from the _downloadBlockQueue since it would require a O(N) search and the
        //  the main loop skips the entry once encountered if it has already been downloaded...

        final BlockDownloadCallback blockDownloadCallback = _blockDownloadCallback;
        if (blockDownloadCallback != null) {
            _threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    blockDownloadCallback.onBlockDownloaded(block, null);
                }
            });
        }
    }
}

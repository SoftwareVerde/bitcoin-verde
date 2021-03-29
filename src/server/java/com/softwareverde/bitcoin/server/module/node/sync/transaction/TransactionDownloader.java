package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.server.node.RequestId;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Tuple;
import com.softwareverde.util.timer.MilliTimer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionDownloader extends SleepyService {
    public static final Integer MAX_DOWNLOAD_FAILURE_COUNT = 10;

    protected static final Long MAX_TIMEOUT = 90000L;

    protected final Object _downloadCallbackPin = new Object();

    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final Map<Sha256Hash, MilliTimer> _currentTransactionDownloadSet = new ConcurrentHashMap<Sha256Hash, MilliTimer>();
    protected final BitcoinNode.DownloadTransactionCallback _transactionDownloadedCallback;

    protected final ConcurrentHashMap<Sha256Hash, Tuple<RequestId, BitcoinNode>> _pendingRequestInformation = new ConcurrentHashMap<>();

    protected Runnable _newTransactionAvailableCallback = null;

    protected void _onTransactionDownloaded(final Transaction transaction, final FullNodeDatabaseManager databaseManager) throws DatabaseException {
        final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
        pendingTransactionDatabaseManager.storeTransaction(transaction);
    }

    protected void _markPendingTransactionIdsAsFailed(final Set<Sha256Hash> pendingTransactionHashes) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();

            for (final Sha256Hash pendingTransactionHash : pendingTransactionHashes) {
                final PendingTransactionId pendingTransactionId = pendingTransactionDatabaseManager.getPendingTransactionId(pendingTransactionHash);
                if (pendingTransactionId == null) { continue; }

                pendingTransactionDatabaseManager.incrementFailedDownloadCount(pendingTransactionId);
            }
            pendingTransactionDatabaseManager.purgeFailedPendingTransactions(MAX_DOWNLOAD_FAILURE_COUNT);
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }

    // This function iterates through each Transaction in-flight, and checks for items that have exceeded the MAX_TIMEOUT.
    //  Items exceeding the timeout have their onFailure method called.
    //  This function should not be necessary, and is a work-around for a bug within the NodeManager that is causing onFailure to not be triggered.
    // TODO: Investigate why onFailure is not being invoked by the BitcoinNodeManager.
    protected void _checkForStalledDownloads() {
        final MutableList<Sha256Hash> stalledTransactionHashes = new MutableList<Sha256Hash>();
        for (final Sha256Hash transactionHash : _currentTransactionDownloadSet.keySet()) {
            final MilliTimer milliTimer = _currentTransactionDownloadSet.get(transactionHash);
            if (milliTimer == null) {
                stalledTransactionHashes.add(transactionHash);
                continue;
            }

            milliTimer.stop();
            final Long msElapsed = milliTimer.getMillisecondsElapsed();
            if (msElapsed >= MAX_TIMEOUT) {
                stalledTransactionHashes.add(transactionHash);
            }
        }

        if (! stalledTransactionHashes.isEmpty()) {
            for (final Sha256Hash stalledTransactionHash : stalledTransactionHashes) {
                Logger.warn("Stalled Transaction Detected: " + stalledTransactionHash);
                _currentTransactionDownloadSet.remove(stalledTransactionHash);

                final Tuple<RequestId, BitcoinNode> requestInformation = _pendingRequestInformation.remove(stalledTransactionHash);
                if (requestInformation != null) {
                    _transactionDownloadedCallback.onFailure(requestInformation.first, requestInformation.second, stalledTransactionHash);
                }
            }
        }
    }

    @Override
    protected void _onStart() { }

    @Override
    protected Boolean _run() {
        final int maximumConcurrentDownloadCount = Math.max(1, _bitcoinNodeManager.getActiveNodeCount());

        _checkForStalledDownloads();

        { // Determine if routine should wait for a request to complete...
            int currentDownloadCount = _currentTransactionDownloadSet.size();
            while (currentDownloadCount >= maximumConcurrentDownloadCount) {
                synchronized (_downloadCallbackPin) {
                    final MilliTimer waitTimer = new MilliTimer();
                    try {
                        waitTimer.start();
                        _downloadCallbackPin.wait(MAX_TIMEOUT);
                        waitTimer.stop();
                    }
                    catch (final InterruptedException exception) { return false; }

                    if (waitTimer.getMillisecondsElapsed() > MAX_TIMEOUT) {
                        Logger.warn("Transaction download stalled.");
                        _markPendingTransactionIdsAsFailed(_currentTransactionDownloadSet.keySet());
                        _currentTransactionDownloadSet.clear();
                        return false;
                    }
                }

                currentDownloadCount = _currentTransactionDownloadSet.size();
            }
        }

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final BitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();

            final List<BitcoinNode> nodes = _bitcoinNodeManager.getNodes();
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

            final Map<NodeId, ? extends List<PendingTransactionId>> downloadPlan = pendingTransactionDatabaseManager.selectIncompletePendingTransactions(nodeIds);
            if (downloadPlan.isEmpty()) { return false; }

            for (final NodeId nodeId : downloadPlan.keySet()) {
                if (_currentTransactionDownloadSet.size() >= maximumConcurrentDownloadCount) { break; }
                final List<PendingTransactionId> pendingTransactionIds = downloadPlan.get(nodeId);
                final MutableList<Sha256Hash> pendingTransactionHashes = new MutableList<Sha256Hash>(pendingTransactionIds.getCount());
                for (final PendingTransactionId pendingTransactionId : pendingTransactionIds) {
                    final Sha256Hash transactionHash = pendingTransactionDatabaseManager.getPendingTransactionHash(pendingTransactionId);
                    if (transactionHash == null) { continue; }
                    final boolean itemIsAlreadyBeingDownloaded = _currentTransactionDownloadSet.containsKey(transactionHash);
                    if (itemIsAlreadyBeingDownloaded) { continue; }

                    pendingTransactionHashes.add(transactionHash);

                    final MilliTimer timer = new MilliTimer();
                    _currentTransactionDownloadSet.put(transactionHash, timer);

                    pendingTransactionDatabaseManager.updateLastDownloadAttemptTime(pendingTransactionId);
                    timer.start();
                }

                if (pendingTransactionHashes.isEmpty()) { continue; }
                final BitcoinNode bitcoinNode = nodeMap.get(nodeId);
                final RequestId requestId = bitcoinNode.requestTransactions(pendingTransactionHashes, _transactionDownloadedCallback);

                final Tuple<RequestId, BitcoinNode> requestInformation = new Tuple<>(requestId, bitcoinNode);
                for (final Sha256Hash transactionHash : pendingTransactionHashes) {
                    _pendingRequestInformation.put(transactionHash, requestInformation);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return false;
        }

        return true;
    }

    @Override
    protected void _onSleep() { }

    public TransactionDownloader(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        _bitcoinNodeManager = bitcoinNodeManager;
        _databaseManagerFactory = databaseManagerFactory;

        _transactionDownloadedCallback = new BitcoinNode.DownloadTransactionCallback() {
            @Override
            public void onResult(final RequestId requestId, final BitcoinNode bitcoinNode, final Transaction transaction) {
                _pendingRequestInformation.remove(transaction.getHash());

                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    _onTransactionDownloaded(transaction, databaseManager);
                }
                catch (final DatabaseException exception) {
                    Logger.warn(exception);
                    return;
                }
                finally {
                    final Sha256Hash transactionHash = transaction.getHash();

                    final MilliTimer timer = _currentTransactionDownloadSet.remove(transactionHash);

                    if (timer != null) {
                        timer.stop();
                    }

                    Logger.info("Downloaded Transaction: " + transactionHash + " (" + (timer != null ? timer.getMillisecondsElapsed() : "??") + "ms)");

                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }

                final Runnable newTransactionAvailableCallback = _newTransactionAvailableCallback;
                if (newTransactionAvailableCallback != null) {
                    newTransactionAvailableCallback.run();
                }
            }

            @Override
            public void onFailure(final RequestId requestId, final BitcoinNode bitcoinNode, final Sha256Hash transactionHash) {
                if (transactionHash == null) { return; }
                _pendingRequestInformation.remove(transactionHash);

                try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
                    final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();

                    final PendingTransactionId pendingTransactionId = pendingTransactionDatabaseManager.getPendingTransactionId(transactionHash);
                    if (pendingTransactionId == null) {
                        Logger.debug("Unable to increment download failure count for transaction: " + transactionHash);
                        return;
                    }

                    pendingTransactionDatabaseManager.incrementFailedDownloadCount(pendingTransactionId);

                    pendingTransactionDatabaseManager.purgeFailedPendingTransactions(MAX_DOWNLOAD_FAILURE_COUNT);
                }
                catch (final DatabaseException exception) {
                    Logger.warn("Unable to increment download failure count for " + transactionHash + " transactions.", exception);
                }
                finally {
                    _currentTransactionDownloadSet.remove(transactionHash);
                    synchronized (_downloadCallbackPin) {
                        _downloadCallbackPin.notifyAll();
                    }
                }
            }
        };
    }

    public void setNewTransactionAvailableCallback(final Runnable runnable) {
        _newTransactionAvailableCallback = runnable;
    }

    public void submitTransaction(final Transaction transaction) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            _onTransactionDownloaded(transaction, databaseManager);
            Logger.info("Transaction submitted: " + transaction.getHash());
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return;
        }

        final Runnable newTransactionAvailableCallback = _newTransactionAvailableCallback;
        if (newTransactionAvailableCallback != null) {
            newTransactionAvailableCallback.run();
        }
    }

}

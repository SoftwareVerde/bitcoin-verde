package com.softwareverde.bitcoin.server.module.node.sync.transaction;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.chain.time.MedianBlockTime;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.pending.PendingTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.BitcoinNodeManager;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.validator.TransactionValidator;
import com.softwareverde.concurrent.service.SleepyService;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.util.TransactionUtil;
import com.softwareverde.io.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.network.time.NetworkTime;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.MilliTimer;
import com.softwareverde.util.type.time.SystemTime;

import java.util.HashMap;

public class TransactionProcessor extends SleepyService {
    public interface NewTransactionProcessedCallback {
        void onNewTransaction(Transaction transaction);
    }

    protected static final Long MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE = 5000L;

    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final NetworkTime _networkTime;
    protected final MedianBlockTime _medianBlockTime;

    protected final SystemTime _systemTime;
    protected Long _lastOrphanPurgeTime;
    protected NewTransactionProcessedCallback _newTransactionProcessedCallback;

    @Override
    protected void _onStart() { }

    @Override
    public Boolean _run() {
        final Thread thread = Thread.currentThread();

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final DatabaseConnection databaseConnection = databaseManager.getDatabaseConnection();
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            final PendingTransactionDatabaseManager pendingTransactionDatabaseManager = databaseManager.getPendingTransactionDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();
            final BlockHeaderDatabaseManager blockHeaderDatabaseManager = databaseManager.getBlockHeaderDatabaseManager();

            final Long now = _systemTime.getCurrentTimeInMilliSeconds();
            if ((now - _lastOrphanPurgeTime) > MIN_MILLISECONDS_BEFORE_ORPHAN_PURGE) {
                final MilliTimer purgeOrphanedTransactionsTimer = new MilliTimer();
                purgeOrphanedTransactionsTimer.start();
                pendingTransactionDatabaseManager.purgeExpiredOrphanedTransactions();
                purgeOrphanedTransactionsTimer.stop();
                Logger.log("Purge Orphaned Transactions: " + purgeOrphanedTransactionsTimer.getMillisecondsElapsed() + "ms");
                _lastOrphanPurgeTime = _systemTime.getCurrentTimeInMilliSeconds();
            }


            while (! thread.isInterrupted()) {
                final List<PendingTransactionId> pendingTransactionIds = pendingTransactionDatabaseManager.selectCandidatePendingTransactionIds();
                if (pendingTransactionIds.isEmpty()) { return false; }

                final HashMap<Sha256Hash, PendingTransactionId> pendingTransactionIdMap = new HashMap<Sha256Hash, PendingTransactionId>(pendingTransactionIds.getSize());
                final MutableList<Transaction> transactionsToStore = new MutableList<Transaction>(pendingTransactionIds.getSize());
                for (final PendingTransactionId pendingTransactionId : pendingTransactionIds) {
                    if (thread.isInterrupted()) { return false; }

                    final Transaction transaction = pendingTransactionDatabaseManager.getPendingTransaction(pendingTransactionId);
                    if (transaction == null) { continue; }

                    final Boolean transactionCanBeStored = transactionDatabaseManager.previousOutputsExist(transaction);
                    if (! transactionCanBeStored) {
                        pendingTransactionDatabaseManager.updateTransactionDependencies(transaction);
                        continue;
                    }

                    pendingTransactionIdMap.put(transaction.getHash(), pendingTransactionId);
                    transactionsToStore.add(transaction);
                }

                final TransactionValidator transactionValidator = new TransactionValidator(databaseManager, _networkTime, _medianBlockTime);
                transactionValidator.setLoggingEnabled(true);

                final BlockId blockId = blockHeaderDatabaseManager.getHeadBlockHeaderId();
                final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);
                final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);

                final List<NodeId> connectedNodes;
                {
                    final List<BitcoinNode> nodes = _bitcoinNodeManager.getNodes();
                    final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<NodeId>(nodes.getSize());
                    for (final BitcoinNode bitcoinNode : nodes) {
                        nodeIdsBuilder.add(bitcoinNode.getId());
                    }
                    connectedNodes = nodeIdsBuilder.build();
                }

                final HashMap<NodeId, MutableList<Sha256Hash>> nodeUnseenTransactionHashes = new HashMap<NodeId, MutableList<Sha256Hash>>();

                int invalidTransactionCount = 0;
                final MilliTimer storeTransactionsTimer = new MilliTimer();
                storeTransactionsTimer.start();
                for (final Transaction transaction : transactionsToStore) {
                    if (thread.isInterrupted()) { break; }

                    final Sha256Hash transactionHash = transaction.getHash();
                    final PendingTransactionId pendingTransactionId = pendingTransactionIdMap.get(transactionHash);
                    if (pendingTransactionId == null) { continue; }

                    TransactionUtil.startTransaction(databaseConnection);

                    final TransactionId transactionId = transactionDatabaseManager.storeTransaction(transaction);
                    final Boolean transactionIsValid = transactionValidator.validateTransaction(blockchainSegmentId, blockHeight, transaction, true);

                    if (! transactionIsValid) {
                        TransactionUtil.rollbackTransaction(databaseConnection);

                        TransactionUtil.startTransaction(databaseConnection);
                        pendingTransactionDatabaseManager.deletePendingTransaction(pendingTransactionId);
                        TransactionUtil.commitTransaction(databaseConnection);

                        invalidTransactionCount += 1;
                        Logger.log("Invalid MemoryPool Transaction: " + transactionHash);
                        continue;
                    }

                    final Boolean isUnconfirmedTransaction = (transactionDatabaseManager.getBlockId(blockchainSegmentId, transactionId) == null);
                    if (isUnconfirmedTransaction) {
                        transactionDatabaseManager.addToUnconfirmedTransactions(transactionId);
                    }
                    TransactionUtil.commitTransaction(databaseConnection);

                    final List<NodeId> nodesWithoutTransaction = nodeDatabaseManager.filterNodesViaTransactionInventory(connectedNodes, transactionHash, FilterType.KEEP_NODES_WITHOUT_INVENTORY);
                    for (final NodeId nodeId : nodesWithoutTransaction) {
                        final BitcoinNode bitcoinNode = _bitcoinNodeManager.getNode(nodeId);
                        if (bitcoinNode == null) { continue; }
                        if (! bitcoinNode.matchesFilter(transaction)) { continue; }

                        if (! nodeUnseenTransactionHashes.containsKey(nodeId)) {
                            nodeUnseenTransactionHashes.put(nodeId, new MutableList<Sha256Hash>());
                        }

                        final MutableList<Sha256Hash> transactionHashes = nodeUnseenTransactionHashes.get(nodeId);
                        transactionHashes.add(transactionHash);
                    }

                    TransactionUtil.startTransaction(databaseConnection);
                    pendingTransactionDatabaseManager.deletePendingTransaction(pendingTransactionId);
                    TransactionUtil.commitTransaction(databaseConnection);

                    final NewTransactionProcessedCallback newTransactionProcessedCallback = _newTransactionProcessedCallback;
                    if (newTransactionProcessedCallback != null) {
                        newTransactionProcessedCallback.onNewTransaction(transaction);
                    }
                }
                storeTransactionsTimer.stop();

                Logger.log("Committed " + (transactionsToStore.getSize() - invalidTransactionCount) + " transactions to the MemoryPool in " + storeTransactionsTimer.getMillisecondsElapsed() + "ms. (" + String.format("%.2f", (transactionsToStore.getSize() / storeTransactionsTimer.getMillisecondsElapsed().floatValue() * 1000F)) + "tps) (" + invalidTransactionCount + " invalid)");

                for (final NodeId nodeId : nodeUnseenTransactionHashes.keySet()) {
                    final BitcoinNode bitcoinNode = _bitcoinNodeManager.getNode(nodeId);
                    if (bitcoinNode == null) { continue; }
                    if (! Util.coalesce(bitcoinNode.isTransactionRelayEnabled(), false)) { continue; }

                    final List<Sha256Hash> newTransactionHashes = nodeUnseenTransactionHashes.get(nodeId);
                    // Logger.log("Relaying " + newTransactionHashes.getSize() + " Transactions to: " + bitcoinNode.getUserAgent() + " " + bitcoinNode.getConnectionString());
                    bitcoinNode.transmitTransactionHashes(newTransactionHashes);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.log(exception);
        }

        return false;
    }

    @Override
    protected void _onSleep() { }

    public TransactionProcessor(final FullNodeDatabaseManagerFactory databaseManagerFactory, final NetworkTime networkTime, final MedianBlockTime medianBlockTime, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _networkTime = networkTime;
        _medianBlockTime = medianBlockTime;
        _bitcoinNodeManager = bitcoinNodeManager;

        _systemTime = new SystemTime();
        _lastOrphanPurgeTime = 0L;
    }

    public void setNewTransactionProcessedCallback(final NewTransactionProcessedCallback newTransactionProcessedCallback) {
        _newTransactionProcessedCallback = newTransactionProcessedCallback;
    }
}

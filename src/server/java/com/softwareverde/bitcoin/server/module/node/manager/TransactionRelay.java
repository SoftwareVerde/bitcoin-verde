package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.module.node.sync.SlpTransactionProcessor;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidationCache;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.slp.validator.TransactionAccumulator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Util;

import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class TransactionRelay {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final NodeRpcHandler _nodeRpcHandler;
    protected final Boolean _shouldRelayInvalidSlpTransactions;

    protected final Long _batchWindow;
    protected final Thread _batchThread;
    protected final ConcurrentLinkedDeque<Transaction> _queuedTransactions = new ConcurrentLinkedDeque<Transaction>();

    protected void _relayTransactions(final List<Transaction> transactions) {
        final HashMap<NodeId, MutableList<Sha256Hash>> nodeUnseenTransactionHashes = new HashMap<NodeId, MutableList<Sha256Hash>>();
        final MutableList<TransactionWithFee> transactionsToAnnounceViaRpc = new MutableList<TransactionWithFee>((_nodeRpcHandler != null) ? transactions.getCount() : 0);

        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();
            final FullNodeTransactionDatabaseManager transactionDatabaseManager = databaseManager.getTransactionDatabaseManager();

            final TransactionAccumulator transactionAccumulator = SlpTransactionProcessor.createTransactionAccumulator(databaseManager, null);
            final SlpTransactionValidationCache slpTransactionValidationCache = SlpTransactionProcessor.createSlpTransactionValidationCache(databaseManager);
            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);

            final List<NodeId> connectedNodes;
            {
                final List<BitcoinNode> nodes = _bitcoinNodeManager.getNodes();
                final ImmutableListBuilder<NodeId> nodeIdsBuilder = new ImmutableListBuilder<NodeId>(nodes.getCount());
                for (final BitcoinNode bitcoinNode : nodes) {
                    nodeIdsBuilder.add(bitcoinNode.getId());
                }
                connectedNodes = nodeIdsBuilder.build();
            }

            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();

                final Boolean isSlpTransaction = Transaction.isSlpTransaction(transaction);
                if ( isSlpTransaction && (! _shouldRelayInvalidSlpTransactions)) {
                    // NOTE: Parent SlpTransactions may not have been cached within the the SlpTransactionValidator if it is still processing the initial catch-up batch.
                    //  This isn't ideal and may slow down transaction relaying until the SlpTransactionValidator has finished its first run-through...
                    //  TODO: Consider delaying SLP-Relaying by providing a TransactionRelay reference into the SlpTransactionProcessor and invoke ::relaySlpTransactions...
                    final Boolean isValidSlpTransaction = slpTransactionValidator.validateTransaction(transaction);
                    if (! isValidSlpTransaction) {
                        Logger.info("Not relaying invalid SLP Transaction: " + transactionHash);
                        continue;
                    }
                }

                if (_nodeRpcHandler != null) {
                    final Long transactionFee = transactionDatabaseManager.calculateTransactionFee(transaction);
                    transactionsToAnnounceViaRpc.add(new TransactionWithFee(transaction, transactionFee));
                }

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
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
            return;
        }

        for (final NodeId nodeId : nodeUnseenTransactionHashes.keySet()) {
            final BitcoinNode bitcoinNode = _bitcoinNodeManager.getNode(nodeId);
            if (bitcoinNode == null) { continue; }

            if (! Util.coalesce(bitcoinNode.isTransactionRelayEnabled(), false)) { continue; }

            final List<Sha256Hash> newTransactionHashes = nodeUnseenTransactionHashes.get(nodeId);
            bitcoinNode.transmitTransactionHashes(newTransactionHashes);

            // NOTE: It could be beneficial to update the available Node inventory, although is unnecessary unless the same Transaction is attempted to be broadcast multiple times (should never happen).
            // nodeDatabaseManager.updateTransactionInventory(bitcoinNode, newTransactionHashes);
        }

        if (_nodeRpcHandler != null) {
            for (final TransactionWithFee transactionWithFee : transactionsToAnnounceViaRpc) {
                _nodeRpcHandler.onNewTransaction(transactionWithFee);
            }
        }
    }

    protected List<Transaction> _drainQueuedTransactions() {
        final MutableList<Transaction> transactions = new MutableList<Transaction>();
        while (true) {
            final Transaction transaction = _queuedTransactions.pollFirst();
            if (transaction == null) { break; }

            transactions.add(transaction);
        }
        return transactions;
    }

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final NodeRpcHandler nodeRpcHandler, final Boolean shouldRelayInvalidSlpTransactions, final Long batchWindow) {
        _databaseManagerFactory = databaseManagerFactory;
        _bitcoinNodeManager = bitcoinNodeManager;
        _nodeRpcHandler = nodeRpcHandler;
        _shouldRelayInvalidSlpTransactions = shouldRelayInvalidSlpTransactions;
        _batchWindow = batchWindow;

        _batchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (! _batchThread.isInterrupted()) {
                    try {
                        synchronized (_queuedTransactions) {
                            if (_queuedTransactions.isEmpty()) {
                                _queuedTransactions.wait();
                            }
                        }

                        final List<Transaction> transactions = _drainQueuedTransactions();
                        if (! transactions.isEmpty()) { // Should be unnecessary, unless Object::wait spontaneously wakes up.
                            _relayTransactions(transactions); // NOTE: Technically possible to never broadcast drained Transactions if an exception is encountered during relay.

                            if (_batchWindow > 0L) {
                                Thread.sleep(_batchWindow);
                            }
                        }
                    }
                    catch (final Exception exception) {
                        if (exception instanceof InterruptedException) {
                            return;
                        }

                        Logger.debug(exception);
                    }
                }
            }
        });
        _batchThread.setName("TransactionRelay");
        _batchThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread thread, final Throwable exception) {
                Logger.debug(exception);
            }
        });
    }

    public void start() {
        _batchThread.start();
    }

    public void relayTransactions(final List<Transaction> transactions) {
        for (final Transaction transaction : transactions) {
            _queuedTransactions.add(transaction);
        }

        synchronized (_queuedTransactions) {
            _queuedTransactions.notifyAll();
        }
    }

    public void stop() {
        _batchThread.interrupt();

        try {
            _batchThread.join(5000L);
        }
        catch (final Exception exception) {
            Logger.debug(exception);
        }
    }
}

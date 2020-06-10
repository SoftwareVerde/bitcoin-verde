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
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class TransactionRelay {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _bitcoinNodeManager;
    protected final NodeRpcHandler _nodeRpcHandler;
    protected final TransactionWhitelist _transactionWhitelist;
    protected final Boolean _shouldRelayInvalidSlpTransactions;

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        this(databaseManagerFactory, bitcoinNodeManager, null, null, false);
    }

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final RequestDataHandlerMonitor transactionWhitelist) {
        this(databaseManagerFactory, bitcoinNodeManager, transactionWhitelist, null, false);
    }

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final RequestDataHandlerMonitor transactionWhitelist, final NodeRpcHandler nodeRpcHandler) {
        this(databaseManagerFactory, bitcoinNodeManager, transactionWhitelist, nodeRpcHandler, false);
    }

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager, final RequestDataHandlerMonitor transactionWhitelist, final NodeRpcHandler nodeRpcHandler, final Boolean shouldRelayInvalidSlpTransactions) {
        _databaseManagerFactory = databaseManagerFactory;
        _bitcoinNodeManager = bitcoinNodeManager;
        _transactionWhitelist = transactionWhitelist;
        _nodeRpcHandler = nodeRpcHandler;
        _shouldRelayInvalidSlpTransactions = shouldRelayInvalidSlpTransactions;
    }

    public void relayTransactions(final List<Transaction> transactions) {
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

            final MutableList<TransactionWithFee> transactionsToAnnounceViaRpc = new MutableList<TransactionWithFee>((_nodeRpcHandler != null) ? transactions.getCount() : 0);
            final HashMap<NodeId, MutableList<Sha256Hash>> nodeUnseenTransactionHashes = new HashMap<NodeId, MutableList<Sha256Hash>>();
            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();

                if (_transactionWhitelist != null) {
                    _transactionWhitelist.addTransactionHash(transactionHash);
                }

                final Boolean isSlpTransaction = Transaction.isSlpTransaction(transaction);
                if ( (isSlpTransaction) && (! _shouldRelayInvalidSlpTransactions)) {
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

            for (final NodeId nodeId : nodeUnseenTransactionHashes.keySet()) {
                final BitcoinNode bitcoinNode = _bitcoinNodeManager.getNode(nodeId);
                if (bitcoinNode == null) { continue; }

                if (! Util.coalesce(bitcoinNode.isTransactionRelayEnabled(), false)) { continue; }

                final List<Sha256Hash> newTransactionHashes = nodeUnseenTransactionHashes.get(nodeId);
                bitcoinNode.transmitTransactionHashes(newTransactionHashes);
            }

            if (_nodeRpcHandler != null) {
                for (final TransactionWithFee transactionWithFee : transactionsToAnnounceViaRpc) {
                    _nodeRpcHandler.onNewTransaction(transactionWithFee);
                }
            }
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}

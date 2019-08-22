package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.FullNodeTransactionDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.rpc.NodeRpcHandler;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionWithFee;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Util;

import java.util.HashMap;

public class TransactionRelay {
    protected final FullNodeDatabaseManagerFactory _databaseManagerFactory;
    protected final BitcoinNodeManager _bitcoinNodeManager;

    public TransactionRelay(final FullNodeDatabaseManagerFactory databaseManagerFactory, final BitcoinNodeManager bitcoinNodeManager) {
        _databaseManagerFactory = databaseManagerFactory;
        _bitcoinNodeManager = bitcoinNodeManager;
    }

    public void relayTransactions(final List<Transaction> transactions) {
        try (final FullNodeDatabaseManager databaseManager = _databaseManagerFactory.newDatabaseManager()) {
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

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
            for (final Transaction transaction : transactions) {
                final Sha256Hash transactionHash = transaction.getHash();

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
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}

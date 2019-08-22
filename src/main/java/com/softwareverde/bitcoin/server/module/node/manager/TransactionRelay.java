package com.softwareverde.bitcoin.server.module.node.manager;

import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.fullnode.FullNodeDatabaseManagerFactory;
import com.softwareverde.bitcoin.server.module.node.database.node.fullnode.FullNodeBitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.sync.SlpTransactionProcessor;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidationCache;
import com.softwareverde.bitcoin.slp.validator.SlpTransactionValidator;
import com.softwareverde.bitcoin.slp.validator.TransactionAccumulator;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.output.TransactionOutput;
import com.softwareverde.bitcoin.transaction.script.slp.SlpScriptInflater;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableListBuilder;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.logging.Logger;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Container;
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
            final BlockchainDatabaseManager blockchainDatabaseManager = databaseManager.getBlockchainDatabaseManager();
            final FullNodeBitcoinNodeDatabaseManager nodeDatabaseManager = databaseManager.getNodeDatabaseManager();

            final Container<BlockchainSegmentId> blockchainSegmentId = new Container<BlockchainSegmentId>();
            blockchainSegmentId.value = blockchainDatabaseManager.getHeadBlockchainSegmentId();

            final TransactionAccumulator transactionAccumulator = SlpTransactionProcessor.createTransactionAccumulator(blockchainSegmentId, databaseManager, null);
            final SlpTransactionValidationCache slpTransactionValidationCache = SlpTransactionProcessor.createSlpTransactionValidationCache(blockchainSegmentId, databaseManager);
            final SlpTransactionValidator slpTransactionValidator = new SlpTransactionValidator(transactionAccumulator, slpTransactionValidationCache);

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

                final List<TransactionOutput> transactionOutputs = transaction.getTransactionOutputs();
                final TransactionOutput transactionOutput = transactionOutputs.get(0);
                final Boolean isSlpTransaction = SlpScriptInflater.matchesSlpFormat(transactionOutput.getLockingScript());
                if (isSlpTransaction) {
                    final Boolean isValidSlpTransaction = slpTransactionValidator.validateTransaction(transaction);
                    if (! isValidSlpTransaction) {
                        Logger.info("Not relaying invalid SLP Transaction: " + transactionHash);
                        continue;
                    }
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
        }
        catch (final DatabaseException exception) {
            Logger.warn(exception);
        }
    }
}

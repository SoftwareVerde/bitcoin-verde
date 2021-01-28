package com.softwareverde.bitcoin.server.module.node.database.node.fullnode;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.DatabaseConnection;
import com.softwareverde.bitcoin.server.database.query.BatchedInsertQuery;
import com.softwareverde.bitcoin.server.database.query.Query;
import com.softwareverde.bitcoin.server.database.query.ValueExtractor;
import com.softwareverde.bitcoin.server.module.node.database.DatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.BlockRelationship;
import com.softwareverde.bitcoin.server.module.node.database.block.header.BlockHeaderDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.blockchain.BlockchainDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManagerCore;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.immutable.ImmutableList;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.database.row.Row;
import com.softwareverde.network.ip.Ip;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Util;

import java.util.HashSet;

public class FullNodeBitcoinNodeDatabaseManagerCore extends BitcoinNodeDatabaseManagerCore implements FullNodeBitcoinNodeDatabaseManager {
    public FullNodeBitcoinNodeDatabaseManagerCore(final DatabaseManager databaseManager) {
        super(databaseManager);
    }

    @Override
    public Boolean updateBlockInventory(final BitcoinNode node, final Long blockHeight, final Sha256Hash blockHash) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return false; }

        java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, head_block_height, head_block_hash FROM nodes WHERE id = ?")
                .setParameter(nodeId)
        );
        if (rows.isEmpty()) { return false; }
        final Row row = rows.get(0);
        final Long headBlockHeight = row.getLong("head_block_height");
        final Sha256Hash headBlockHash = Sha256Hash.wrap(row.getBytes("head_block_hash"));

        if (headBlockHeight > blockHeight) { return false; }
        if (Util.areEqual(headBlockHeight, blockHeight)) {
            if (Util.areEqual(headBlockHash, blockHash)) {
                return false;
            }
        }

        databaseConnection.executeSql(
            new Query("UPDATE nodes SET head_block_height = ?, head_block_hash = ? WHERE id = ?")
                .setParameter(blockHeight)
                .setParameter(blockHash)
                .setParameter(nodeId)
        );
        return true;
    }

    @Override
    public void updateTransactionInventory(final BitcoinNode node, final List<Sha256Hash> transactionHashes) throws DatabaseException {
        if (transactionHashes.isEmpty()) { return; }

        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final Ip ip = node.getIp();
        final Integer port = node.getPort();

        final NodeId nodeId = _getNodeId(ip, port);
        if (nodeId == null) { return; }

        final BatchedInsertQuery batchedInsertQuery = new BatchedInsertQuery("INSERT IGNORE INTO node_transactions_inventory (node_id, hash) VALUES (?, ?)");
        for (final Sha256Hash transactionHash : transactionHashes) {
            batchedInsertQuery.setParameter(nodeId);
            batchedInsertQuery.setParameter(transactionHash);
        }

        databaseConnection.executeSql(batchedInsertQuery);
    }

    @Override
    public List<NodeId> filterNodesViaTransactionInventory(final List<NodeId> nodeIds, final Sha256Hash transactionHash, final FilterType filterType) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT node_id FROM node_transactions_inventory WHERE hash = ? AND node_id IN (?)")
                .setParameter(transactionHash)
                .setInClauseParameters(nodeIds, ValueExtractor.IDENTIFIER)
        );

        final HashSet<NodeId> filteredNodes = new HashSet<NodeId>(rows.size());
        if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
            for (final NodeId nodeId : nodeIds) {
                filteredNodes.add(nodeId);
            }
        }

        for (final Row row : rows) {
            final NodeId nodeWithTransaction = NodeId.wrap(row.getLong("node_id"));

            if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
                filteredNodes.remove(nodeWithTransaction);
            }
            else {
                filteredNodes.add(nodeWithTransaction);
            }
        }

        return new ImmutableList<NodeId>(filteredNodes);
    }

    @Override
    public List<NodeId> filterNodesViaBlockInventory(final List<NodeId> nodeIds, final Sha256Hash blockHash, final FilterType filterType) throws DatabaseException {
        final DatabaseConnection databaseConnection = _databaseManager.getDatabaseConnection();

        final java.util.List<Row> rows = databaseConnection.query(
            new Query("SELECT id, head_block_height, head_block_hash FROM nodes WHERE id IN (?)")
                .setInClauseParameters(nodeIds, ValueExtractor.IDENTIFIER)
        );

        // TODO: Check for blockchain node feature...

        final HashSet<NodeId> filteredNodes = new HashSet<NodeId>(rows.size());

        final BlockchainDatabaseManager blockchainDatabaseManager = _databaseManager.getBlockchainDatabaseManager();
        final BlockHeaderDatabaseManager blockHeaderDatabaseManager = _databaseManager.getBlockHeaderDatabaseManager();
        final BlockId blockId = blockHeaderDatabaseManager.getBlockHeaderId(blockHash);
        if (blockId == null) { return new MutableList<NodeId>(0); }

        final Long blockHeight = blockHeaderDatabaseManager.getBlockHeight(blockId);
        final BlockchainSegmentId blockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(blockId);

        for (final Row row : rows) {
            final NodeId nodeId = NodeId.wrap(row.getLong("id"));
            final Long nodeBlockHeight = row.getLong("head_block_height");
            final Sha256Hash nodeBlockHash = Sha256Hash.wrap(row.getBytes("head_block_hash"));

            final BlockId nodeBlockId = blockHeaderDatabaseManager.getBlockHeaderId(nodeBlockHash);
            final BlockchainSegmentId nodeBlockchainSegmentId = blockHeaderDatabaseManager.getBlockchainSegmentId(nodeBlockId);
            final Boolean nodeIsOnSameBlockchain = blockchainDatabaseManager.areBlockchainSegmentsConnected(blockchainSegmentId, nodeBlockchainSegmentId, BlockRelationship.ANY);
            final boolean nodeHasBlock = ((nodeBlockHeight >= blockHeight) && Util.coalesce(nodeIsOnSameBlockchain, false));
            // TODO: Nodes that diverged after the desired blockHeight could still serve the block...

            if (filterType == FilterType.KEEP_NODES_WITH_INVENTORY) {
                if (nodeHasBlock) {
                    filteredNodes.add(nodeId);
                }
            }
            else if (filterType == FilterType.KEEP_NODES_WITHOUT_INVENTORY) {
                if (! nodeHasBlock) {
                    filteredNodes.add(nodeId);
                }
            }
        }

        return new ImmutableList<NodeId>(filteredNodes);
    }
}

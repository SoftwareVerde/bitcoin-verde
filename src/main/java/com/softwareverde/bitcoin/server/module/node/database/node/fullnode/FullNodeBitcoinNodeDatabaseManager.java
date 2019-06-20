package com.softwareverde.bitcoin.server.module.node.database.node.fullnode;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.module.node.sync.block.pending.PendingBlockId;
import com.softwareverde.bitcoin.server.module.node.sync.transaction.pending.PendingTransactionId;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;

public interface FullNodeBitcoinNodeDatabaseManager extends BitcoinNodeDatabaseManager {
    Boolean updateBlockInventory(BitcoinNode node, List<PendingBlockId> pendingBlockIds) throws DatabaseException;
    void deleteBlockInventory(PendingBlockId pendingBlockId) throws DatabaseException;
    void deleteBlockInventory(List<PendingBlockId> pendingBlockIds) throws DatabaseException;
    void updateTransactionInventory(BitcoinNode node, List<PendingTransactionId> pendingTransactionIds) throws DatabaseException;
    List<NodeId> filterNodesViaTransactionInventory(List<NodeId> nodeIds, Sha256Hash transactionHash, FilterType filterType) throws DatabaseException;
    List<NodeId> filterNodesViaBlockInventory(List<NodeId> nodeIds, Sha256Hash blockHash, FilterType filterType) throws DatabaseException;
    void deleteTransactionInventory(PendingTransactionId pendingTransactionId) throws DatabaseException;
    void deleteTransactionInventory(List<PendingTransactionId> pendingTransactionIds) throws DatabaseException;
}

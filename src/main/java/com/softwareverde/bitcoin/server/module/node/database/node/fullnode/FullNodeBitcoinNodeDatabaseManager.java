package com.softwareverde.bitcoin.server.module.node.database.node.fullnode;

import com.softwareverde.bitcoin.server.module.node.database.node.BitcoinNodeDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.manager.FilterType;
import com.softwareverde.bitcoin.server.node.BitcoinNode;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;

public interface FullNodeBitcoinNodeDatabaseManager extends BitcoinNodeDatabaseManager {
    Boolean updateBlockInventory(BitcoinNode node, Long blockHeight, Sha256Hash blockHash) throws DatabaseException;
    void updateTransactionInventory(BitcoinNode node, List<Sha256Hash> transactionHashes) throws DatabaseException;
    List<NodeId> filterNodesViaTransactionInventory(List<NodeId> nodeIds, Sha256Hash transactionHash, FilterType filterType) throws DatabaseException;
    List<NodeId> filterNodesViaBlockInventory(List<NodeId> nodeIds, Sha256Hash blockHash, FilterType filterType) throws DatabaseException;
}

package com.softwareverde.bitcoin.server.module.node.database.block.pending;

import com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory.UnknownBlockInventory;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;

public interface PendingBlockDatabaseManager {
    List<UnknownBlockInventory> findUnknownNodeInventoryByPriority(final List<NodeId> connectedNodes) throws DatabaseException;
}

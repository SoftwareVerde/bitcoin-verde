package com.softwareverde.bitcoin.server.module.node.database.block.pending.spv;

import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory.UnknownBlockInventory;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;

public class SpvPendingBlockDatabaseManager implements PendingBlockDatabaseManager {
    public SpvPendingBlockDatabaseManager() { }

    @Override
    public List<UnknownBlockInventory> findUnknownNodeInventoryByPriority(final List<NodeId> connectedNodes) throws DatabaseException {
        return new MutableList<UnknownBlockInventory>(0);
    }
}

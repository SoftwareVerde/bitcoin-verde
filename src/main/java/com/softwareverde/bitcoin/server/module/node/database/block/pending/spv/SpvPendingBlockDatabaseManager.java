package com.softwareverde.bitcoin.server.module.node.database.block.pending.spv;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.module.node.database.block.pending.PendingBlockDatabaseManager;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Tuple;

public class SpvPendingBlockDatabaseManager implements PendingBlockDatabaseManager {
    public SpvPendingBlockDatabaseManager() { }

    @Override
    public List<Tuple<Sha256Hash, Sha256Hash>> selectPriorityPendingBlocksWithUnknownNodeInventory(final List<NodeId> connectedNodes) {
        return new MutableList<Tuple<Sha256Hash, Sha256Hash>>(0);
    }
}

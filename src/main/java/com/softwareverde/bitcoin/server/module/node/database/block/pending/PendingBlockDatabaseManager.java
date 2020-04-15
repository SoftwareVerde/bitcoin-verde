package com.softwareverde.bitcoin.server.module.node.database.block.pending;

import com.softwareverde.bitcoin.server.module.node.database.block.pending.inventory.UnknownBlockInventory;
import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface PendingBlockDatabaseManager {
    ReentrantReadWriteLock.ReadLock READ_WRITE_LOCK = ReadWriteLock.readLock();
    ReentrantReadWriteLock.WriteLock DELETE_LOCK = ReadWriteLock.writeLock();

    List<UnknownBlockInventory> findUnknownNodeInventoryByPriority(final List<NodeId> connectedNodes) throws DatabaseException;
}

class ReadWriteLock {
    protected static final ReentrantReadWriteLock REENTRANT_READ_WRITE_LOCK = new ReentrantReadWriteLock();

    public static ReentrantReadWriteLock.ReadLock readLock() {
        return ReadWriteLock.REENTRANT_READ_WRITE_LOCK.readLock();
    }

    public static ReentrantReadWriteLock.WriteLock writeLock() {
        return ReadWriteLock.REENTRANT_READ_WRITE_LOCK.writeLock();
    }
}

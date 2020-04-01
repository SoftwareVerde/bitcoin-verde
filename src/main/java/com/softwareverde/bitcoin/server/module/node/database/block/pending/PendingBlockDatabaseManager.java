package com.softwareverde.bitcoin.server.module.node.database.block.pending;

import com.softwareverde.constable.list.List;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.security.hash.sha256.Sha256Hash;
import com.softwareverde.util.Tuple;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface PendingBlockDatabaseManager {
    ReentrantReadWriteLock.ReadLock READ_WRITE_LOCK = ReadWriteLock.readLock();
    ReentrantReadWriteLock.WriteLock DELETE_LOCK = ReadWriteLock.writeLock();

    List<Tuple<Sha256Hash, Sha256Hash>> selectPriorityPendingBlocksWithUnknownNodeInventory(final List<NodeId> connectedNodes) throws DatabaseException;
}

class ReadWriteLock {
    protected static final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    public static ReentrantReadWriteLock.ReadLock readLock() {
        return ReadWriteLock.readWriteLock.readLock();
    }

    public static ReentrantReadWriteLock.WriteLock writeLock() {
        return ReadWriteLock.readWriteLock.writeLock();
    }
}

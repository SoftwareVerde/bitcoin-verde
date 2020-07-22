package com.softwareverde.bitcoin.server.module.node.database.block.pending;

import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.database.DatabaseException;
import com.softwareverde.network.p2p.node.NodeId;
import com.softwareverde.util.Tuple;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public interface PendingBlockDatabaseManager {
    // NOTE: Read/Write locks are currently disabled.
    //  Enabling the locks greatly slows down block download and processing, and
    //  there is no clear detriment to keeping the locks disabled.
    ReentrantReadWriteLock.ReadLock READ_LOCK = ReadWriteLock.disabledReadLock();
    ReentrantReadWriteLock.WriteLock WRITE_LOCK = ReadWriteLock.disabledWriteLock();

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

    public static ReentrantReadWriteLock.ReadLock disabledReadLock() {
        return new DisabledReadLock(ReadWriteLock.readWriteLock);
    }

    public static ReentrantReadWriteLock.WriteLock disabledWriteLock() {
        return new DisabledWriteLock(ReadWriteLock.readWriteLock);
    }
}

class DisabledReadLock extends ReentrantReadWriteLock.ReadLock {
    public DisabledReadLock(final ReentrantReadWriteLock lock) {
        super(lock);
    }

    @Override
    public void lock() { }

    @Override
    public void unlock() { }
}

class DisabledWriteLock extends ReentrantReadWriteLock.WriteLock {
    public DisabledWriteLock(final ReentrantReadWriteLock lock) {
        super(lock);
    }

    @Override
    public void lock() { }

    @Override
    public void unlock() { }
}
package com.softwareverde.bitcoin.server.module.node.database.transaction.fullnode.utxo;

import com.softwareverde.util.Container;
import com.softwareverde.util.Util;

import java.util.concurrent.locks.ReentrantReadWriteLock;

class UtxoCacheStaticState {
    public static final ReentrantReadWriteLock.ReadLock READ_LOCK;
    public static final ReentrantReadWriteLock.WriteLock WRITE_LOCK;
    static {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(false);
        READ_LOCK = readWriteLock.readLock();
        WRITE_LOCK = readWriteLock.writeLock();
    }

    // null indicates uninitialized; -1 represents an invalidated set, and must first be cleared (via _clearUncommittedUtxoSet) before any other operations are performed.
    protected static final Container<Long> UNCOMMITTED_UTXO_BLOCK_HEIGHT = new Container<Long>(null);

    protected static Long getUtxoBlockHeight() {
        return Util.coalesce(UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, 0L);
    }

    protected static Boolean isUtxoCacheDefunct() {
        return (Util.coalesce(UNCOMMITTED_UTXO_BLOCK_HEIGHT.value, 0L) <= -1L);
    }

    protected static Boolean isUtxoCacheUninitialized() {
        return (UNCOMMITTED_UTXO_BLOCK_HEIGHT.value == null);
    }

    protected static Boolean isUtxoCacheReady() {
        final Long uncommittedUtxoBlockHeight = UNCOMMITTED_UTXO_BLOCK_HEIGHT.value;
        return ( (uncommittedUtxoBlockHeight != null) && (uncommittedUtxoBlockHeight >= 0) );
    }
}

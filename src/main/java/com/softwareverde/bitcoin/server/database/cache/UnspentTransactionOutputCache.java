package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.util.Util;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UnspentTransactionOutputCache {
    public static final Integer MAX_ITEM_COUNT = (1 << 28); // 268,435,456

    protected UnspentTransactionOutputCache _masterCache = null;

    protected final TreeMap<Sha256Hash, Map<Integer, TransactionOutputId>> _transactionOutputs = new TreeMap<Sha256Hash, Map<Integer, TransactionOutputId>>(new Comparator<Sha256Hash>() {
        @Override
        public int compare(final Sha256Hash sha256Hash0, final Sha256Hash sha256Hash1) {
            for (int i = (Sha256Hash.BYTE_COUNT - 1); i >= 0; --i) {
                final byte b0 = sha256Hash0.getByte(i);
                final byte b1 = sha256Hash1.getByte(i);
                final int compare = (b0 - b1);
                if (compare != 0) { return compare; }
            }
            return 0;
        }
    });

    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

    protected final TreeMap<TransactionOutputId, Sha256Hash> _reverseMap = new TreeMap<TransactionOutputId, Sha256Hash>();
    protected final LinkedList<TransactionOutputId> _invalidatedItems = new LinkedList<TransactionOutputId>();

    protected void _removeTransactionOutputId(final TransactionOutputId transactionOutputId) {
        final Sha256Hash transactionHash = _reverseMap.remove(transactionOutputId);
        if (transactionHash == null) { return; }

        final Map<Integer, TransactionOutputId> map = _transactionOutputs.get(transactionHash);
        for (final Integer transactionOutputIndex : map.keySet()) {
            final TransactionOutputId mapTransactionOutputId = map.get(transactionOutputIndex);
            if (Util.areEqual(transactionOutputId, mapTransactionOutputId)) {
                map.remove(transactionOutputIndex);
                break;
            }
        }
    }

    public UnspentTransactionOutputCache() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();
    }

    public void setMasterCache(final UnspentTransactionOutputCache masterCache) {
        _masterCache = masterCache;
    }

    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        _writeLock.lock();
        Map<Integer, TransactionOutputId> map = _transactionOutputs.get(transactionHash);
        if (map == null) {
            map = new TreeMap<Integer, TransactionOutputId>();
            _transactionOutputs.put(transactionHash, map);
        }

        map.put(transactionOutputIndex, transactionOutputId);
        _reverseMap.put(transactionOutputId, transactionHash);
        _invalidatedItems.remove(transactionOutputId);
        _writeLock.unlock();
    }

    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        _readLock.lock();
        final Map<Integer, TransactionOutputId> map = _transactionOutputs.get(transactionHash);
        if (map != null) {
            final TransactionOutputId transactionOutputId = map.get(transactionOutputIndex);
            if (transactionOutputId != null) {
                _readLock.unlock();
                return transactionOutputId;
            }
        }

        final UnspentTransactionOutputCache masterCache = _masterCache;
        if (masterCache != null) {
            final TransactionOutputId transactionOutputId = masterCache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
            _readLock.unlock();
            return transactionOutputId;
        }

        _readLock.unlock();
        return null;
    }

    public void invalidateUnspentTransactionOutputId(final TransactionOutputId transactionOutputId) {
        _writeLock.lock();
        _removeTransactionOutputId(transactionOutputId);
        _invalidatedItems.addLast(transactionOutputId);
        _writeLock.unlock();
    }

    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputId> transactionOutputIds) {
        _writeLock.lock();
        for (final TransactionOutputId transactionOutputId : transactionOutputIds) {
            _removeTransactionOutputId(transactionOutputId);
            _invalidatedItems.addLast(transactionOutputId);
        }
        _writeLock.unlock();
    }

    /**
     * Absorbs and clears the sourceCache.
     */
    public void commit(final UnspentTransactionOutputCache sourceCache) {
        sourceCache._writeLock.lock();

        _writeLock.lock();
        for (final Sha256Hash transactionHash : sourceCache._transactionOutputs.keySet()) {
            final Map<Integer, TransactionOutputId> sourceMap = sourceCache._transactionOutputs.remove(transactionHash);
            final Map<Integer, TransactionOutputId> existingMap = _transactionOutputs.get(transactionHash);
            if (existingMap == null) {
                _transactionOutputs.put(transactionHash, new TreeMap<Integer, TransactionOutputId>(sourceMap));
            }
            else {
                existingMap.putAll(sourceMap);
            }
        }

        for (final TransactionOutputId transactionOutputId : sourceCache._invalidatedItems) {
            _removeTransactionOutputId(transactionOutputId);
        }
        _writeLock.unlock();

        sourceCache._reverseMap.clear();
        sourceCache._invalidatedItems.clear();
        sourceCache._writeLock.unlock();
    }

    public void clear() {
        _reverseMap.clear();
        _invalidatedItems.clear();
    }
}

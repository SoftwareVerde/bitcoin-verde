package com.softwareverde.bitcoin.server.database.cache.utxo;

import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.Util;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class JvmUnspentTransactionOutputCache implements UnspentTransactionOutputCache {
    protected final ReentrantReadWriteLock.ReadLock _readLock;
    protected final ReentrantReadWriteLock.WriteLock _writeLock;

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

    protected final LinkedList<TransactionOutputIdentifier> _invalidatedItems = new LinkedList<TransactionOutputIdentifier>();

    protected UnspentTransactionOutputCache _masterCache = null;

    protected void _removeTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) {
        final Map<Integer, TransactionOutputId> map = _transactionOutputs.get(transactionOutputIdentifier.getTransactionHash());
        if (map == null) { return; }

        for (final Integer transactionOutputIndex : map.keySet()) {
            final TransactionOutputId mapTransactionOutputId = map.get(transactionOutputIndex);
            if (Util.areEqual(transactionOutputIdentifier.getOutputIndex(), mapTransactionOutputId)) {
                map.remove(transactionOutputIndex);
                break;
            }
        }
    }

    // TODO: Support a max-UTXO count...
    public JvmUnspentTransactionOutputCache() {
        final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        _readLock = readWriteLock.readLock();
        _writeLock = readWriteLock.writeLock();
    }

    @Override
    public void setMasterCache(final UnspentTransactionOutputCache masterCache) {
        _masterCache = masterCache;
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        _writeLock.lock();
        Map<Integer, TransactionOutputId> map = _transactionOutputs.get(transactionHash);
        if (map == null) {
            map = new TreeMap<Integer, TransactionOutputId>();
            _transactionOutputs.put(transactionHash, map);
        }

        map.put(transactionOutputIndex, transactionOutputId);
        _invalidatedItems.remove(new TransactionOutputIdentifier(transactionHash, transactionOutputIndex));
        _writeLock.unlock();
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Long insertId, final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        // TODO: Use the insertId to dictate when the UTXO is ejected by a newer UTXO...
        this.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
    }

    @Override
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

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputId) {
        _writeLock.lock();
        _removeTransactionOutputId(transactionOutputId);
        _invalidatedItems.addLast(transactionOutputId);
        _writeLock.unlock();
    }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        _writeLock.lock();
        for (final TransactionOutputIdentifier transactionOutputId : transactionOutputIdentifiers) {
            _removeTransactionOutputId(transactionOutputId);
            _invalidatedItems.addLast(transactionOutputId);
        }
        _writeLock.unlock();
    }

    @Override
    public void commit(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        if (! (unspentTransactionOutputCache instanceof JvmUnspentTransactionOutputCache)) {
            Logger.warn("Attempted to commit cache of different type.");
            return;
        }

        final JvmUnspentTransactionOutputCache sourceCache = (JvmUnspentTransactionOutputCache) unspentTransactionOutputCache;
        sourceCache._writeLock.lock();

        _writeLock.lock();
        for (final Sha256Hash transactionHash : sourceCache._transactionOutputs.keySet()) {
            final Map<Integer, TransactionOutputId> sourceMap = sourceCache._transactionOutputs.get(transactionHash);
            final Map<Integer, TransactionOutputId> existingMap = _transactionOutputs.get(transactionHash);
            if (existingMap == null) {
                _transactionOutputs.put(transactionHash, new TreeMap<Integer, TransactionOutputId>(sourceMap));
            }
            else {
                existingMap.putAll(sourceMap);
            }
        }

        for (final TransactionOutputIdentifier transactionOutputId : sourceCache._invalidatedItems) {
            _removeTransactionOutputId(transactionOutputId);
        }
        _writeLock.unlock();

        sourceCache._transactionOutputs.clear();
        sourceCache._invalidatedItems.clear();
        sourceCache._writeLock.unlock();
    }

    @Override
    public void commit() {
        _writeLock.lock();
        _invalidatedItems.clear();
        _writeLock.unlock();
    }

    @Override
    public MemoryStatus getMemoryStatus() {
        return null;
    }

    @Override
    public void pruneHalf() {
        boolean shouldPrune = true;
        for (final Sha256Hash key0 : _transactionOutputs.keySet()) {
            final Map<Integer, TransactionOutputId> subMap = _transactionOutputs.get(key0);

            for (final Integer key1 : subMap.keySet()) {
                if (shouldPrune) {
                    subMap.remove(key1);
                }
                shouldPrune = (! shouldPrune);
            }

            if (subMap.isEmpty()) {
                _transactionOutputs.remove(key0);
            }
        }
    }

    @Override
    public UtxoCount getMaxUtxoCount() {
        return null;
    }

    @Override
    public void close() {
        _writeLock.lock();
        _transactionOutputs.clear();
        _invalidatedItems.clear();
        _writeLock.unlock();
    }
}

package com.softwareverde.bitcoin.server.database.cache.conscientious;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;

public class ConscientiousUnspentTransactionOutputCache<T, S> implements UnspentTransactionOutputCache {

    protected final MemoryStatus _memoryStatus;
    protected final Float _memoryPercentThreshold;
    protected final UnspentTransactionOutputCache _cache;

    protected void _pruneHalf() {
        Logger.log("NOTICE: Pruning cache by half: " + _cache.toString());
        _memoryStatus.logCurrentMemoryUsage();

        _cache.pruneHalf();
    }

    protected ConscientiousUnspentTransactionOutputCache(final UnspentTransactionOutputCache cache, final Float memoryPercentThreshold) {
        _cache = cache;
        _memoryStatus = cache.getMemoryStatus();
        _memoryPercentThreshold = memoryPercentThreshold;
    }

    public Float getMemoryPercentThreshold() {
        return _memoryPercentThreshold;
    }

    public UnspentTransactionOutputCache unwrap() {
        return _cache;
    }

    @Override
    public void setMasterCache(final UnspentTransactionOutputCache masterCache) {
        _cache.setMasterCache(masterCache);
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final boolean isAboveThreshold = (_memoryStatus.getMemoryUsedPercent() >= _memoryPercentThreshold);
        if (isAboveThreshold) {
            _pruneHalf();
        }

        _cache.cacheUnspentTransactionOutputId(transactionHash, transactionOutputIndex, transactionOutputId);
    }

    @Override
    public void cacheUnspentTransactionOutputId(final Long insertId, final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        final boolean isAboveThreshold = (_memoryStatus.getMemoryUsedPercent() >= _memoryPercentThreshold);
        if (isAboveThreshold) {
            _pruneHalf();
        }

        _cache.cacheUnspentTransactionOutputId(insertId, transactionHash, transactionOutputIndex, transactionOutputId);
    }

    @Override
    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        return _cache.getCachedUnspentTransactionOutputId(transactionHash, transactionOutputIndex);
    }

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) {
        _cache.invalidateUnspentTransactionOutputId(transactionOutputIdentifier);
    }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) {
        _cache.invalidateUnspentTransactionOutputIds(transactionOutputIdentifiers);
    }

    @Override
    public void commit(final UnspentTransactionOutputCache sourceCache) {
        _cache.commit(sourceCache);
    }

    @Override
    public void commit() {
        _cache.commit();
    }

    @Override
    public MemoryStatus getMemoryStatus() {
        return _memoryStatus;
    }

    @Override
    public void pruneHalf() {
        _cache.pruneHalf();
    }

    @Override
    public UtxoCount getMaxUtxoCount() {
        return null;
    }

    @Override
    public void close() {
        _cache.close();
    }
}

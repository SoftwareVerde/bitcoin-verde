package com.softwareverde.bitcoin.server.database.cache.conscientious;

import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;

public class DisabledUnspentTransactionOutputCache implements UnspentTransactionOutputCache {
    protected final UtxoCount _maxUtxoCount = UtxoCount.wrap(0L);

    @Override
    public void setMasterCache(final UnspentTransactionOutputCache masterCache) { }

    @Override
    public void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public void cacheUnspentTransactionOutputId(final Long insertId, final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) { }

    @Override
    public TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) { return null; }

    @Override
    public void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputIdentifier) { }

    @Override
    public void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIdentifiers) { }

    @Override
    public void commit(final UnspentTransactionOutputCache sourceCache) { }

    @Override
    public void commit() { }

    @Override
    public MemoryStatus getMemoryStatus() { return null; }

    @Override
    public void pruneHalf() { }

    @Override
    public UtxoCount getMaxUtxoCount() {
        return _maxUtxoCount;
    }

    @Override
    public void close() { }
}

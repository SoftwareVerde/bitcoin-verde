package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.server.database.cache.utxo.JvmUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.NativeUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;

public class MasterDatabaseManagerCache implements AutoCloseable {
    protected static <T, S> void _commitToCache(final MutableCache<T, S> cache, final MutableCache<T, S> destination) {
        if (cache.masterCacheWasInvalidated()) {
            destination.invalidate();
        }
        for (final T key : cache.getKeys()) {
            final S value = cache.removeItem(key);
            destination.cacheItem(key, value);
        }
    }

    protected final MutableCache<ImmutableSha256Hash, TransactionId> _transactionIdCache                            = new HashMapCache<ImmutableSha256Hash, TransactionId>("TransactionIdCache", 128000);
    protected final MutableCache<TransactionId, ImmutableTransaction> _transactionCache                             = new HashMapCache<TransactionId, ImmutableTransaction>("TransactionCache", 128000);
    protected final MutableCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache  = new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId", 128000);
    protected final MutableCache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache                     = new HashMapCache<BlockId, BlockchainSegmentId>("BlockId-BlockchainSegmentId", 2048);
    protected final MutableCache<String, AddressId> _addressIdCache                                                 = new DisabledCache<String, AddressId>();
    protected final MutableCache<BlockId, Long> _blockHeightCache                                                   = new HashMapCache<BlockId, Long>("BlockHeightCache", 2048);
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache;

    protected final Long _maxCachedUtxoCount;

    public MasterDatabaseManagerCache(final Long maxUtxoCacheByteCount) {
        final Long maxUtxoCount = NativeUnspentTransactionOutputCache.calculateMaxUtxoCountFromMemoryUsage(maxUtxoCacheByteCount);
        _maxCachedUtxoCount = maxUtxoCount;
        _unspentTransactionOutputCache = ((NativeUnspentTransactionOutputCache.isEnabled()) ? new NativeUnspentTransactionOutputCache(maxUtxoCount) : new JvmUnspentTransactionOutputCache());
    }

    public Cache<TransactionId, ImmutableTransaction> getTransactionCache() { return _transactionCache; }
    public Cache<ImmutableSha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }
    public Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }
    public Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() { return _blockIdBlockchainSegmentIdCache; }
    public Cache<String, AddressId> getAddressIdCache() { return _addressIdCache; }
    public Cache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }
    public UnspentTransactionOutputCache getUnspentTransactionOutputCache() { return _unspentTransactionOutputCache; }

    public void commitLocalDatabaseManagerCache(final LocalDatabaseManagerCache localDatabaseManagerCache) {
        _commitToCache(localDatabaseManagerCache.getTransactionIdCache(), _transactionIdCache);
        _commitToCache(localDatabaseManagerCache.getTransactionCache(), _transactionCache);
        _commitToCache(localDatabaseManagerCache.getTransactionOutputIdCache(), _transactionOutputIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache(), _blockIdBlockchainSegmentIdCache);
        _commitToCache(localDatabaseManagerCache.getAddressIdCache(), _addressIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockHeightCache(), _blockHeightCache);

        _unspentTransactionOutputCache.commit(localDatabaseManagerCache.getUnspentTransactionOutputCache());
    }

    public void commit() {
        _unspentTransactionOutputCache.commit();
    }

    public Long getMaxCachedUtxoCount() {
        return _maxCachedUtxoCount;
    }

    @Override
    public void close() {
        _unspentTransactionOutputCache.close();
    }
}

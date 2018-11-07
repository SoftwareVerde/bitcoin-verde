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
    protected final MutableCache<ImmutableSha256Hash, TransactionId> _transactionIdCache                            = new HashMapCache<ImmutableSha256Hash, TransactionId>("TransactionIdCache", 32000);
    protected final MutableCache<TransactionId, ImmutableTransaction> _transactionCache                             = new HashMapCache<TransactionId, ImmutableTransaction>("TransactionCache", 32000);
    protected final MutableCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache  = new DisabledCache<CachedTransactionOutputIdentifier, TransactionOutputId>(); // new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId", HashMapCache.DISABLED_CACHE_SIZE);
    protected final MutableCache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache                     = new HashMapCache<BlockId, BlockchainSegmentId>("BlockId-BlockchainSegmentId", 2016);
    protected final MutableCache<String, AddressId> _addressIdCache                                                 = new DisabledCache<String, AddressId>(); // new HashMapCache<String, AddressId>("AddressId", HashMapCache.DISABLED_CACHE_SIZE);
    protected final MutableCache<BlockId, Long> _blockHeightCache                                                   = new HashMapCache<BlockId, Long>("BlockHeightCache", 550000);
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache;

    protected static <T, S> void _commitToCache(final MutableCache<T, S> cache, final MutableCache<T, S> destination) {
        if (cache.masterCacheWasInvalidated()) {
            destination.invalidate();
        }
        for (final T key : cache.getKeys()) {
            final S value = cache.removeItem(key);
            destination.cacheItem(key, value);
        }
    }

    public MasterDatabaseManagerCache() {
        _unspentTransactionOutputCache = ((NativeUnspentTransactionOutputCache.isEnabled()) ? new NativeUnspentTransactionOutputCache() : new JvmUnspentTransactionOutputCache());
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

    @Override
    public void close() {
        _unspentTransactionOutputCache.close();
    }
}

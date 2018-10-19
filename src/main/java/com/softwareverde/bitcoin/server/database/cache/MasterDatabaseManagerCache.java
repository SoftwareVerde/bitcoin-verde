package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockChainSegmentId;
import com.softwareverde.bitcoin.transaction.ImmutableTransaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;

public class MasterDatabaseManagerCache {
    protected final HashMapCache<ImmutableSha256Hash, TransactionId> _transactionIdCache = new HashMapCache<ImmutableSha256Hash, TransactionId>("TransactionIdCache", HashMapCache.DEFAULT_CACHE_SIZE);
    protected final HashMapCache<TransactionId, ImmutableTransaction> _transactionCache = new HashMapCache<TransactionId, ImmutableTransaction>("TransactionCache", HashMapCache.DEFAULT_CACHE_SIZE);
    protected final HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache = new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId", HashMapCache.DISABLED_CACHE_SIZE);
    protected final HashMapCache<BlockId, BlockChainSegmentId> _blockIdBlockChainSegmentIdCache = new HashMapCache<BlockId, BlockChainSegmentId>("BlockId-BlockChainSegmentId", 1460);
    protected final HashMapCache<String, AddressId> _addressIdCache = new HashMapCache<String, AddressId>("AddressId", HashMapCache.DISABLED_CACHE_SIZE);
    protected final HashMapCache<BlockId, Long> _blockHeightCache = new HashMapCache<BlockId, Long>("BlockHeightCache", 500000);
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache = new UnspentTransactionOutputCache();

    protected static <T, S> void _commitToCache(final HashMapCache<T, S> cache, final HashMapCache<T, S> destination) {
        if (cache.masterCacheWasInvalidated()) {
            destination.invalidate();
        }
        for (final T key : cache.getKeys()) {
            final S value = cache.removeItem(key);
            destination.cacheItem(key, value);
        }
    }

    public Cache<TransactionId, ImmutableTransaction> getTransactionCache() { return _transactionCache; }
    public Cache<ImmutableSha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }
    public Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }
    public Cache<BlockId, BlockChainSegmentId> getBlockIdBlockChainSegmentIdCache() { return _blockIdBlockChainSegmentIdCache; }
    public Cache<String, AddressId> getAddressIdCache() { return _addressIdCache; }
    public Cache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }
    public UnspentTransactionOutputCache getUnspentTransactionOutputCache() { return _unspentTransactionOutputCache; }

    public void commitLocalDatabaseManagerCache(final LocalDatabaseManagerCache localDatabaseManagerCache) {
        _commitToCache(localDatabaseManagerCache.getTransactionIdCache(), _transactionIdCache);
        _commitToCache(localDatabaseManagerCache.getTransactionCache(), _transactionCache);
        _commitToCache(localDatabaseManagerCache.getTransactionOutputIdCache(), _transactionOutputIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockIdBlockChainSegmentIdCache(), _blockIdBlockChainSegmentIdCache);
        _commitToCache(localDatabaseManagerCache.getAddressIdCache(), _addressIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockHeightCache(), _blockHeightCache);

        _unspentTransactionOutputCache.commit(localDatabaseManagerCache.getUnspentTransactionOutputCache());
    }
}

package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.transaction.ConstTransaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;

public class MasterDatabaseManagerCacheCore implements MasterDatabaseManagerCache {
    protected static <T, S> void _commitToCache(final MutableCache<T, S> cache, final MutableCache<T, S> destination) {
        if (cache.masterCacheWasInvalidated()) {
            destination.invalidate();
        }
        for (final T key : cache.getKeys()) {
            final S value = cache.removeItem(key);
            destination.cacheItem(key, value);
        }
    }

    protected final MutableCache<ImmutableSha256Hash, TransactionId> _transactionIdCache;
    protected final MutableCache<TransactionId, ConstTransaction> _transactionCache;
    protected final MutableCache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache;
    protected final MutableCache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache;
    protected final MutableCache<String, AddressId> _addressIdCache;
    protected final MutableCache<BlockId, Long> _blockHeightCache;

    public MasterDatabaseManagerCacheCore() {
        // final MemoryStatus memoryStatus = new JvmMemoryStatus();

        _transactionIdCache                 = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new HashMapCache<ImmutableSha256Hash, TransactionId>(                    "TransactionIdCache",           128000), memoryStatus);
        _transactionCache                   = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new HashMapCache<TransactionId, ConstTransaction>(                   "TransactionCache",             128000), memoryStatus);
        _transactionOutputIdCache           = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId",          128000), memoryStatus);
        _blockIdBlockchainSegmentIdCache    = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new HashMapCache<BlockId, BlockchainSegmentId>(                          "BlockId-BlockchainSegmentId",  2048), memoryStatus);
        _blockHeightCache                   = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new HashMapCache<BlockId, Long>(                                         "BlockHeightCache",             2048), memoryStatus);
        _addressIdCache                     = new DisabledCache<>(); // MemoryConscientiousCache.wrap(0.95F, new DisabledCache<String, AddressId>(), memoryStatus);
    }

    @Override
    public Cache<TransactionId, ConstTransaction> getTransactionCache() { return _transactionCache; }

    @Override
    public Cache<ImmutableSha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }

    @Override
    public Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }

    @Override
    public Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() { return _blockIdBlockchainSegmentIdCache; }

    @Override
    public Cache<String, AddressId> getAddressIdCache() { return _addressIdCache; }

    @Override
    public Cache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }

    @Override
    public void commitLocalDatabaseManagerCache(final LocalDatabaseManagerCache localDatabaseManagerCache) {
        _commitToCache(localDatabaseManagerCache.getTransactionIdCache(), _transactionIdCache);
        _commitToCache(localDatabaseManagerCache.getTransactionCache(), _transactionCache);
        _commitToCache(localDatabaseManagerCache.getTransactionOutputIdCache(), _transactionOutputIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache(), _blockIdBlockchainSegmentIdCache);
        _commitToCache(localDatabaseManagerCache.getAddressIdCache(), _addressIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockHeightCache(), _blockHeightCache);
    }

    @Override
    public void commit() {
        // Nothing.
    }

    @Override
    public void close() {
        // Nothing.
    }
}

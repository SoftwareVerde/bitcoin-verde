package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.utxo.DisabledUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCacheFactory;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.util.Util;

public class MasterDatabaseManagerCacheCore implements MasterDatabaseManagerCache {
    public enum CacheSize {
        DISABLED, SMALL, MEDIUM, LARGE
    }

    public static class CacheConfiguration {
        protected Integer _transactionIdCacheItemCount;
        protected Integer _transactionCacheItemCount;
        protected Integer _transactionOutputIdCacheItemCount;
        protected Integer _blockIdBlockchainSegmentIdCacheItemCount;
        protected Integer _blockHeightCacheItemCount;
        protected Integer _addressIdCacheItemCount;

        public CacheConfiguration() { }

        public Integer getTransactionIdCacheItemCount() { return _transactionIdCacheItemCount; }
        public Integer getTransactionCacheItemCount() { return _transactionCacheItemCount; }
        public Integer getTransactionOutputIdCacheItemCount() { return _transactionOutputIdCacheItemCount; }
        public Integer getBlockIdBlockchainSegmentIdCacheItemCount() { return _blockIdBlockchainSegmentIdCacheItemCount; }
        public Integer getBlockHeightCacheItemCount() { return _blockHeightCacheItemCount; }
        public Integer getAddressIdCacheItemCount() { return _addressIdCacheItemCount; }
    }

    protected static CacheConfiguration getCacheConfiguration(final CacheSize cacheSize) {
        final CacheConfiguration cacheConfiguration = new CacheConfiguration();

        switch (cacheSize) {
            case DISABLED: {
                cacheConfiguration._transactionIdCacheItemCount = null;
                cacheConfiguration._transactionCacheItemCount = null;
                cacheConfiguration._transactionOutputIdCacheItemCount = null;
                cacheConfiguration._blockIdBlockchainSegmentIdCacheItemCount = null;
                cacheConfiguration._blockHeightCacheItemCount = null;
                cacheConfiguration._addressIdCacheItemCount = null;
            } break;

            case SMALL: {
                // A Small cache stores about one 32MB block data.
                cacheConfiguration._transactionIdCacheItemCount = 128000;
                cacheConfiguration._transactionCacheItemCount = 128000;
                cacheConfiguration._transactionOutputIdCacheItemCount = 256000;
                cacheConfiguration._blockIdBlockchainSegmentIdCacheItemCount = 2048;
                cacheConfiguration._blockHeightCacheItemCount = 2048;
                cacheConfiguration._addressIdCacheItemCount = null;
            } break;

            case MEDIUM: {
                // A Medium cache stores about one day's worth of 32MB block data.
                cacheConfiguration._transactionIdCacheItemCount = 18432000;
                cacheConfiguration._transactionCacheItemCount = 18432000;
                cacheConfiguration._transactionOutputIdCacheItemCount = 36864000;
                cacheConfiguration._blockIdBlockchainSegmentIdCacheItemCount = 4096;
                cacheConfiguration._blockHeightCacheItemCount = 4096;
                cacheConfiguration._addressIdCacheItemCount = null;
            } break;

            case LARGE: {
                // A Large cache stores about one weeks's worth of 32MB block data.
                cacheConfiguration._transactionIdCacheItemCount = 129024000;
                cacheConfiguration._transactionCacheItemCount = 129024000;
                cacheConfiguration._transactionOutputIdCacheItemCount = 258048000;
                cacheConfiguration._blockIdBlockchainSegmentIdCacheItemCount = 4096;
                cacheConfiguration._blockHeightCacheItemCount = 4096;
                cacheConfiguration._addressIdCacheItemCount = null;
            } break;
        }

        return cacheConfiguration;
    }

    protected static <T, S> Cache<T, S> newCache(final String cacheName, final Integer cacheSize) {
        if (Util.coalesce(cacheSize) < 1) {
            return new DisabledCache<T, S>();
        }

        return new HashMapCache<T, S>(cacheName, cacheSize);
    }

    protected static <T, S> void _commitToCache(final Cache<T, S> cache, final Cache<T, S> destination) {
        for (final T key : cache.getKeys()) {
            final S value = cache.remove(key);
            destination.set(key, value);
        }
    }

    protected final UnspentTransactionOutputCacheFactory _unspentTransactionOutputCacheFactory;
    protected final Cache<Sha256Hash, TransactionId> _transactionIdCache;
    protected final Cache<TransactionId, Transaction> _transactionCache;
    protected final Cache<CachedTransactionOutputIdentifier, TransactionOutputId> _transactionOutputIdCache;
    protected final Cache<BlockId, BlockchainSegmentId> _blockIdBlockchainSegmentIdCache;
    protected final Cache<String, AddressId> _addressIdCache;
    protected final Cache<BlockId, Long> _blockHeightCache;
    protected final UnspentTransactionOutputCache _unspentTransactionOutputCache;

    protected final UtxoCount _maxCachedUtxoCount;

    public MasterDatabaseManagerCacheCore() {
        this(null, CacheSize.SMALL);
    }

    public MasterDatabaseManagerCacheCore(final UnspentTransactionOutputCacheFactory unspentTransactionOutputCacheFactory) {
        this(unspentTransactionOutputCacheFactory, CacheSize.MEDIUM);
    }

    public MasterDatabaseManagerCacheCore(final UnspentTransactionOutputCacheFactory unspentTransactionOutputCacheFactory, final CacheSize cacheSize) {
        final CacheConfiguration cacheConfiguration = MasterDatabaseManagerCacheCore.getCacheConfiguration(cacheSize);

        _transactionIdCache                 = MasterDatabaseManagerCacheCore.newCache("TransactionIdCache", cacheConfiguration.getTransactionIdCacheItemCount());
        _transactionCache                   = MasterDatabaseManagerCacheCore.newCache("TransactionCache", cacheConfiguration.getTransactionCacheItemCount());
        _transactionOutputIdCache           = MasterDatabaseManagerCacheCore.newCache("TransactionOutputIdCache", cacheConfiguration.getTransactionOutputIdCacheItemCount());
        _blockIdBlockchainSegmentIdCache    = MasterDatabaseManagerCacheCore.newCache("BlockchainSegmentIdCache", cacheConfiguration.getBlockIdBlockchainSegmentIdCacheItemCount());
        _blockHeightCache                   = MasterDatabaseManagerCacheCore.newCache("BlockHeightCache", cacheConfiguration.getBlockHeightCacheItemCount());
        _addressIdCache                     = MasterDatabaseManagerCacheCore.newCache("AddressIdCache", cacheConfiguration.getAddressIdCacheItemCount());

        _unspentTransactionOutputCacheFactory = Util.coalesce(unspentTransactionOutputCacheFactory, DisabledUnspentTransactionOutputCache.FACTORY);
        _unspentTransactionOutputCache = _unspentTransactionOutputCacheFactory.newUnspentTransactionOutputCache();
        _maxCachedUtxoCount = _unspentTransactionOutputCache.getMaxUtxoCount();
    }

    @Override
    public Cache<TransactionId, Transaction> getTransactionCache() { return _transactionCache; }
    @Override
    public Cache<Sha256Hash, TransactionId> getTransactionIdCache() { return _transactionIdCache; }
    @Override
    public Cache<CachedTransactionOutputIdentifier, TransactionOutputId> getTransactionOutputIdCache() { return _transactionOutputIdCache; }
    @Override
    public Cache<BlockId, BlockchainSegmentId> getBlockIdBlockchainSegmentIdCache() { return _blockIdBlockchainSegmentIdCache; }
    @Override
    public Cache<String, AddressId> getAddressIdCache() { return _addressIdCache; }
    @Override
    public Cache<BlockId, Long> getBlockHeightCache() { return _blockHeightCache; }
    @Override
    public UnspentTransactionOutputCache getUnspentTransactionOutputCache() { return _unspentTransactionOutputCache; }

    @Override
    public void commitLocalDatabaseManagerCache(final LocalDatabaseManagerCache localDatabaseManagerCache) {
        _commitToCache(localDatabaseManagerCache.getTransactionIdCache(), _transactionIdCache);
        _commitToCache(localDatabaseManagerCache.getTransactionCache(), _transactionCache);
        _commitToCache(localDatabaseManagerCache.getTransactionOutputIdCache(), _transactionOutputIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockIdBlockchainSegmentIdCache(), _blockIdBlockchainSegmentIdCache);
        _commitToCache(localDatabaseManagerCache.getAddressIdCache(), _addressIdCache);
        _commitToCache(localDatabaseManagerCache.getBlockHeightCache(), _blockHeightCache);

        _unspentTransactionOutputCache.commit(localDatabaseManagerCache.getUnspentTransactionOutputCache());
    }

    @Override
    public void commit() {
        _unspentTransactionOutputCache.commit();
    }

    @Override
    public UtxoCount getMaxCachedUtxoCount() {
        return _maxCachedUtxoCount;
    }

    @Override
    public UnspentTransactionOutputCache newUnspentTransactionOutputCache() {
        return _unspentTransactionOutputCacheFactory.newUnspentTransactionOutputCache();
    }

    @Override
    public void close() {
        _unspentTransactionOutputCache.close();
    }
}

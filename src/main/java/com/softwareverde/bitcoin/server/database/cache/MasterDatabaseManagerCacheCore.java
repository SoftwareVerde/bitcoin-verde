package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.address.AddressId;
import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.chain.segment.BlockchainSegmentId;
import com.softwareverde.bitcoin.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.server.database.cache.conscientious.MemoryConscientiousCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.DisabledUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCacheFactory;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.memory.JvmMemoryStatus;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.bitcoin.transaction.Transaction;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.util.Util;

public class MasterDatabaseManagerCacheCore implements MasterDatabaseManagerCache {
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
        this(null);
    }

    public MasterDatabaseManagerCacheCore(final UnspentTransactionOutputCacheFactory unspentTransactionOutputCacheFactory) {
        final MemoryStatus memoryStatus = new JvmMemoryStatus();

        _transactionIdCache                 = MemoryConscientiousCache.wrap(0.95F, new HashMapCache<Sha256Hash, TransactionId>(                    "TransactionIdCache",           128000), memoryStatus);
        _transactionCache                   = MemoryConscientiousCache.wrap(0.95F, new HashMapCache<TransactionId, Transaction>(                   "TransactionCache",             128000), memoryStatus);
        _transactionOutputIdCache           = MemoryConscientiousCache.wrap(0.95F, new HashMapCache<CachedTransactionOutputIdentifier, TransactionOutputId>("TransactionOutputId",          128000), memoryStatus);
        _blockIdBlockchainSegmentIdCache    = MemoryConscientiousCache.wrap(0.95F, new HashMapCache<BlockId, BlockchainSegmentId>(                          "BlockId-BlockchainSegmentId",  2048), memoryStatus);
        _blockHeightCache                   = MemoryConscientiousCache.wrap(0.95F, new HashMapCache<BlockId, Long>(                                         "BlockHeightCache",             2048), memoryStatus);
        _addressIdCache                     = MemoryConscientiousCache.wrap(0.95F, new DisabledCache<String, AddressId>(), memoryStatus);

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

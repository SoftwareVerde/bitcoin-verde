package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.server.database.cache.recency.RecentItemTracker;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;

import java.util.HashMap;
import java.util.Map;

public class TransactionIdCache {
    public static final Integer DEFAULT_CACHE_SIZE = 65536;

    public final Object MUTEX = new Object();

    protected final Integer _cacheMaxSize = DEFAULT_CACHE_SIZE;
    protected final HashMap<Sha256Hash, Map<BlockId, TransactionId>> _cache = new HashMap<Sha256Hash, Map<BlockId, TransactionId>>(_cacheMaxSize);
    protected final RecentItemTracker<Sha256Hash> _recentHashes = new RecentItemTracker<Sha256Hash>(_cacheMaxSize);
    protected int _cacheSize = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;

    public void clear() {
        _cache.clear();
        _cacheSize = 0;
        _recentHashes.clear();

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;

        _recentHashes.clearDebug();
    }

    public void invalidateTransaction(final Sha256Hash transactionHash) {
        _cache.remove(transactionHash);
    }

    public void cacheTransactionId(final BlockId blockId, final TransactionId transactionId, final Sha256Hash sha256Hash) {
        final ImmutableSha256Hash transactionHash = sha256Hash.asConst();

        synchronized (MUTEX) {
            _recentHashes.markRecent(transactionHash);

            if (_cache.containsKey(transactionHash)) {
                return;
            }

            if (_cacheSize >= _cacheMaxSize) {
                final Sha256Hash oldestHash = _recentHashes.getOldestItem();
                _cache.remove(oldestHash);
                _cacheSize -= 1;
            }

            final Map<BlockId, TransactionId> subMap;
            {
                final Map<BlockId, TransactionId> map = _cache.get(transactionHash);
                subMap = (map != null ? map : new HashMap<BlockId, TransactionId>());
            }

            subMap.put(blockId, transactionId);

            _cache.put(transactionHash, subMap);
            _cacheSize += 1;
        }
    }

    public TransactionId getCachedTransactionId(final BlockId blockId, final Sha256Hash transactionHash) {
        synchronized (MUTEX) {
            _cacheQueryCount += 1;

            if (! _cache.containsKey(transactionHash)) {
                _cacheMissCount += 1;
                return null;
            }

            _recentHashes.markRecent(transactionHash);
            final Map<BlockId, TransactionId> subMap = _cache.get(transactionHash);
            final TransactionId transactionId = subMap.get(blockId);

            return transactionId;
        }
    }

    public Map<BlockId, TransactionId> getCachedTransactionIds(final Sha256Hash transactionHash) {
        synchronized (MUTEX) {
            _cacheQueryCount += 1;

            if (! _cache.containsKey(transactionHash)) {
                _cacheMissCount += 1;
                return null;
            }

            _recentHashes.markRecent(transactionHash);
            final Map<BlockId, TransactionId> subMap = new HashMap<BlockId, TransactionId>(_cache.get(transactionHash));

            return subMap;
        }
    }

    public Integer getSize() {
        return _cache.size();
    }

    public void debug() {
        Logger.log("TransactionCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _cache.size() + " | Time Spent Searching: " + _recentHashes.getMsSpentSearching());
    }
}

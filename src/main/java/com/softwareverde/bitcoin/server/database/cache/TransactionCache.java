package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.timer.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionCache {
    protected final Object _mutex = new Object();
    protected final Integer _maxCachedItemCount;
    protected final HashMap<Sha256Hash, Map<BlockId, TransactionId>> _cache = new HashMap<Sha256Hash, Map<BlockId, TransactionId>>();

    protected int _itemCount = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;
    protected double _msSpentSearching = 0D;

    /**
     * Reduces the items within the cache to at least one less than the _maxCachedItemCount.
     */
    protected void _reduceCachedItems() {
        final Timer timer = new Timer();
        timer.start();

        final List<Sha256Hash> transactionHashes = new ArrayList<Sha256Hash>(_cache.keySet());

        while (_itemCount >= _maxCachedItemCount) {
            final Sha256Hash transactionHash = transactionHashes.remove(0);
            final Map<BlockId, TransactionId> subMap = _cache.remove(transactionHash);
            _itemCount -= subMap.size();
        }

        timer.stop();
        _msSpentSearching += timer.getMillisecondsElapsed();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;
        _msSpentSearching = 0D;
    }

    public TransactionCache(final Integer maxCachedItemCount) {
        _maxCachedItemCount = maxCachedItemCount;
    }

    public void clear() {
        synchronized (_mutex) {
            _cache.clear();
            _itemCount = 0;

            _clearDebug();
        }
    }

    public void clearDebug() {
        _clearDebug();
    }

    public void cacheTransactionId(final BlockId blockId, final TransactionId transactionId, final Sha256Hash sha256Hash) {
        final ImmutableSha256Hash transactionHash = sha256Hash.asConst();

        synchronized (_mutex) {
            if (_itemCount >= _maxCachedItemCount) {
                _reduceCachedItems();
            }

            final Map<BlockId, TransactionId> subMap;
            {
                final Map<BlockId, TransactionId> map = _cache.get(transactionHash);
                if (map != null) {
                    subMap = map;
                }
                else {
                    subMap = new HashMap<BlockId, TransactionId>();
                    _cache.put(transactionHash, subMap);
                }
            }

            if (! subMap.containsKey(blockId)) {
                _itemCount += 1;
            }

            subMap.put(blockId, transactionId);
        }
    }

    public TransactionId getCachedTransactionId(final BlockId blockId, final Sha256Hash transactionHash) {
        final Timer timer = new Timer();
        timer.start();

        synchronized (_mutex) {
            _cacheQueryCount += 1;

            final Map<BlockId, TransactionId> subMap = _cache.get(transactionHash);
            if (subMap == null) {
                _cacheMissCount += 1;

                timer.stop();
                _msSpentSearching += timer.getMillisecondsElapsed();

                return null;
            }

            final TransactionId transactionId = subMap.get(blockId);
            timer.stop();
            _msSpentSearching += timer.getMillisecondsElapsed();

            return transactionId;
        }
    }

    public Map<BlockId, TransactionId> getCachedTransactionIds(final Sha256Hash transactionHash) {
        final Timer timer = new Timer();
        timer.start();

        synchronized (_mutex) {
            _cacheQueryCount += 1;

            final Map<BlockId, TransactionId> subMap = _cache.get(transactionHash);
            if (subMap == null) {
                _cacheMissCount += 1;

                timer.stop();
                _msSpentSearching += timer.getMillisecondsElapsed();
                return new HashMap<BlockId, TransactionId>();
            }

            final Map<BlockId, TransactionId> cachedTransactionIds = new HashMap<BlockId, TransactionId>(subMap);
            timer.stop();
            _msSpentSearching += timer.getMillisecondsElapsed();
            return cachedTransactionIds;
        }
    }

    public Integer getSize() {
        return _cache.size();
    }

    public void debug() {
        Logger.log("TransactionCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _cache.size() + " | Time Spent Searching: " + _msSpentSearching);
    }
}

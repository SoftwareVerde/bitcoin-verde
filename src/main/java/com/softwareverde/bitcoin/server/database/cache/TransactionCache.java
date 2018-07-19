package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;

import java.util.*;

public class TransactionCache {
    protected final Object _mutex = new Object();
    protected final Integer _maxCachedItemCount;
    protected final HashMap<Sha256Hash, Map<BlockId, TransactionId>> _cache = new HashMap<Sha256Hash, Map<BlockId, TransactionId>>();

    protected int _itemCount = 0;

    /**
     * Reduces the items within the cache to at least one less than the _maxCachedItemCount.
     */
    protected void _reduceCachedItems() {
        final List<Sha256Hash> transactionHashes = new ArrayList<Sha256Hash>(_cache.keySet());

        while (_itemCount >= _maxCachedItemCount) {
            final Sha256Hash transactionHash = transactionHashes.remove(0);
            final Map<BlockId, TransactionId> subMap = _cache.remove(transactionHash);
            _itemCount -= subMap.size();
        }
    }

    public TransactionCache(final Integer maxCachedItemCount) {
        _maxCachedItemCount = maxCachedItemCount;
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
        synchronized (_mutex) {
            final Map<BlockId, TransactionId> subMap = _cache.get(transactionHash);
            if (subMap == null) { return null; }

            return subMap.get(blockId);
        }
    }

    public Map<BlockId, TransactionId> getCachedTransactionIds(final Sha256Hash transactionHash) {
        synchronized (_mutex) {
            final Map<BlockId, TransactionId> subMap = _cache.get(transactionHash);
            if (subMap == null) { return new HashMap<BlockId, TransactionId>(); }

            return new HashMap<BlockId, TransactionId>(subMap);
        }
    }
}

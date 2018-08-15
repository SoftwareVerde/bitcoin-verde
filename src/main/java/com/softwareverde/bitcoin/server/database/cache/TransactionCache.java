package com.softwareverde.bitcoin.server.database.cache;

import com.softwareverde.bitcoin.block.BlockId;
import com.softwareverde.bitcoin.transaction.TransactionId;
import com.softwareverde.bitcoin.type.hash.sha256.ImmutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.io.Logger;
import com.softwareverde.util.SortUtil;
import com.softwareverde.util.Util;
import com.softwareverde.util.timer.Timer;

import java.util.*;

class CachedSha256Hash {
    static final Comparator<CachedSha256Hash> comparator = new Comparator<CachedSha256Hash>() {
        @Override
        public int compare(final CachedSha256Hash o1, final CachedSha256Hash o2) {
            return o1.sequenceNumber.compareTo(o2.sequenceNumber);
        }
    };

    static final Object MUTEX = new Object();
    static Long _nextId = Long.MIN_VALUE;

    public final Sha256Hash sha256Hash;
    public final Long sequenceNumber;

    public CachedSha256Hash(final Sha256Hash sha256Hash) {
        synchronized (MUTEX) {
            this.sha256Hash = sha256Hash;
            this.sequenceNumber = _nextId;
            _nextId += 1L;
        }
    }
}

public class TransactionCache {
    public final Object MUTEX = new Object();

    protected final Integer _cacheMaxSize = 4096; // 32768;
    protected final HashMap<Sha256Hash, Map<BlockId, TransactionId>> _cache = new HashMap<Sha256Hash, Map<BlockId, TransactionId>>(_cacheMaxSize);
    protected final List<CachedSha256Hash> _recentHashes = new ArrayList<CachedSha256Hash>(_cacheMaxSize);
    protected int _cacheSize = 0;

    protected int _cacheQueryCount = 0;
    protected int _cacheMissCount = 0;
    protected double _msSpentSearching = 0D;

    protected void _markAsRecentBlockId(final int index, final Sha256Hash hash) {
        _recentHashes.remove(index);
        _recentHashes.add(new CachedSha256Hash(hash));
        SortUtil.insertionSort(_recentHashes, CachedSha256Hash.comparator);
    }

    protected Integer _findIndex(final Sha256Hash hash) {
        final Timer timer = new Timer();
        timer.start();

        // TODO: This takes too long...
        for (int i = 0; i < _cacheSize; ++i) {
            final int index = ((_cacheSize - i) - 1);
            final CachedSha256Hash cachedHash = _recentHashes.get(index);
            if (Util.areEqual(cachedHash.sha256Hash, hash)) {
                timer.stop();
                _msSpentSearching += timer.getMillisecondsElapsed();

                return index;
            }
        }

        timer.stop();
        _msSpentSearching += timer.getMillisecondsElapsed();

        return null;
    }

    public void clear() {
        _recentHashes.clear();
        _cache.clear();
        _cacheSize = 0;

        _clearDebug();
    }

    public void clearDebug() {
        _clearDebug();
    }

    protected void _clearDebug() {
        _cacheQueryCount = 0;
        _cacheMissCount = 0;
        _msSpentSearching = 0D;
    }

    public void cacheTransactionId(final BlockId blockId, final TransactionId transactionId, final Sha256Hash sha256Hash) {
        final ImmutableSha256Hash transactionHash = sha256Hash.asConst();

        synchronized (MUTEX) {
            if (_cache.containsKey(transactionHash)) {
                final Integer index = _findIndex(transactionHash);
                _markAsRecentBlockId(index, transactionHash);
                return;
            }

            if (_cacheSize >= _cacheMaxSize) {
                final CachedSha256Hash oldestHash = _recentHashes.remove(0);
                _cache.remove(oldestHash.sha256Hash);
                _cacheSize -= 1;
            }

            final Map<BlockId, TransactionId> subMap;
            {
                final Map<BlockId, TransactionId> map = _cache.get(transactionHash);
                subMap = (map != null ? map : new HashMap<BlockId, TransactionId>());
            }

            subMap.put(blockId, transactionId);

            _recentHashes.add(new CachedSha256Hash(transactionHash));
            SortUtil.insertionSort(_recentHashes, CachedSha256Hash.comparator);
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

            final Integer index = _findIndex(transactionHash);
            _markAsRecentBlockId(index, transactionHash);
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

            final Integer index = _findIndex(transactionHash);
            _markAsRecentBlockId(index, transactionHash);
            final Map<BlockId, TransactionId> subMap = new HashMap<BlockId, TransactionId>(_cache.get(transactionHash));

            return subMap;
        }
    }

    public Integer getSize() {
        return _cache.size();
    }

    public void debug() {
        Logger.log("TransactionCache Miss/Queries: " + _cacheMissCount + "/" + _cacheQueryCount + " ("+ (((float) _cacheMissCount) / ((float) _cacheQueryCount) * 100) +"% Miss) | Cache Size: " + _cache.size() + " | Time Spent Searching: " + _msSpentSearching);
    }
}

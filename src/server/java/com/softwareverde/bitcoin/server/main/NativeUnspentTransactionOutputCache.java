package com.softwareverde.bitcoin.server.main;

import com.softwareverde.bitcoin.server.database.cache.conscientious.ConscientiousUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.DisabledUnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCache;
import com.softwareverde.bitcoin.server.database.cache.utxo.UnspentTransactionOutputCacheFactory;
import com.softwareverde.bitcoin.server.database.cache.utxo.UtxoCount;
import com.softwareverde.bitcoin.server.memory.MemoryStatus;
import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.constable.list.List;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.logging.Logger;
import com.softwareverde.util.SystemUtil;
import com.softwareverde.util.jni.NativeUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.softwareverde.bitcoin.jni.NativeUnspentTransactionOutputCache.*;

public class NativeUnspentTransactionOutputCache implements UnspentTransactionOutputCache {
    public static UnspentTransactionOutputCacheFactory createNativeUnspentTransactionOutputCacheFactory(final UtxoCount maxUtxoCount) {
        return new UnspentTransactionOutputCacheFactory() {
            @Override
            public UnspentTransactionOutputCache newUnspentTransactionOutputCache() {
                { // Initialize the NativeUnspentTransactionOutputCache...
                    final boolean nativeCacheIsEnabled = NativeUnspentTransactionOutputCache.isEnabled();
                    if (nativeCacheIsEnabled) {
                        NativeUnspentTransactionOutputCache.init();
                    }
                    else {
                        Logger.warn("NOTICE: NativeUtxoCache not enabled.");
                    }
                }

                if (NativeUnspentTransactionOutputCache.isEnabled()) {
                    return new NativeUnspentTransactionOutputCache(maxUtxoCount);
                }
                else {
                    return new DisabledUnspentTransactionOutputCache();
                }
            }
        };
    }

    public static Long DEFAULT_MAX_ITEM_COUNT = (1L << 24); // Approximately 1/4 of all Unspent Transaction Outputs as of 2018-11.

    public static UtxoCount calculateMaxUtxoCountFromMemoryUsage(final Long maxByteCount) {
        // B-Tree UTXO Cache Memory Usage: https://docs.google.com/spreadsheets/d/1cB_BbJ1Tg6AuV4Ge3yVG081WXUJ-T9YFwYN95I6VcoI
        final double megabyteCount = (maxByteCount / 1024D / 1024D);

        // utxoCount = -1.03E6 + 9330 * megabytes - 1.43 * megabytes^2 + 1.48E-4 * megabytes^3
        // utxoCount = -1030000.0 + 9330 * megabytes - 1.43 * megabytes^2 + 0.000148 * megabytes^3
        return UtxoCount.wrap(Math.max(0, ((long) ((9330 * megabyteCount) - (1.43D * Math.pow(megabyteCount, 2)) + 0.000148D * Math.pow(megabyteCount, 3))) - 1030000L));
    }

    private static final boolean LIBRARY_LOADED_CORRECTLY;
    private static final Object MASTER_MUTEX = new Object();
    private static final ConcurrentHashMap<Integer, ReentrantReadWriteLock> MUTEXES = new ConcurrentHashMap<Integer, ReentrantReadWriteLock>(256);
    private static boolean IS_INIT = false;

    static {
        boolean isEnabled = true;
        try {
            final String extension;
            {
                if (SystemUtil.isWindowsOperatingSystem()) {
                    extension = "dll";
                }
                else if (SystemUtil.isMacOperatingSystem()) {
                    extension = "dylib";
                }
                else {
                    extension = "so";
                }
            }

            NativeUtil.loadLibraryFromJar("/lib/utxocache." + extension);
        }
        catch (final Exception exception) {
            Logger.warn("utxocache failed to load.");
            isEnabled = false;
        }
        LIBRARY_LOADED_CORRECTLY = isEnabled;
    }

    public static boolean isEnabled() {
        return LIBRARY_LOADED_CORRECTLY;
    }

    public static void init() {
        synchronized (MASTER_MUTEX) {
            if (IS_INIT) { return; }

            _init();
            IS_INIT = true;
        }
    }

    public static void destroy() {
        synchronized (MASTER_MUTEX) {
            if (! IS_INIT) { return; }

            _destroy();
            IS_INIT = false;
        }
    }

    protected final MemoryStatus _memoryStatus = new SystemMemoryStatus();
    protected final UtxoCount _maxUtxoCount;
    protected Integer _cacheId;

    protected NativeUnspentTransactionOutputCache _unwrapCache(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        if (unspentTransactionOutputCache instanceof NativeUnspentTransactionOutputCache) {
            return ((NativeUnspentTransactionOutputCache) unspentTransactionOutputCache);
        }

        if (unspentTransactionOutputCache instanceof ConscientiousUnspentTransactionOutputCache) {
            final ConscientiousUnspentTransactionOutputCache wrappedCache = (ConscientiousUnspentTransactionOutputCache) unspentTransactionOutputCache;
            final UnspentTransactionOutputCache unwrappedCached = wrappedCache.unwrap();
            if (unwrappedCached instanceof NativeUnspentTransactionOutputCache) {
                return ((NativeUnspentTransactionOutputCache) unwrappedCached);
            }
        }

        return null;
    }

    protected Integer _unwrapCacheId(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        final NativeUnspentTransactionOutputCache unwrappedCache = _unwrapCache(unspentTransactionOutputCache);
        if (unwrappedCache == null) { return null; }

        return unwrappedCache._cacheId;
    }

    public NativeUnspentTransactionOutputCache(final UtxoCount maxUtxoCount) {
        synchronized (MASTER_MUTEX) {
            _cacheId = _createCache();
        }

        _maxUtxoCount = maxUtxoCount;

        if (_cacheId < 0) {
            _cacheId = null;
            return;
        }

        MUTEXES.put(_cacheId, new ReentrantReadWriteLock());
        _setMaxItemCount(_cacheId, maxUtxoCount.unwrap());
    }

    public void setMaxItemCount(final UtxoCount maxUtxoCount) {
        _setMaxItemCount(_cacheId, maxUtxoCount.unwrap());
    }

    @Override
    public synchronized void cacheUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        if (_cacheId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        _cacheUnspentTransactionOutputId(_cacheId, transactionHash.getBytes(), transactionOutputIndex, transactionOutputId.longValue());
        writeLock.unlock();
    }

    @Override
    public synchronized void setMasterCache(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        if (_cacheId == null) { return; }

        final Integer masterCacheId = _unwrapCacheId(unspentTransactionOutputCache);
        if (masterCacheId == null) {
            Logger.warn("Attempted to set master cache of different type: " + unspentTransactionOutputCache.getClass().getSimpleName());
            return;
        }

        synchronized (MASTER_MUTEX) {
            _setMasterCache(_cacheId, masterCacheId);
        }
    }

    @Override
    public synchronized TransactionOutputId getCachedUnspentTransactionOutputId(final Sha256Hash transactionHash, final Integer transactionOutputIndex) {
        if (_cacheId == null) { return null; }

        final ReentrantReadWriteLock.ReadLock readLock = MUTEXES.get(_cacheId).readLock();
        readLock.lock();
        final long transactionOutputId = _getCachedUnspentTransactionOutputId(_cacheId, transactionHash.getBytes(), transactionOutputIndex);
        readLock.unlock();

        if (! (transactionOutputId > 0)) { return null; }

        return TransactionOutputId.wrap(transactionOutputId);
    }

    @Override
    public synchronized void invalidateUnspentTransactionOutputId(final TransactionOutputIdentifier transactionOutputId) {
        if (_cacheId == null) { return; }
        if (transactionOutputId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        _invalidateUnspentTransactionOutputId(_cacheId, transactionOutputId.getTransactionHash().getBytes(), transactionOutputId.getOutputIndex());
        writeLock.unlock();
    }

    @Override
    public synchronized void invalidateUnspentTransactionOutputIds(final List<TransactionOutputIdentifier> transactionOutputIds) {
        if (_cacheId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        for (final TransactionOutputIdentifier transactionOutputId : transactionOutputIds) {
            if (transactionOutputId == null) { continue; }
            _invalidateUnspentTransactionOutputId(_cacheId, transactionOutputId.getTransactionHash().getBytes(), transactionOutputId.getOutputIndex());
        }
        writeLock.unlock();
    }

    @Override
    public synchronized void commit(final UnspentTransactionOutputCache unspentTransactionOutputCache) {
        if (_cacheId == null) { return; }

        final NativeUnspentTransactionOutputCache sourceCache = _unwrapCache(unspentTransactionOutputCache);
        if (sourceCache == null) {
            Logger.warn("Attempted to commit cache of different type: " + unspentTransactionOutputCache.getClass().getSimpleName());
            return;
        }

        final ReentrantReadWriteLock.WriteLock sourceCacheWriteLock = MUTEXES.get(sourceCache._cacheId).writeLock();
        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        sourceCacheWriteLock.lock();
        writeLock.lock();
        _commit(_cacheId, sourceCache._cacheId);
        writeLock.unlock();
        sourceCacheWriteLock.unlock();
    }

    @Override
    public synchronized void commit() {
        if (_cacheId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        _commit(_cacheId);
        writeLock.unlock();
    }

    @Override
    public MemoryStatus getMemoryStatus() {
        return _memoryStatus;
    }

    @Override
    public void pruneHalf() {
        if (_cacheId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        _pruneHalf(_cacheId);
        writeLock.unlock();
    }

    @Override
    public synchronized void close() {
        if (_cacheId == null) { return; }

        synchronized (MASTER_MUTEX) {
            _deleteCache(_cacheId);
        }
        _cacheId = null;
    }

    @Override
    public synchronized void cacheUnspentTransactionOutputId(final Long insertId, final Sha256Hash transactionHash, final Integer transactionOutputIndex, final TransactionOutputId transactionOutputId) {
        if (_cacheId == null) { return; }

        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        writeLock.lock();
        _loadUnspentTransactionOutputId(_cacheId, insertId, transactionHash.getBytes(), transactionOutputIndex, transactionOutputId.longValue());
        writeLock.unlock();
    }

    @Override
    public UtxoCount getMaxUtxoCount() {
        return _maxUtxoCount;
    }
}

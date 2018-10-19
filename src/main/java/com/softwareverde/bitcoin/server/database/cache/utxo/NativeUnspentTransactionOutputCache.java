package com.softwareverde.bitcoin.server.database.cache.utxo;

import com.softwareverde.bitcoin.transaction.output.TransactionOutputId;
import com.softwareverde.bitcoin.transaction.output.identifier.TransactionOutputIdentifier;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.constable.list.List;
import com.softwareverde.io.Logger;
import com.softwareverde.util.jni.NativeUtil;
import org.apache.commons.lang3.SystemUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.softwareverde.bitcoin.jni.NativeUnspentTransactionOutputCache.*;

public class NativeUnspentTransactionOutputCache implements UnspentTransactionOutputCache {
    private static final boolean LIBRARY_LOADED_CORRECTLY;
    private static final Object MASTER_MUTEX = new Object();
    private static final ConcurrentHashMap<Integer, ReentrantReadWriteLock> MUTEXES = new ConcurrentHashMap<Integer, ReentrantReadWriteLock>(256);

    static {
        boolean isEnabled = true;
        try {
            final String extension;
            {
                if (SystemUtils.IS_OS_WINDOWS) {
                    extension = "dll";
                }
                else if (SystemUtils.IS_OS_MAC) {
                    extension = "dylib";
                }
                else {
                    extension = "so";
                }
            }

            NativeUtil.loadLibraryFromJar("/lib/utxocache." + extension);
        }
        catch (final Exception exception) {
            Logger.log("NOTICE: utxocache failed to load.");
            isEnabled = false;
        }
        LIBRARY_LOADED_CORRECTLY = isEnabled;
    }

    public static boolean isEnabled() {
        return LIBRARY_LOADED_CORRECTLY;
    }

    public static void init() {
        synchronized (MASTER_MUTEX) {
            _init();
        }
    }

    public static void destroy() {
        synchronized (MASTER_MUTEX) {
            _destroy();
        }
    }

    protected Integer _cacheId;

    public NativeUnspentTransactionOutputCache() {
        synchronized (MASTER_MUTEX) {
            _cacheId = _createCache();
        }

        if (_cacheId < 0) {
            _cacheId = null;
            return;
        }

        MUTEXES.put(_cacheId, new ReentrantReadWriteLock());
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
        if (! (unspentTransactionOutputCache instanceof NativeUnspentTransactionOutputCache)) {
            Logger.log("NOTICE: Attempted to set master cache of different type.");
            return;
        }

        final NativeUnspentTransactionOutputCache masterCache = (NativeUnspentTransactionOutputCache) unspentTransactionOutputCache;
        synchronized (MASTER_MUTEX) {
            _setMasterCache(_cacheId, masterCache._cacheId);
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
        if (! (unspentTransactionOutputCache instanceof NativeUnspentTransactionOutputCache)) {
            Logger.log("NOTICE: Attempted to commit cache of different type.");
            return;
        }

        final NativeUnspentTransactionOutputCache sourceCache = (NativeUnspentTransactionOutputCache) unspentTransactionOutputCache;
        final ReentrantReadWriteLock.WriteLock sourceCacheWriteLock = MUTEXES.get(sourceCache._cacheId).writeLock();
        final ReentrantReadWriteLock.WriteLock writeLock = MUTEXES.get(_cacheId).writeLock();

        sourceCacheWriteLock.lock();
        writeLock.lock();
        _commit(_cacheId, sourceCache._cacheId);
        writeLock.unlock();
        sourceCacheWriteLock.unlock();
    }


    @Override
    public synchronized void close() {
        synchronized (MASTER_MUTEX) {
            _deleteCache(_cacheId);
        }
        _cacheId = null;
    }
}

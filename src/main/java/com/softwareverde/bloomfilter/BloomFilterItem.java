package com.softwareverde.bloomfilter;

import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.util.HashUtil;

public class BloomFilterItem {
    protected final ByteArray _bytes;
    private final long _nonce;

    protected Long[] _cachedHashes = new Long[16];

    public BloomFilterItem(final ByteArray bytes, final long nonce) {
        _bytes = bytes;
        _nonce = nonce;
    }

    public BloomFilterItem(final ByteArray bytes) {
        _bytes = bytes;
        _nonce = 0L;
    }

    public void setInitialCacheSize(final int cacheCount) {
        _cachedHashes = new Long[cacheCount];
    }

    public long getNonce() {
        return _nonce;
    }

    public ByteArray getBytes() {
        return _bytes;
    }

    public long getHash(final int index) {
        // Lockless cache check...
        final Long[] cachedHashes = _cachedHashes;
        if (index < cachedHashes.length) {
            final Long hash = cachedHashes[index];
            if (hash != null) { return hash; }
        }

        // Locked cache update...
        synchronized (this) {
            if (index >= _cachedHashes.length) { // Check again, now with the lock...
                final Long[] newArray = new Long[index + 1];
                for (int i = 0; i < _cachedHashes.length; ++i) {
                    newArray[i] = _cachedHashes[i];
                }
                _cachedHashes = newArray;
            }

            final Long hash = HashUtil.murmurHash(_nonce, index, _bytes);
            _cachedHashes[index] = hash;

            return hash;
        }
    }
}
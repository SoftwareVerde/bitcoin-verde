package com.softwareverde.bloomfilter;

import com.softwareverde.concurrent.lock.IndexLock;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.util.HashUtil;

public class ThreadSafeMutableBloomFilter extends MutableBloomFilter {
    protected static final Integer MAX_LOCK_SEGMENT_COUNT = 256;

    public static ThreadSafeMutableBloomFilter newInstance(final Long maxItemCount, final Double falsePositiveRate, final Long nonce) {
        return new ThreadSafeMutableBloomFilter(maxItemCount, falsePositiveRate, nonce);
    }

    public static ThreadSafeMutableBloomFilter newInstance(final Long maxItemCount, final Double falsePositiveRate) {
        return new ThreadSafeMutableBloomFilter(maxItemCount, falsePositiveRate);
    }

    public static ThreadSafeMutableBloomFilter newInstance(final ByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        return new ThreadSafeMutableBloomFilter(new MutableByteArray(byteArray), hashFunctionCount, nonce);
    }

    public static ThreadSafeMutableBloomFilter newInstance(final ByteArray byteArray, final Integer hashFunctionCount, final Long nonce, final byte updateMode) {
        final ThreadSafeMutableBloomFilter mutableBloomFilter = new ThreadSafeMutableBloomFilter(new MutableByteArray(byteArray), hashFunctionCount, nonce);
        mutableBloomFilter.setUpdateMode(updateMode);
        return mutableBloomFilter;
    }

    public static ThreadSafeMutableBloomFilter copyOf(final BloomFilter bloomFilter) {
        final ThreadSafeMutableBloomFilter mutableBloomFilter = new ThreadSafeMutableBloomFilter(new MutableByteArray(bloomFilter.getBytes()), bloomFilter.getHashFunctionCount(), bloomFilter.getNonce());
        mutableBloomFilter.setUpdateMode(bloomFilter.getUpdateMode());
        return mutableBloomFilter;
    }

    public static ThreadSafeMutableBloomFilter wrap(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        return new ThreadSafeMutableBloomFilter(byteArray, hashFunctionCount, nonce);
    }

    protected final IndexLock _indexLock;
    protected final int _indexLockSegmentCount;

    protected int _getByteIndex(final long bitIndex) {
        final long byteIndex = (bitIndex >>> 3);
        if (byteIndex >= _bytes.getByteCount()) { throw new IndexOutOfBoundsException(); }

        return ((int) byteIndex);
    }

    protected Integer _calculateLockSegmentCount(final Integer byteCount) {
        if (byteCount < MAX_LOCK_SEGMENT_COUNT) {
            return byteCount;
        }

        return MAX_LOCK_SEGMENT_COUNT;
    }

    /**
     * NOTICE: The provided byteArray is not copied and used directly as the member variable...
     */
    protected ThreadSafeMutableBloomFilter(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        super(byteArray, hashFunctionCount, nonce);
        _indexLockSegmentCount = _calculateLockSegmentCount(byteArray.getByteCount());
        _indexLock = new IndexLock(_indexLockSegmentCount);
    }

    protected ThreadSafeMutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate, final Long nonce) {
        super(maxItemCount, falsePositiveRate, nonce);
        final Integer byteCount = _bytes.getByteCount();
        _indexLockSegmentCount = _calculateLockSegmentCount(byteCount);
        _indexLock = new IndexLock(_indexLockSegmentCount);
    }

    protected ThreadSafeMutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate) {
        super(maxItemCount, falsePositiveRate);
        final Integer byteCount = _bytes.getByteCount();

        _indexLockSegmentCount = _calculateLockSegmentCount(byteCount);
        _indexLock = new IndexLock(_indexLockSegmentCount);
    }

    @Override
    public void addItem(final ByteArray item) {
        final int byteCount = _bytes.getByteCount();
        final long bitCount = (byteCount * 8L);

        for (int i = 0; i < _hashFunctionCount; ++i) {
            final Long hash = HashUtil.murmurHash(_nonce, i, item);
            final long index = (hash % bitCount);

            final int byteIndex = _getByteIndex(index);
            final int lockIndex = (byteIndex % _indexLockSegmentCount);
            _indexLock.lock(lockIndex);
            try {
                _bytes.setBit(index, true);
            }
            finally {
                _indexLock.unlock(lockIndex);
            }
        }
    }

    @Override
    public void clear() {
        final int byteCount = _bytes.getByteCount();
        for (int i = 0; i < byteCount; ++i) {
            final int lockIndex = (i % _indexLockSegmentCount);

            _indexLock.lock(lockIndex);
            try {
                _bytes.setByte(i, (byte) 0x00);
            }
            finally {
                _indexLock.unlock(lockIndex);
            }
        }
    }
}

package com.softwareverde.bloomfilter;

import com.softwareverde.concurrent.lock.IndexLock;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.cryptography.util.HashUtil;

public class MutableBloomFilter implements BloomFilter {
    protected static final Double LN_2 = Math.log(2);
    protected static final Double LN_2_SQUARED = (Math.pow(LN_2, 2));
    protected static final Integer MAX_LOCK_SEGMENT_COUNT = 256;

    protected static Integer _calculateByteCount(final Long maxItemCount, final Double falsePositiveRate) {
        final int byteCount = (int) ( (-1.0D / LN_2_SQUARED * maxItemCount * Math.log(falsePositiveRate)) / 8D );
        if (byteCount < 1) { return 1; }

        return Math.min(byteCount, ByteArray.MAX_BYTE_COUNT);
    }

    public static Integer calculateFunctionCount(final Integer byteCount, final Long maxItemCount) {
        return (int) ((byteCount / maxItemCount.doubleValue()) * 8D * LN_2);
    }

    protected static Long _makeUnsignedInt(final Long nonce) {
        return (nonce & 0xFFFFFFFFL);
    }

    public static MutableBloomFilter newInstance(final Long maxItemCount, final Double falsePositiveRate, final Long nonce) {
        return new MutableBloomFilter(maxItemCount, falsePositiveRate, nonce);
    }

    public static MutableBloomFilter newInstance(final Long maxItemCount, final Double falsePositiveRate) {
        return new MutableBloomFilter(maxItemCount, falsePositiveRate);
    }

    public static MutableBloomFilter newInstance(final ByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        return new MutableBloomFilter(new MutableByteArray(byteArray), hashFunctionCount, nonce);
    }

    public static MutableBloomFilter newInstance(final ByteArray byteArray, final Integer hashFunctionCount, final Long nonce, final byte updateMode) {
        final MutableBloomFilter mutableBloomFilter = new MutableBloomFilter(new MutableByteArray(byteArray), hashFunctionCount, nonce);
        mutableBloomFilter.setUpdateMode(updateMode);
        return mutableBloomFilter;
    }

    public static MutableBloomFilter copyOf(final BloomFilter bloomFilter) {
        final MutableBloomFilter mutableBloomFilter = new MutableBloomFilter(new MutableByteArray(bloomFilter.getBytes()), bloomFilter.getHashFunctionCount(), bloomFilter.getNonce());
        mutableBloomFilter.setUpdateMode(bloomFilter.getUpdateMode());
        return mutableBloomFilter;
    }

    public static MutableBloomFilter wrap(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        return new MutableBloomFilter(byteArray, hashFunctionCount, nonce);
    }

    protected final IndexLock _indexLock;
    protected final int _indexLockSegmentCount;

    protected final MutableByteArray _bytes;
    protected final Long _nonce;
    protected final Integer _hashFunctionCount;
    protected byte _updateMode = 0x00;

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
    protected MutableBloomFilter(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        _bytes = byteArray;
        _indexLockSegmentCount = _calculateLockSegmentCount(byteArray.getByteCount());
        _indexLock = new IndexLock(_indexLockSegmentCount);
        _hashFunctionCount = hashFunctionCount;
        _nonce = _makeUnsignedInt(nonce);
    }

    protected MutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate, final Long nonce) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _indexLockSegmentCount = _calculateLockSegmentCount(byteCount);
        _indexLock = new IndexLock(_indexLockSegmentCount);
        _hashFunctionCount = calculateFunctionCount(byteCount, maxItemCount);
        _nonce = _makeUnsignedInt(nonce);
    }

    protected MutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _indexLockSegmentCount = _calculateLockSegmentCount(byteCount);
        _indexLock = new IndexLock(_indexLockSegmentCount);
        _hashFunctionCount = calculateFunctionCount(byteCount, maxItemCount);

        // NOTE: Making large nonces may cause the murmurHash initialization vector to overflow in a way inconsistent with the other clients...
        //  It's unconfirmed (either way) for whether MurmurHash and Bitcoin's MurmurHash handle the overflow the same.
        //  It is also possible to encounter an underflow when hashFunctionCount > 29, regardless of the nonce.
        //  In order to minimize the risk of different the behaviors, Integer.MAX_VALUE is used instead of Long.MAX_VALUE, and the nonce then converted to an unsigned int.
        //  This does not remove the risk and should be properly investigated.
        // TODO: Investigate MurmurHash h1 underflow/overflow...
        _nonce = _makeUnsignedInt((long) (Math.random() * Integer.MAX_VALUE));
    }

    @Override
    public Long getNonce() { return _nonce; }

    @Override
    public Integer getHashFunctionCount() { return _hashFunctionCount; }

    @Override
    public ByteArray getBytes() { return _bytes; }

    @Override
    public Integer getByteCount() {
        return _bytes.getByteCount();
    }

    public void addItem(final ByteArray item) {
        final int byteCount = _bytes.getByteCount();
        final long bitCount = (byteCount * 8L);

        for (int i = 0; i < _hashFunctionCount; ++i) {
            final Long hash = HashUtil.murmurHash(_nonce, i, item);
            final long index = (hash % bitCount);

            final int byteIndex = _getByteIndex(index);
            final int lockIndex = (byteIndex % _indexLockSegmentCount);
            try {
                _indexLock.lock(lockIndex);
                _bytes.setBit(index, true);
            }
            finally {
                _indexLock.unlock(lockIndex);
            }
        }
    }

    @Override
    public Boolean containsItem(final ByteArray item) {
        return BloomFilterCore.containsItem(_bytes, _hashFunctionCount, _nonce, item);
    }

    @Override
    public byte getUpdateMode() {
        return _updateMode;
    }

    public void setUpdateMode(final byte updateMode) {
        _updateMode = updateMode;
    }

    public void clear() {
        final int byteCount = _bytes.getByteCount();
        for (int i = 0; i < byteCount; ++i) {
            final int lockIndex = (i % _indexLockSegmentCount);

            try {
                _indexLock.lock(lockIndex);
                _bytes.setByte(i, (byte) 0x00);
            }
            finally {
                _indexLock.unlock(lockIndex);
            }
        }
    }

    public MutableByteArray unwrap() {
        return _bytes;
    }

    /**
     * Calculates the theoretical false positive rate, if were to contain the elementCount elements...
     */
    @Override
    public Float getFalsePositiveRate(final Long elementCount) {
        return BloomFilterCore.getFalsePositiveRate(_bytes, _hashFunctionCount, elementCount);
    }

    @Override
    public Float getFalsePositiveRate() {
        return BloomFilterCore.getFalsePositiveRate(_bytes, _hashFunctionCount);
    }

    @Override
    public ImmutableBloomFilter asConst() {
        return new ImmutableBloomFilter(this);
    }

    @Override
    public boolean equals(final Object object) {
        return BloomFilterCore.equals(_bytes, _hashFunctionCount, _nonce, _updateMode, object);
    }

    @Override
    public int hashCode() {
        return BloomFilterCore.hashCode(_bytes);
    }
}

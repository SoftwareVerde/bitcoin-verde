package com.softwareverde.bloomfilter;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;

public class BloomFilter {
    private static final Double LN_2 = Math.log(2);
    private static final Double LN_2_SQUARED = (Math.pow(LN_2, 2));

    protected final MutableByteArray _bytes;
    protected final Long _nonce;
    protected final Integer _hashFunctionCount;

    protected static Integer _calculateByteCount(final Integer maxItemCount, final Double falsePositiveRate) {
        return (int) ( (-1 / LN_2_SQUARED * maxItemCount * Math.log(falsePositiveRate)) / 8 );
    }
    protected static Integer _calculateFunctionCount(final Integer byteCount, final Integer maxItemCount) {
        return (int) ((byteCount * 8 * LN_2) / maxItemCount);
    }

    public BloomFilter(final Integer maxItemCount, final Long nonce, final Double falsePositiveRate) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _hashFunctionCount = _calculateFunctionCount(byteCount, maxItemCount);
        _nonce = nonce;
    }

    public BloomFilter(final Integer maxItemCount, final Double falsePositiveRate) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _hashFunctionCount = _calculateFunctionCount(byteCount, maxItemCount);
        _nonce = (long) (Math.random() * Long.MAX_VALUE);
    }

    public BloomFilter(final ByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        _bytes = new MutableByteArray(byteArray);
        _hashFunctionCount = hashFunctionCount;
        _nonce = nonce;
    }

    public Long getNonce() { return _nonce; }
    public Integer getHashFunctionCount() { return _hashFunctionCount; }
    public ByteArray getBytes() { return _bytes; }

    public void addItem(final ByteArray item) {
        final Integer byteCount = _bytes.getByteCount();
        final Integer bitCount = (byteCount * 8);

        for (int i = 0; i < _hashFunctionCount; ++i) {
            final Long hash = BitcoinUtil.murmurHash(_nonce, i, item);
            final Integer index = (int) (hash % bitCount);
            _bytes.setBit(index, true);
        }
    }

    public Boolean containsItem(final ByteArray item) {
        final Integer byteCount = _bytes.getByteCount();
        final Integer bitCount = (byteCount * 8);

        for (int i = 0; i < _hashFunctionCount; ++i) {
            final Long hash = BitcoinUtil.murmurHash(_nonce, i, item);
            final Integer index = (int) (hash % bitCount);
            final Boolean isSet = _bytes.getBit(index);

            if (! isSet) {
                return false;
            }
        }

        return true;
    }

    public void clear() {
        for (int i = 0; i < _bytes.getByteCount(); ++i) {
            _bytes.set(i, (byte) 0x00);
        }
    }

    /**
     * Calculates the theoretical false positive rate, if were to contain the elementCount elements...
     */
    public Float getFalsePositiveRate(final Integer elementCount) {
        final Integer bitCount = (_bytes.getByteCount() * 8);
        final Float exponent = ( (-1.0F * _hashFunctionCount * elementCount) / bitCount );
        return ( (float) Math.pow(1.0F - Math.pow(Math.E, exponent), _hashFunctionCount) );
    }

    public Float getFalsePositiveRate() {
        final Integer byteCount = _bytes.getByteCount();
        final Integer bitCount = (byteCount * 8);
        int setBitCount = 0;
        for (int i = 0; i < byteCount; ++i) {
            final byte b = _bytes.getByte(i);
            setBitCount += Integer.bitCount(b & 0xFF);
        }

        final Integer unsetBitCount = (bitCount - setBitCount);
        final Float unsetBitCountRatio = (unsetBitCount.floatValue() / bitCount.floatValue());
        return ( (float) Math.pow(1.0F - unsetBitCountRatio, _hashFunctionCount) );
    }

    @Override
    public boolean equals(final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BloomFilter)) { return false; }

        final BloomFilter bloomFilter = (BloomFilter) object;
        return Util.areEqual(_bytes, bloomFilter._bytes);
    }

    @Override
    public int hashCode() {
        return _bytes.hashCode();
    }
}
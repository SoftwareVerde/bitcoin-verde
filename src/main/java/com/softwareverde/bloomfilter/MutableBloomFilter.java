package com.softwareverde.bloomfilter;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class MutableBloomFilter implements BloomFilter {
    private static final Double LN_2 = Math.log(2);
    private static final Double LN_2_SQUARED = (Math.pow(LN_2, 2));

    protected final MutableByteArray _bytes;
    protected final Long _nonce;
    protected final Integer _hashFunctionCount;

    protected static Integer _calculateByteCount(final Long maxItemCount, final Double falsePositiveRate) {
        final Integer byteCount = (int) ( (-1.0D / LN_2_SQUARED * maxItemCount * Math.log(falsePositiveRate)) / 8D );
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

    public static MutableBloomFilter copyOf(final BloomFilter bloomFilter) {
        return new MutableBloomFilter(new MutableByteArray(bloomFilter.getBytes()), bloomFilter.getHashFunctionCount(), bloomFilter.getNonce());
    }

    public static MutableBloomFilter wrap(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        return new MutableBloomFilter(byteArray, hashFunctionCount, nonce);
    }

    /**
     * NOTICE: The provided byteArray is not copied and used directly as the member variable...
     */
    protected MutableBloomFilter(final MutableByteArray byteArray, final Integer hashFunctionCount, final Long nonce) {
        _bytes = byteArray;
        _hashFunctionCount = hashFunctionCount;
        _nonce = _makeUnsignedInt(nonce);
    }

    protected MutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate, final Long nonce) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _hashFunctionCount = calculateFunctionCount(byteCount, maxItemCount);
        _nonce = _makeUnsignedInt(nonce);
    }

    protected MutableBloomFilter(final Long maxItemCount, final Double falsePositiveRate) {
        final Integer byteCount = _calculateByteCount(maxItemCount, falsePositiveRate);
        _bytes = new MutableByteArray(byteCount);
        _hashFunctionCount = calculateFunctionCount(byteCount, maxItemCount);

        // NOTE: Making large nonces may cause the murmurHash initialization vector to overflow in a way inconsistent with the other clients...
        //  It's unconfirmed (either way) for whether MurmurHash and Bitcoin's MurmurHash handle the overflow the same.
        //  It is also possible to encounter an underflow when hashFunctionCount > 29, regardless of the nonce.
        //  In order to minimize the risk of different the behaviors, Integer.MAX_VALUE is used instead of Long.MAX_VALUE, and the nonce then converted to an unsigned int.
        //  This does not remove the risk and should be properly investigated.
        // TODO: Investigate MurmurHash h1 underflow/overflow...
        _nonce = _makeUnsignedInt((long) (Math.random() * Integer.MAX_VALUE));
    }

    public Long getNonce() { return _nonce; }
    public Integer getHashFunctionCount() { return _hashFunctionCount; }
    public ByteArray getBytes() { return _bytes; }

    public void addItem(final ByteArray item) {
        final Integer byteCount = _bytes.getByteCount();
        final Long bitCount = (byteCount * 8L);

        for (int i = 0; i < _hashFunctionCount; ++i) {
            final Long hash = BitcoinUtil.murmurHash(_nonce, i, item);
            final Long index = (hash % bitCount);
            _bytes.setBit(index, true);
        }
    }

    @Override
    public Boolean containsItem(final ByteArray item) {
        return BloomFilterCore.containsItem(_bytes, _hashFunctionCount, _nonce, item);
    }

    public void clear() {
        for (int i = 0; i < _bytes.getByteCount(); ++i) {
            _bytes.set(i, (byte) 0x00);
        }
    }

    public MutableByteArray unwrap() {
        return _bytes;
    }

    /**
     * Calculates the theoretical false positive rate, if were to contain the elementCount elements...
     */
    public Float getFalsePositiveRate(final Long elementCount) {
        return BloomFilterCore.getFalsePositiveRate(_bytes, _hashFunctionCount, elementCount);
    }

    public Float getFalsePositiveRate() {
        return BloomFilterCore.getFalsePositiveRate(_bytes, _hashFunctionCount);
    }

    @Override
    public ImmutableBloomFilter asConst() {
        return new ImmutableBloomFilter(this);
    }

    @Override
    public boolean equals(final Object object) {
        return BloomFilterCore.equals(_bytes, _hashFunctionCount, _nonce, object);
    }

    @Override
    public int hashCode() {
        return BloomFilterCore.hashCode(_bytes);
    }
}

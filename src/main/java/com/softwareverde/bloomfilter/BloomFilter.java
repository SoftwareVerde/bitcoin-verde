package com.softwareverde.bloomfilter;

import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.cryptography.util.HashUtil;
import com.softwareverde.util.Util;

public interface BloomFilter extends Constable<ImmutableBloomFilter> {
    BloomFilter MATCH_ALL = new ImmutableBloomFilter(new byte[] { (byte) 0xFF }, 0L, 1);
    BloomFilter MATCH_NONE = new ImmutableBloomFilter(new byte[] { (byte) 0x00 }, 0L, 1);

    Integer MAX_ITEM_COUNT = Integer.MAX_VALUE;
    Integer MAX_FUNCTION_COUNT = Integer.MAX_VALUE;

    Long getNonce();
    Integer getHashFunctionCount();
    ByteArray getBytes();
    Integer getByteCount();
    Boolean containsItem(ByteArray item);
    Boolean containsItem(BloomFilterItem item);
    byte getUpdateMode();

    /**
     * Calculates the theoretical false positive rate, if were to contain the elementCount elements...
     */
    Float getFalsePositiveRate(Long elementCount);
    Float getFalsePositiveRate();

    @Override
    ImmutableBloomFilter asConst();

    @Override
    boolean equals(Object object);

    @Override
    int hashCode();
}

class BloomFilterCore {
    public static Boolean containsItem(final ByteArray bytes, final Integer hashFunctionCount, final Long nonce, final ByteArray item) {
        final int byteCount = bytes.getByteCount();
        final long bitCount = (byteCount * 8L);

        for (int i = 0; i < hashFunctionCount; ++i) {
            final Long hash = HashUtil.murmurHash(nonce, i, item);
            final long index = (hash % bitCount);
            final boolean isSet = bytes.getBit(index);

            if (! isSet) {
                return false;
            }
        }

        return true;
    }

    public static Boolean containsItem(final ByteArray bytes, final Integer hashFunctionCount, final BloomFilterItem item) {
        final int byteCount = bytes.getByteCount();
        final long bitCount = (byteCount * 8L);

        for (int i = 0; i < hashFunctionCount; ++i) {
            final long hash = item.getHash(i);
            final long index = (hash % bitCount);
            final boolean isSet = bytes.getBit(index);

            if (! isSet) {
                return false;
            }
        }

        return true;
    }

    public static Float getFalsePositiveRate(final ByteArray bytes, final Integer hashFunctionCount, final Long elementCount) {
        final long bitCount = (bytes.getByteCount() * 8L);
        final double exponent = (-1.0D * hashFunctionCount * (elementCount / ((double) bitCount)) );
        return ( (float) Math.pow(1.0D - Math.pow(Math.E, exponent), hashFunctionCount) );
    }

    public static Float getFalsePositiveRate(final ByteArray bytes, final Integer hashFunctionCount) {
        final int byteCount = bytes.getByteCount();
        final long bitCount = (byteCount * 8L);
        long setBitCount = 0L;
        for (int i = 0; i < byteCount; ++i) {
            final byte b = bytes.getByte(i);
            setBitCount += Integer.bitCount(b & 0xFF);
        }

        final long unsetBitCount = (bitCount - setBitCount);
        final double unsetBitCountRatio = ( ((double) unsetBitCount) / ((double) bitCount) );
        return ( (float) Math.pow(1.0D - unsetBitCountRatio, hashFunctionCount) );
    }

    public static boolean equals(final ByteArray bytes, final Integer hashFunctionCount, final Long nonce, final byte updateMode, final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BloomFilter)) { return false; }

        final BloomFilter bloomFilter = (BloomFilter) object;

        if (! Util.areEqual(hashFunctionCount, bloomFilter.getHashFunctionCount())) {
            return false;
        }

        if (! Util.areEqual(nonce, bloomFilter.getNonce())) {
            return false;
        }

        if (updateMode != bloomFilter.getUpdateMode()) {
            return false;
        }

        return Util.areEqual(bytes, bloomFilter.getBytes());
    }

    public static int hashCode(final ByteArray bytes) {
        return bytes.hashCode();
    }
}
package com.softwareverde.bloomfilter;

import com.softwareverde.bitcoin.util.BitcoinUtil;
import com.softwareverde.constable.Constable;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.util.Util;

public interface BloomFilter extends Constable<ImmutableBloomFilter> {
    BloomFilter MATCH_ALL = new ImmutableBloomFilter(new byte[] { (byte) 0xFF }, 0L, 1);
    BloomFilter MATCH_NONE = new ImmutableBloomFilter(new byte[] { (byte) 0x00 }, 0L, 1);

    Integer MAX_ITEM_COUNT = Integer.MAX_VALUE;
    Integer MAX_FUNCTION_COUNT = Integer.MAX_VALUE;

    Long getNonce();
    Integer getHashFunctionCount();
    ByteArray getBytes();
    Boolean containsItem(ByteArray item);

    /**
     * Calculates the theoretical false positive rate, if were to contain the elementCount elements...
     */
    Float getFalsePositiveRate(Integer elementCount);
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
        final Integer byteCount = bytes.getByteCount();
        final Integer bitCount = (byteCount * 8);

        for (int i = 0; i < hashFunctionCount; ++i) {
            final Long hash = BitcoinUtil.murmurHash(nonce, i, item);
            final Integer index = (int) (hash % bitCount);
            final Boolean isSet = bytes.getBit(index);

            if (! isSet) {
                return false;
            }
        }

        return true;
    }

    public static Float getFalsePositiveRate(final ByteArray bytes, final Integer hashFunctionCount, final Integer elementCount) {
        final Integer bitCount = (bytes.getByteCount() * 8);
        final Float exponent = ( (-1.0F * hashFunctionCount * elementCount) / bitCount );
        return ( (float) Math.pow(1.0F - Math.pow(Math.E, exponent), hashFunctionCount) );
    }

    public static Float getFalsePositiveRate(final ByteArray bytes, final Integer hashFunctionCount) {
        final Integer byteCount = bytes.getByteCount();
        final Integer bitCount = (byteCount * 8);
        int setBitCount = 0;
        for (int i = 0; i < byteCount; ++i) {
            final byte b = bytes.getByte(i);
            setBitCount += Integer.bitCount(b & 0xFF);
        }

        final Integer unsetBitCount = (bitCount - setBitCount);
        final Float unsetBitCountRatio = (unsetBitCount.floatValue() / bitCount.floatValue());
        return ( (float) Math.pow(1.0F - unsetBitCountRatio, hashFunctionCount) );
    }

    public static boolean equals(final ByteArray bytes, final Integer hashFunctionCount, final Long nonce, final Object object) {
        if (object == null) { return false; }
        if (! (object instanceof BloomFilter)) { return false; }

        final BloomFilter bloomFilter = (BloomFilter) object;

        if (! Util.areEqual(hashFunctionCount, bloomFilter.getHashFunctionCount())) {
            return false;
        }

        if (! Util.areEqual(nonce, bloomFilter.getNonce())) {
            return false;
        }

        return Util.areEqual(bytes, bloomFilter.getBytes());
    }

    public static int hashCode(final ByteArray bytes) {
        return bytes.hashCode();
    }
}
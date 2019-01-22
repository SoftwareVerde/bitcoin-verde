package com.softwareverde.bloomfilter;

import com.softwareverde.constable.Const;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.ImmutableByteArray;

public class ImmutableBloomFilter implements BloomFilter, Const {
    protected final ByteArray _bytes;
    protected final Long _nonce;
    protected final Integer _hashFunctionCount;

    public ImmutableBloomFilter(final BloomFilter bloomFilter) {
        _bytes = bloomFilter.getBytes().asConst();
        _nonce = bloomFilter.getNonce();
        _hashFunctionCount = bloomFilter.getHashFunctionCount();
    }

    protected ImmutableBloomFilter(final byte[] bytes, final Long nonce, final Integer hashFunctionCount) {
        _bytes = new ImmutableByteArray(bytes);
        _nonce = nonce;
        _hashFunctionCount = hashFunctionCount;
    }

    @Override
    public Long getNonce() {
        return _nonce;
    }

    @Override
    public Integer getHashFunctionCount() {
        return _hashFunctionCount;
    }

    @Override
    public ByteArray getBytes() {
        return _bytes;
    }

    @Override
    public Boolean containsItem(final ByteArray item) {
        return BloomFilterCore.containsItem(_bytes, _hashFunctionCount, _nonce, item);
    }

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
        return this;
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

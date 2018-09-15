package com.softwareverde.bitcoin.bloomfilter;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.Endian;

public class BloomFilterInflater {
    public static final Integer MAX_BYTE_COUNT = (32 * 1024 * 1024);

    public BloomFilter fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        final Integer bloomFilterByteCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (bloomFilterByteCount > MAX_BYTE_COUNT) { return null; }

        final Integer bloomFilterBitCount = (bloomFilterByteCount * 8);
        final ByteArray bytes = MutableByteArray.wrap(byteArrayReader.readBytes(bloomFilterByteCount, Endian.BIG));
        final Integer hashFunctionCount = byteArrayReader.readInteger(4, Endian.LITTLE);
        final Long nonce = byteArrayReader.readLong(4, Endian.LITTLE);

        final MutableByteArray bloomFilterBytes = new MutableByteArray(bloomFilterByteCount);
        for (int i = 0; i < bloomFilterBitCount; ++i) {
            final int bitcoinBloomFilterIndex = ( (i & 0x7FFFFFF8) + ((~i) & 0x00000007) ); // Aka: ( ((i / 8) * 8) + (7 - (i % 8)) )
            final boolean bit = bytes.getBit(i);
            bloomFilterBytes.setBit(bitcoinBloomFilterIndex, bit);
        }

        return new BloomFilter(bloomFilterBytes, hashFunctionCount, nonce);
    }
}

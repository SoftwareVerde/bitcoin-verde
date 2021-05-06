package com.softwareverde.bitcoin.bloomfilter;

import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bloomfilter.MutableBloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.Endian;

public class BloomFilterInflater {
    public static final Integer MAX_BYTE_COUNT = (32 * 1024 * 1024);

    protected MutableBloomFilter _fromByteArrayReader(final ByteArrayReader byteArrayReader) {
        final Integer bloomFilterByteCount = byteArrayReader.readVariableLengthInteger().intValue();
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

        final byte updateMode = byteArrayReader.readByte();

        if (byteArrayReader.didOverflow()) { return null; }

        final MutableBloomFilter mutableBloomFilter = MutableBloomFilter.newInstance(bloomFilterBytes, hashFunctionCount, nonce);
        mutableBloomFilter.setUpdateMode(updateMode);
        return mutableBloomFilter;
    }

    public MutableBloomFilter fromBytes(final ByteArray byteArray) {
        final ByteArrayReader byteArrayReader = new ByteArrayReader(byteArray);
        return _fromByteArrayReader(byteArrayReader);
    }

    public MutableBloomFilter fromBytes(final ByteArrayReader byteArrayReader) {
        return _fromByteArrayReader(byteArrayReader);
    }
}

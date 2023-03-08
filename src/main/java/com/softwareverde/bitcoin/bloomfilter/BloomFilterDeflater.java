package com.softwareverde.bitcoin.bloomfilter;

import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;
import com.softwareverde.util.bytearray.Endian;

public class BloomFilterDeflater {
    public ByteArray toBytes(final BloomFilter bloomFilter) {
        final ByteArray bloomFilterBytes = bloomFilter.getBytes();
        final int bloomFilterByteCount = bloomFilterBytes.getByteCount();
        final int bloomFilterBitCount = (bloomFilterByteCount * 8);
        final Long nonce = bloomFilter.getNonce();
        final Integer hashFunctionCount = bloomFilter.getHashFunctionCount();
        final byte updateMode = bloomFilter.getUpdateMode();

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(CompactVariableLengthInteger.variableLengthIntegerToBytes(bloomFilterByteCount));

        {
            final MutableByteArray littleEndianBytes = new MutableByteArray(bloomFilterByteCount);
            for (int i = 0; i < bloomFilterBitCount; ++i) {
                // 0 1 2 3 4 5 6 7 | 8 9 A B C D E F | G H I J K L M N
                // Bitcoin treats the indexes of its BloomFilter differently than com.softwareverde.bloomfilter.BloomFilter.
                //  In Bitcoin, the 0th-bit is the LSB of the first byte (aka, index 7).
                //  The BloomFilterDeflater converts the BloomFilter's serialized btyes to match the Bitcoin format so other nodes can inflate it.
                final int bitcoinBloomFilterIndex = ( (i & 0x7FFFFFF8) + ((~i) & 0x00000007) ); // Aka: ( ((i / 8) * 8) + (7 - (i % 8)) )
                final boolean bit = bloomFilterBytes.getBit(i);
                littleEndianBytes.setBit(bitcoinBloomFilterIndex, bit);
            }
            byteArrayBuilder.appendBytes(littleEndianBytes);
        }
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(hashFunctionCount), Endian.LITTLE);
        byteArrayBuilder.appendBytes(ByteUtil.integerToBytes(nonce), Endian.LITTLE);
        byteArrayBuilder.appendByte(updateMode);
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }

    public Integer getByteCount(final BloomFilter bloomFilter) {
        final int bloomFilterByteCount = bloomFilter.getByteCount();
        final ByteArray bloomFilterByteCountBytes = CompactVariableLengthInteger.variableLengthIntegerToBytes(bloomFilterByteCount);

        return (bloomFilterByteCountBytes.getByteCount() + 4 + 4 + 1 + bloomFilterByteCount);
    }
}

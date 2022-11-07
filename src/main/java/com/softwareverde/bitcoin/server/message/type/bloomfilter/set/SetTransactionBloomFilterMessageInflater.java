package com.softwareverde.bitcoin.server.message.type.bloomfilter.set;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.inflater.BloomFilterInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class SetTransactionBloomFilterMessageInflater extends BitcoinProtocolMessageInflater {
    public static final Long MAX_SIZE = 36000L;
    public static final Integer MAX_HASH_FUNCTION_COUNT = 50;

    protected final BloomFilterInflaters _bloomFilterInflaters;

    public SetTransactionBloomFilterMessageInflater(final BloomFilterInflaters bloomFilterInflaters) {
        _bloomFilterInflaters = bloomFilterInflaters;
    }

    @Override
    public SetTransactionBloomFilterMessage fromBytes(final byte[] bytes) {
        final SetTransactionBloomFilterMessage setTransactionBloomFilterMessage = new SetTransactionBloomFilterMessage(_bloomFilterInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.SET_TRANSACTION_BLOOM_FILTER);
        if (protocolMessageHeader == null) { return null; }

        final BloomFilterInflater bloomFilterInflater = _bloomFilterInflaters.getBloomFilterInflater();
        final BloomFilter bloomFilter = bloomFilterInflater.fromBytes(byteArrayReader);
        if (bloomFilter == null) { return null; }

        setTransactionBloomFilterMessage.setBloomFilter(bloomFilter);

        if (byteArrayReader.didOverflow()) { return null; }

        return setTransactionBloomFilterMessage;
    }
}

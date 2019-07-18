package com.softwareverde.bitcoin.server.message.type.bloomfilter.set;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.inflater.BloomFilterInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;

public class SetTransactionBloomFilterMessage extends BitcoinProtocolMessage {
    protected final BloomFilterInflaters _bloomFilterInflaters;
    protected BloomFilter _bloomFilter = null;

    public SetTransactionBloomFilterMessage(final BloomFilterInflaters bloomFilterInflaters) {
        super(MessageType.SET_TRANSACTION_BLOOM_FILTER);
        _bloomFilterInflaters = bloomFilterInflaters;
    }

    public BloomFilter getBloomFilter() {
        return _bloomFilter;
    }

    public void setBloomFilter(final BloomFilter bloomFilter) {
        _bloomFilter = bloomFilter.asConst();
    }

    @Override
    protected ByteArray _getPayload() {
        if (_bloomFilter == null) { return new MutableByteArray(0); }
        final BloomFilterDeflater bloomFilterDeflater = _bloomFilterInflaters.getBloomFilterDeflater();
        return bloomFilterDeflater.toBytes(_bloomFilter);
    }
}

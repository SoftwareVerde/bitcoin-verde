package com.softwareverde.bitcoin.server.message.type.thin.request.block;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bloomfilter.BloomFilter;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class RequestExtraThinBlockMessage extends BitcoinProtocolMessage {
    protected InventoryItem _inventoryItem = null;
    protected BloomFilter _bloomFilter = null;

    public RequestExtraThinBlockMessage() {
        super(MessageType.REQUEST_EXTRA_THIN_BLOCK);
    }

    public InventoryItem getInventoryItem() {
        return _inventoryItem;
    }

    public BloomFilter getBloomFilter() {
        return _bloomFilter;
    }

    public void setInventoryItem(final InventoryItem inventoryItem) {
        _inventoryItem = inventoryItem;
    }

    public void setBloomFilter(final BloomFilter bloomFilter) {
        _bloomFilter = bloomFilter;
    }

    @Override
    protected ByteArray _getPayload() {
        final BloomFilterDeflater bloomFilterDeflater = new BloomFilterDeflater();
        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(_inventoryItem.getBytes());
        byteArrayBuilder.appendBytes(bloomFilterDeflater.toBytes(_bloomFilter));
        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

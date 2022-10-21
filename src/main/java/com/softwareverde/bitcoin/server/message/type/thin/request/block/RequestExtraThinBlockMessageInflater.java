package com.softwareverde.bitcoin.server.message.type.thin.request.block;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.inflater.BloomFilterInflaters;
import com.softwareverde.bitcoin.inflater.InventoryItemInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;
import com.softwareverde.util.bytearray.ByteArrayReader;
import com.softwareverde.bloomfilter.BloomFilter;

public class RequestExtraThinBlockMessageInflater extends BitcoinProtocolMessageInflater {

    protected final InventoryItemInflaters _inventoryItemInflaters;
    protected final BloomFilterInflaters _bloomFilterInflaters;

    public RequestExtraThinBlockMessageInflater(final InventoryItemInflaters inventoryItemInflaters, final BloomFilterInflaters bloomFilterInflaters) {
        _inventoryItemInflaters = inventoryItemInflaters;
        _bloomFilterInflaters = bloomFilterInflaters;
    }

    @Override
    public RequestExtraThinBlockMessage fromBytes(final byte[] bytes) {
        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage(_bloomFilterInflaters);
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_DATA);
        if (protocolMessageHeader == null) { return null; }

        final InventoryItemInflater inventoryItemInflater = _inventoryItemInflaters.getInventoryItemInflater();
        final InventoryItem inventoryItem = inventoryItemInflater.fromBytes(byteArrayReader);
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);

        final BloomFilterInflater bloomFilterInflater = _bloomFilterInflaters.getBloomFilterInflater();
        final BloomFilter bloomFilter = bloomFilterInflater.fromBytes(byteArrayReader);
        requestExtraThinBlockMessage.setBloomFilter(bloomFilter);

        if (byteArrayReader.didOverflow()) { return null; }

        return requestExtraThinBlockMessage;
    }
}

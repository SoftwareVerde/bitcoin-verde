package com.softwareverde.bitcoin.server.message.type.thin.request.block;

import com.softwareverde.bitcoin.bloomfilter.BloomFilterInflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bloomfilter.BloomFilter;

public class RequestExtraThinBlockMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public RequestExtraThinBlockMessage fromBytes(final byte[] bytes) {
        final InventoryItemInflater inventoryItemInflater = new InventoryItemInflater();
        final BloomFilterInflater bloomFilterInflater = new BloomFilterInflater();

        final RequestExtraThinBlockMessage requestExtraThinBlockMessage = new RequestExtraThinBlockMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_DATA);
        if (protocolMessageHeader == null) { return null; }

        final InventoryItem inventoryItem = inventoryItemInflater.fromBytes(byteArrayReader);
        requestExtraThinBlockMessage.setInventoryItem(inventoryItem);

        final BloomFilter bloomFilter = bloomFilterInflater.fromBytes(byteArrayReader);
        requestExtraThinBlockMessage.setBloomFilter(bloomFilter);

        if (byteArrayReader.didOverflow()) { return null; }

        return requestExtraThinBlockMessage;
    }
}

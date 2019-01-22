package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class RequestDataMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public RequestDataMessage fromBytes(final byte[] bytes) {
        final InventoryItemInflater inventoryItemInflater = new InventoryItemInflater();

        final RequestDataMessage inventoryMessage = new RequestDataMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_DATA);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final InventoryItem inventoryItem = inventoryItemInflater.fromBytes(byteArrayReader);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return inventoryMessage;
    }
}

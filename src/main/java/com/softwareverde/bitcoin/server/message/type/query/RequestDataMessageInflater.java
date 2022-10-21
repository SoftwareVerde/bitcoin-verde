package com.softwareverde.bitcoin.server.message.type.query;

import com.softwareverde.bitcoin.inflater.InventoryItemInflaters;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemInflater;
import com.softwareverde.bitcoin.util.bytearray.CompactVariableLengthInteger;
import com.softwareverde.util.bytearray.ByteArrayReader;

public class RequestDataMessageInflater extends BitcoinProtocolMessageInflater {
    protected final InventoryItemInflaters _inventoryItemInflaters;

    public RequestDataMessageInflater(final InventoryItemInflaters inventoryItemInflaters) {
        _inventoryItemInflaters = inventoryItemInflaters;
    }

    @Override
    public RequestDataMessage fromBytes(final byte[] bytes) {
        final InventoryItemInflater inventoryItemInflater = _inventoryItemInflaters.getInventoryItemInflater();

        final RequestDataMessage inventoryMessage = new RequestDataMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_DATA);
        if (protocolMessageHeader == null) { return null; }

        final CompactVariableLengthInteger inventoryCount = CompactVariableLengthInteger.readVariableLengthInteger(byteArrayReader);
        if (! inventoryCount.isCanonical()) { return null; }
        for (int i = 0; i < inventoryCount.value; ++i) {
            final InventoryItem inventoryItem = inventoryItemInflater.fromBytes(byteArrayReader);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return inventoryMessage;
    }
}

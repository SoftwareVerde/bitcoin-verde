package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class RequestDataMessageInflater extends BitcoinProtocolMessageInflater {
    @Override
    public RequestDataMessage fromBytes(final byte[] bytes) {
        final DataHashInflater dataHashInflater = new DataHashInflater();

        final RequestDataMessage inventoryMessage = new RequestDataMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.REQUEST_DATA);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final DataHash dataHash = dataHashInflater.fromBytes(byteArrayReader);
            inventoryMessage.addInventoryItem(dataHash);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return inventoryMessage;
    }
}

package com.softwareverde.bitcoin.server.message.type.request;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashInflater;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class RequestDataMessageInflater extends ProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public RequestDataMessage fromBytes(final byte[] bytes) {
        final DataHashInflater dataHashInflater = new DataHashInflater();

        final RequestDataMessage inventoryMessage = new RequestDataMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.REQUEST_OBJECT);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final DataHash dataHash = dataHashInflater.fromBytes(byteArrayReader);
            inventoryMessage.addInventoryItem(dataHash);
        }

        if (byteArrayReader.wentOutOfBounds()) { return null; }

        return inventoryMessage;
    }
}

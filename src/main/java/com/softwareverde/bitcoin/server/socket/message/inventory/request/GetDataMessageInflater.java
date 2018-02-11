package com.softwareverde.bitcoin.server.socket.message.inventory.request;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.socket.message.inventory.data.header.DataHeader;
import com.softwareverde.bitcoin.server.socket.message.inventory.data.header.DataHeaderInflater;
import com.softwareverde.bitcoin.server.socket.message.inventory.data.header.DataHeaderType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class GetDataMessageInflater extends ProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public GetDataMessage fromBytes(final byte[] bytes) {
        final DataHeaderInflater dataHeaderInflater = new DataHeaderInflater();

        final GetDataMessage inventoryMessage = new GetDataMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.GET_DATA);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final DataHeader dataHeader = dataHeaderInflater.fromBytes(byteArrayReader);
            inventoryMessage.addInventoryItem(dataHeader);
        }

        return inventoryMessage;
    }
}

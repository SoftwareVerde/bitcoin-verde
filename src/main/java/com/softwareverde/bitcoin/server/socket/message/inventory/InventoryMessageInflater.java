package com.softwareverde.bitcoin.server.socket.message.inventory;

import com.softwareverde.bitcoin.server.socket.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.socket.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class InventoryMessageInflater extends ProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public InventoryMessage fromBytes(final byte[] bytes) {
        final InventoryMessage inventoryMessage = new InventoryMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.INVENTORY);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
            final byte[] objectHash = byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE);

            final InventoryMessage.InventoryType inventoryType = InventoryMessage.InventoryType.fromValue(inventoryTypeCode);
            final InventoryMessage.InventoryItem inventoryItem = new InventoryMessage.InventoryItem(inventoryType, objectHash);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        return inventoryMessage;
    }
}

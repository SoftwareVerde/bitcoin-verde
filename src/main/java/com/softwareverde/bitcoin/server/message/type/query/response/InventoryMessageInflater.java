package com.softwareverde.bitcoin.server.message.type.query.response;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItem;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.InventoryItemType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.cryptography.hash.sha256.MutableSha256Hash;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class InventoryMessageInflater extends BitcoinProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public InventoryMessage fromBytes(final byte[] bytes) {
        final InventoryMessage inventoryMessage = new InventoryMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.INVENTORY);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableLengthInteger();
        for (int i = 0; i < inventoryCount; ++i) {
            final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
            final Sha256Hash objectHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE));

            final InventoryItemType dataType = InventoryItemType.fromValue(inventoryTypeCode);
            final InventoryItem inventoryItem = new InventoryItem(dataType, objectHash);
            inventoryMessage.addInventoryItem(inventoryItem);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return inventoryMessage;
    }
}

package com.softwareverde.bitcoin.server.message.type.query.response;

import com.softwareverde.bitcoin.server.message.ProtocolMessage;
import com.softwareverde.bitcoin.server.message.ProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.ProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.bitcoin.util.bytearray.Endian;

public class QueryResponseMessageInflater extends ProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public QueryResponseMessage fromBytes(final byte[] bytes) {
        final QueryResponseMessage queryResponseMessage = new QueryResponseMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final ProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, ProtocolMessage.MessageType.INVENTORY);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
            final byte[] objectHash = byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE);

            final DataHashType dataType = DataHashType.fromValue(inventoryTypeCode);
            final DataHash dataHash = new DataHash(dataType, objectHash);
            queryResponseMessage.addInventoryItem(dataHash);
        }

        return queryResponseMessage;
    }
}

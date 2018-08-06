package com.softwareverde.bitcoin.server.message.type.query.response;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHash;
import com.softwareverde.bitcoin.server.message.type.query.response.hash.DataHashType;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class QueryResponseMessageInflater extends BitcoinProtocolMessageInflater {
    public static final Integer HASH_BYTE_COUNT = 32;

    @Override
    public QueryResponseMessage fromBytes(final byte[] bytes) {
        final QueryResponseMessage queryResponseMessage = new QueryResponseMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_RESPONSE);
        if (protocolMessageHeader == null) { return null; }

        final Long inventoryCount = byteArrayReader.readVariableSizedInteger();
        for (int i=0; i<inventoryCount; ++i) {
            final Integer inventoryTypeCode = byteArrayReader.readInteger(4, Endian.LITTLE);
            final Sha256Hash objectHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(HASH_BYTE_COUNT, Endian.LITTLE));

            final DataHashType dataType = DataHashType.fromValue(inventoryTypeCode);
            final DataHash dataHash = new DataHash(dataType, objectHash);
            queryResponseMessage.addInventoryItem(dataHash);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return queryResponseMessage;
    }
}

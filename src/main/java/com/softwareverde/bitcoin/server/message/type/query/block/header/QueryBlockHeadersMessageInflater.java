package com.softwareverde.bitcoin.server.message.type.query.block.header;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class QueryBlockHeadersMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public QueryBlockHeadersMessage fromBytes(final byte[] bytes) {
        final Integer blockHeaderHashByteCount = 32;
        final QueryBlockHeadersMessage queryBlockHeadersMessage = new QueryBlockHeadersMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        queryBlockHeadersMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i=0; i<blockHeaderCount; ++i) {
            final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.LITTLE);
            queryBlockHeadersMessage._blockHeaderHashes.add(MutableSha256Hash.wrap(blockHeaderHashBytes));
        }

        final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.LITTLE);
        queryBlockHeadersMessage.setDesiredBlockHeaderHash(MutableSha256Hash.wrap(blockHeaderHashBytes));

        if (byteArrayReader.didOverflow()) { return null; }

        return queryBlockHeadersMessage;
    }
}

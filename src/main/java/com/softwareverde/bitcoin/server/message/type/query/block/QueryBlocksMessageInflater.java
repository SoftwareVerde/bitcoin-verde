package com.softwareverde.bitcoin.server.message.type.query.block;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.type.hash.sha256.MutableSha256Hash;
import com.softwareverde.bitcoin.type.hash.sha256.Sha256Hash;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.util.bytearray.Endian;

public class QueryBlocksMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public QueryBlocksMessage fromBytes(final byte[] bytes) {
        final Integer blockHeaderHashByteCount = 32;
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_BLOCKS);
        if (protocolMessageHeader == null) { return null; }

        queryBlocksMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount >= QueryBlocksMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i=0; i<blockHeaderCount; ++i) {
            final Sha256Hash blockHeaderHash = MutableSha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
            queryBlocksMessage._blockHeaderHashes.add(blockHeaderHash);
        }

        final byte[] blockHeaderHashBytes = byteArrayReader.readBytes(32, Endian.LITTLE);
        queryBlocksMessage._desiredBlockHeaderHash = MutableSha256Hash.wrap(blockHeaderHashBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        return queryBlocksMessage;
    }
}

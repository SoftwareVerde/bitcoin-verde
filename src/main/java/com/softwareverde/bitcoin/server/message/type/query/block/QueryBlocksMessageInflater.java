package com.softwareverde.bitcoin.server.message.type.query.block;

import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;
import com.softwareverde.cryptography.hash.sha256.Sha256Hash;
import com.softwareverde.util.bytearray.Endian;

public class QueryBlocksMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public QueryBlocksMessage fromBytes(final byte[] bytes) {
        final int blockHeaderHashByteCount = 32;
        final QueryBlocksMessage queryBlocksMessage = new QueryBlocksMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_BLOCKS);
        if (protocolMessageHeader == null) { return null; }

        queryBlocksMessage._version = byteArrayReader.readInteger(4, Endian.LITTLE);

        final int blockHeaderCount = byteArrayReader.readVariableLengthInteger().intValue();
        if (blockHeaderCount >= QueryBlocksMessage.MAX_BLOCK_HASH_COUNT) { return null; }

        final Integer bytesRequired = (blockHeaderCount * blockHeaderHashByteCount);
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        for (int i = 0; i < blockHeaderCount; ++i) {
            final Sha256Hash blockHash = Sha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
            queryBlocksMessage.addBlockHash(blockHash);
        }

        final Sha256Hash blockHeaderHashBytes = Sha256Hash.wrap(byteArrayReader.readBytes(32, Endian.LITTLE));
        queryBlocksMessage.setStopBeforeBlockHash(blockHeaderHashBytes);

        if (byteArrayReader.didOverflow()) { return null; }

        return queryBlocksMessage;
    }
}

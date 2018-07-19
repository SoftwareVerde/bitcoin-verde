package com.softwareverde.bitcoin.server.message.type.query.response.header;

import com.softwareverde.bitcoin.block.header.BlockHeaderInflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCountInflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessageInflater;
import com.softwareverde.bitcoin.server.message.header.BitcoinProtocolMessageHeader;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessage;
import com.softwareverde.bitcoin.util.bytearray.ByteArrayReader;

public class QueryBlockHeadersResponseMessageInflater extends BitcoinProtocolMessageInflater {

    @Override
    public QueryBlockHeadersResponseMessage fromBytes(final byte[] bytes) {
        final QueryBlockHeadersResponseMessage blockHeadersResponseMessage = new QueryBlockHeadersResponseMessage();
        final ByteArrayReader byteArrayReader = new ByteArrayReader(bytes);

        final BitcoinProtocolMessageHeader protocolMessageHeader = _parseHeader(byteArrayReader, MessageType.QUERY_BLOCK_HEADERS);
        if (protocolMessageHeader == null) { return null; }

        final Integer blockHeaderCount = byteArrayReader.readVariableSizedInteger().intValue();
        if (blockHeaderCount >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return null; }

        final Integer bytesRequired = ( blockHeaderCount * (BlockHeaderInflater.BLOCK_HEADER_BYTE_COUNT + 1) );
        if (byteArrayReader.remainingByteCount() < bytesRequired) { return null; }

        final BlockHeaderWithTransactionCountInflater blockHeaderInflater = new BlockHeaderWithTransactionCountInflater();
        for (int i=0; i<blockHeaderCount; ++i) {
            final BlockHeaderWithTransactionCount blockHeader = blockHeaderInflater.fromBytes(byteArrayReader);
            if (blockHeader == null) { return null; }

            blockHeadersResponseMessage._blockHeaders.add(blockHeader);
        }

        if (byteArrayReader.didOverflow()) { return null; }

        return blockHeadersResponseMessage;
    }
}

package com.softwareverde.bitcoin.server.message.type.query.response.header;

import com.softwareverde.bitcoin.block.header.BlockHeader;
import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.util.Util;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

import java.util.ArrayList;
import java.util.List;

public class QueryBlockHeadersResponseMessage extends BitcoinProtocolMessage {
    protected final List<BlockHeader> _blockHeaders = new ArrayList<BlockHeader>();

    public QueryBlockHeadersResponseMessage() {
        super(MessageType.QUERY_BLOCK_HEADERS);
    }

    public void addBlockHeader(final BlockHeader blockHeader) {
        if (_blockHeaders.size() >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        _blockHeaders.add(blockHeader);
    }

    public void clearBlockHeaders() {
        _blockHeaders.clear();
    }

    public List<BlockHeader> getBlockHeaders() {
        return Util.copyList(_blockHeaders);
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaders.size();

        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderCountBytes);

        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        for (int i=0; i<blockHeaderCount; ++i) {
            final BlockHeader blockHeader = _blockHeaders.get(i);
            byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(blockHeader));
            byteArrayBuilder.appendByte((byte) 0x00); // Transaction count...
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}

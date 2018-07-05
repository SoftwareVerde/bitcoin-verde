package com.softwareverde.bitcoin.server.message.type.query.response.header;

import com.softwareverde.bitcoin.block.header.BlockHeaderDeflater;
import com.softwareverde.bitcoin.block.header.BlockHeaderWithTransactionCount;
import com.softwareverde.bitcoin.server.message.BitcoinProtocolMessage;
import com.softwareverde.bitcoin.server.message.type.MessageType;
import com.softwareverde.bitcoin.server.message.type.query.block.header.QueryBlockHeadersMessage;
import com.softwareverde.bitcoin.util.ByteUtil;
import com.softwareverde.constable.bytearray.ByteArray;
import com.softwareverde.constable.bytearray.MutableByteArray;
import com.softwareverde.constable.list.List;
import com.softwareverde.constable.list.mutable.MutableList;
import com.softwareverde.util.bytearray.ByteArrayBuilder;

public class QueryBlockHeadersResponseMessage extends BitcoinProtocolMessage {
    protected final MutableList<BlockHeaderWithTransactionCount> _blockHeaders = new MutableList<BlockHeaderWithTransactionCount>();

    public QueryBlockHeadersResponseMessage() {
        super(MessageType.QUERY_BLOCK_HEADERS_RESPONSE);
    }

    public void addBlockHeader(final BlockHeaderWithTransactionCount blockHeader) {
        if (_blockHeaders.getSize() >= QueryBlockHeadersMessage.MAX_BLOCK_HEADER_HASH_COUNT) { return; }
        _blockHeaders.add(blockHeader);
    }

    public void clearBlockHeaders() {
        _blockHeaders.clear();
    }

    public List<BlockHeaderWithTransactionCount> getBlockHeaders() {
        return _blockHeaders;
    }

    @Override
    protected ByteArray _getPayload() {
        final int blockHeaderCount = _blockHeaders.getSize();

        final byte[] blockHeaderCountBytes = ByteUtil.variableLengthIntegerToBytes(blockHeaderCount);

        final ByteArrayBuilder byteArrayBuilder = new ByteArrayBuilder();
        byteArrayBuilder.appendBytes(blockHeaderCountBytes);

        final BlockHeaderDeflater blockHeaderDeflater = new BlockHeaderDeflater();
        for (int i=0; i<blockHeaderCount; ++i) {
            final BlockHeaderWithTransactionCount blockHeader = _blockHeaders.get(i);
            byteArrayBuilder.appendBytes(blockHeaderDeflater.toBytes(blockHeader));

            final Integer transactionCount = blockHeader.getTransactionCount();
            final byte[] transactionCountBytes = ByteUtil.variableLengthIntegerToBytes(transactionCount);
            byteArrayBuilder.appendBytes(transactionCountBytes);
        }

        return MutableByteArray.wrap(byteArrayBuilder.build());
    }
}
